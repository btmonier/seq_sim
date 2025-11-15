package net.maizegenetics.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import net.maizegenetics.Constants
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.io.path.*
import kotlin.system.exitProcess

class ExtractChromIds : CliktCommand(name = "extract-chrom-ids") {
    private val gvcfInput by option(
        "--gvcf-file", "-g",
        help = "GVCF file, directory of GVCF files, or text file with paths to GVCF files (one per line)"
    ).path(mustExist = true)
        .required()

    private val outputFile by option(
        "--output-file", "-o",
        help = "Path for the output file"
    ).path(mustExist = false, canBeFile = true, canBeDir = false)
        .default(Path.of("chromosome_ids.txt"))

    private fun collectGvcfFiles(): List<Path> {
        val gvcfFiles = mutableListOf<Path>()

        when {
            gvcfInput.isDirectory() -> {
                // Collect all GVCF files from directory
                echo("Collecting GVCF files from directory: $gvcfInput")
                gvcfInput.listDirectoryEntries().forEach { file ->
                    if (file.isRegularFile() && isGvcfFile(file)) {
                        gvcfFiles.add(file)
                    }
                }
                if (gvcfFiles.isEmpty()) {
                    echo("Error: No GVCF files (*.gvcf, *.gvcf.gz) found in directory: $gvcfInput", err = true)
                    exitProcess(1)
                }
                echo("Found ${gvcfFiles.size} GVCF file(s)")
            }
            gvcfInput.isRegularFile() -> {
                // Check if it's a .txt file with paths or a single GVCF file
                if (gvcfInput.extension == Constants.TEXT_FILE_EXTENSION) {
                    // It's a text file with paths
                    echo("Reading GVCF file paths from: $gvcfInput")
                    gvcfInput.readLines().forEach { line ->
                        val trimmedLine = line.trim()
                        if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) {
                            val gvcfFile = Path(trimmedLine)
                            if (gvcfFile.exists() && gvcfFile.isRegularFile()) {
                                gvcfFiles.add(gvcfFile)
                            } else {
                                echo("Warning: GVCF file not found: $trimmedLine", err = true)
                            }
                        }
                    }
                    if (gvcfFiles.isEmpty()) {
                        echo("Error: No valid GVCF files found in list file: $gvcfInput", err = true)
                        exitProcess(1)
                    }
                    echo("Found ${gvcfFiles.size} GVCF file(s)")
                } else if (isGvcfFile(gvcfInput)) {
                    // It's a single GVCF file
                    echo("Processing single GVCF file: $gvcfInput")
                    gvcfFiles.add(gvcfInput)
                } else {
                    echo("Error: File must have extension .gvcf, .gvcf.gz, or .txt: $gvcfInput", err = true)
                    exitProcess(1)
                }
            }
            else -> {
                echo("Error: Input is neither a file nor a directory: $gvcfInput", err = true)
                exitProcess(1)
            }
        }

        return gvcfFiles
    }

    private fun isGvcfFile(path: Path): Boolean {
        val fileName = path.fileName.toString()
        return Constants.GVCF_EXTENSIONS.any { fileName.endsWith(it) }
    }

    override fun run() {
        // Collect GVCF files
        val gvcfFiles = collectGvcfFiles()

        // Extract unique chromosome IDs from all GVCF files
        val uniqueChromIds = mutableSetOf<String>()
        var successCount = 0
        var failureCount = 0

        gvcfFiles.forEachIndexed { index, gvcfFile ->
            echo("\nProcessing (${index + 1}/${gvcfFiles.size}): ${gvcfFile.fileName}")
            try {
                val chromIds = extractChromIdsFromGvcf(gvcfFile)
                uniqueChromIds.addAll(chromIds)
                successCount++
            } catch (e: Exception) {
                failureCount++
                echo("Error processing ${gvcfFile.fileName}: ${e.message}", err = true)
            }
        }

        // Sort and write unique chromosome IDs to output file
        try {
            val sortedChromIds = uniqueChromIds.sorted()
            outputFile.writeLines(sortedChromIds)

            echo("\n" + "=".repeat(60))
            echo("Extraction completed!")
            echo("Files processed: $successCount successful, $failureCount failed")
            echo("Unique chromosome IDs found: ${sortedChromIds.size}")
            echo("\nChromosome IDs:")
            sortedChromIds.forEach { echo("  $it") }
            echo("\nOutput written to: $outputFile")
        } catch (e: Exception) {
            echo("Error: Failed to write output file: ${e.message}", err = true)
            exitProcess(1)
        }
    }

    private fun extractChromIdsFromGvcf(gvcfFile: Path): Set<String> {
        val chromIds = mutableSetOf<String>()

        val reader = if (gvcfFile.toString().endsWith(".gz")) {
            // Handle compressed GVCF files
            BufferedReader(InputStreamReader(GZIPInputStream(gvcfFile.inputStream())))
        } else {
            // Handle uncompressed GVCF files
            gvcfFile.bufferedReader()
        }

        reader.use { input ->
            input.lineSequence().forEach { line ->
                // Skip header lines (starting with #)
                if (!line.startsWith("#")) {
                    // Split by tab and extract the first column (CHROM)
                    val columns = line.split("\t")
                    if (columns.isNotEmpty()) {
                        val chromId = columns[0].trim()
                        if (chromId.isNotEmpty()) {
                            chromIds.add(chromId)
                        }
                    }
                }
            }
        }

        return chromIds
    }
}
