package net.maizegenetics.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import net.maizegenetics.Constants
import net.maizegenetics.utils.LoggingUtils
import net.maizegenetics.utils.ProcessRunner
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.system.exitProcess

class ConvertRopebwt2Ps4g : CliktCommand(name = "convert-ropebwt2ps4g") {
    companion object {
        private const val LOG_FILE_NAME = "14_convert_ropebwt2ps4g.log"
        private const val OUTPUT_DIR = "output"
        private const val CONVERT_RESULTS_DIR = "14_convert_ropebwt2ps4g_results"
        private const val PS4G_FILE_PATHS_FILE = "ps4g_file_paths.txt"
        private const val DEFAULT_MIN_MEM_LENGTH = 135
        private const val DEFAULT_MAX_NUM_HITS = 16
        private const val BED_EXTENSION = "bed"
    }

    private val logger: Logger = LogManager.getLogger(ConvertRopebwt2Ps4g::class.java)

    private val workDir by option(
        "--work-dir", "-w",
        help = "Working directory for files and scripts"
    ).path(mustExist = false, canBeFile = false, canBeDir = true)
        .default(Path.of(Constants.DEFAULT_WORK_DIR))

    private val bedInput by option(
        "--bed-input", "-b",
        help = "BED file, directory of BED files, or text file with paths to BED files (one per line)"
    ).path(mustExist = false)

    private val outputDirOption by option(
        "--output-dir", "-o",
        help = "Output directory for PS4G files (default: work_dir/output/14_convert_ropebwt2ps4g_results)"
    ).path(mustExist = false, canBeFile = false, canBeDir = true)

    private val splineKnotDirOption by option(
        "--spline-knot-dir", "-s",
        help = "Directory containing spline knots from step 13 (auto-detected if not specified)"
    ).path(mustExist = false, canBeFile = false, canBeDir = true)

    private val minMemLength by option(
        "--min-mem-length", "-m",
        help = "Minimum MEM length threshold"
    ).int()
        .default(DEFAULT_MIN_MEM_LENGTH)

    private val maxNumHits by option(
        "--max-num-hits", "-x",
        help = "Maximum allowable haplotype hits per alignment"
    ).int()
        .default(DEFAULT_MAX_NUM_HITS)

    private fun collectBedFiles(): List<Path> {
        val bedFiles = mutableListOf<Path>()

        // If no input specified, try to auto-detect from step 12
        val actualInput = bedInput ?: run {
            logger.info("No BED input specified, attempting to auto-detect from step 12")
            val step12OutputDir = workDir.resolve(OUTPUT_DIR).resolve("12_ropebwt_mem_results")
            if (!step12OutputDir.exists()) {
                logger.error("Cannot auto-detect BED files: step 12 output directory not found at $step12OutputDir")
                logger.error("Please specify --bed-input or ensure step 12 (ropebwt-mem) has been run")
                exitProcess(1)
            }
            step12OutputDir
        }

        if (!actualInput.exists()) {
            logger.error("Input path not found: $actualInput")
            exitProcess(1)
        }

        when {
            actualInput.isDirectory() -> {
                logger.info("Collecting BED files from directory: $actualInput")
                actualInput.listDirectoryEntries().forEach { file ->
                    if (file.isRegularFile() && file.extension == BED_EXTENSION) {
                        bedFiles.add(file)
                    }
                }
                if (bedFiles.isEmpty()) {
                    logger.error("No BED files found in directory: $actualInput")
                    exitProcess(1)
                }
                logger.info("Found ${bedFiles.size} BED file(s) in directory")
            }
            actualInput.isRegularFile() -> {
                if (actualInput.extension == Constants.TEXT_FILE_EXTENSION) {
                    logger.info("Reading BED file paths from: $actualInput")
                    actualInput.readLines().forEach { line ->
                        val trimmedLine = line.trim()
                        if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) {
                            val bedFile = Path(trimmedLine)
                            if (bedFile.exists() && bedFile.isRegularFile()) {
                                bedFiles.add(bedFile)
                            } else {
                                logger.warn("BED file not found or not a file: $trimmedLine")
                            }
                        }
                    }
                    if (bedFiles.isEmpty()) {
                        logger.error("No valid BED files found in list file: $actualInput")
                        exitProcess(1)
                    }
                    logger.info("Found ${bedFiles.size} BED file(s) in list")
                } else if (actualInput.extension == BED_EXTENSION) {
                    logger.info("Using single BED file: $actualInput")
                    bedFiles.add(actualInput)
                } else {
                    logger.error("BED file must have .bed extension, or be a .txt file with paths: $actualInput")
                    exitProcess(1)
                }
            }
            else -> {
                logger.error("BED input is neither a file nor a directory: $actualInput")
                exitProcess(1)
            }
        }

        return bedFiles
    }

    private fun findSplineKnotDir(): Path {
        val step13OutputDir = workDir.resolve(OUTPUT_DIR).resolve("13_spline_knots_results")

        if (!step13OutputDir.exists()) {
            logger.error("Cannot auto-detect spline knot directory: step 13 output directory not found at $step13OutputDir")
            logger.error("Please specify --spline-knot-dir manually or ensure step 13 (build-spline-knots) has been run")
            exitProcess(1)
        }

        logger.info("Auto-detected spline knot directory: $step13OutputDir")
        return step13OutputDir
    }

    override fun run() {
        // Validate working directory exists
        if (!workDir.exists()) {
            logger.error("Working directory does not exist: $workDir")
            logger.error("Please run 'setup-environment' command first")
            exitProcess(1)
        }

        // Validate PHG is available
        val phgBinary = workDir.resolve(Constants.SRC_DIR)
            .resolve(Constants.PHGV2_DIR)
            .resolve("bin")
            .resolve("phg")
        if (!phgBinary.exists()) {
            logger.error("PHG binary not found: $phgBinary")
            logger.error("Please run 'setup-environment' command first")
            exitProcess(1)
        }

        // Configure file logging to working directory
        LoggingUtils.setupFileLogging(workDir, LOG_FILE_NAME, logger)

        logger.info("Starting PHG convert-ropebwt2ps4g-file")
        logger.info("Working directory: $workDir")

        // Collect BED files
        val bedFiles = collectBedFiles()
        logger.info("Processing ${bedFiles.size} BED file(s)")

        // Determine spline knot directory
        val splineKnotDir = splineKnotDirOption ?: findSplineKnotDir()
        if (!splineKnotDir.exists()) {
            logger.error("Spline knot directory not found: $splineKnotDir")
            exitProcess(1)
        }
        logger.info("Using spline knot directory: $splineKnotDir")

        // Log parameters
        logger.info("Min MEM length: $minMemLength")
        logger.info("Max num hits: $maxNumHits")

        // Create output directory (use custom or default)
        val outputDir = outputDirOption ?: workDir.resolve(OUTPUT_DIR).resolve(CONVERT_RESULTS_DIR)
        if (!outputDir.exists()) {
            logger.debug("Creating output directory: $outputDir")
            outputDir.createDirectories()
            logger.info("Output directory created: $outputDir")
        }

        // Process each BED file with PHG convert-ropebwt2ps4g-file
        var successCount = 0
        var failureCount = 0
        val ps4gFiles = mutableListOf<Path>()

        bedFiles.forEach { bedFile ->
            val sampleName = bedFile.nameWithoutExtension
            val outputFile = outputDir.resolve("${sampleName}.ps4g")

            logger.info("Processing: ${bedFile.fileName} -> ${outputFile.fileName}")

            val exitCode = ProcessRunner.runCommand(
                phgBinary.toString(),
                "convert-ropebwt2ps4g-file",
                "--ropebwt-bed", bedFile.toString(),
                "--output-dir", outputDir.toString(),
                "--spline-knot-dir", splineKnotDir.toString(),
                "--min-mem-length", minMemLength.toString(),
                "--max-num-hits", maxNumHits.toString(),
                workingDir = workDir.toFile(),
                logger = logger
            )

            if (exitCode == 0) {
                successCount++
                // PHG creates output file with same base name as input BED
                if (outputFile.exists()) {
                    ps4gFiles.add(outputFile)
                    logger.info("Successfully processed: ${bedFile.fileName}")
                } else {
                    logger.warn("Command succeeded but output file not found: $outputFile")
                }
            } else {
                failureCount++
                logger.error("Failed to process ${bedFile.fileName} with exit code $exitCode")
            }
        }

        logger.info("PHG convert-ropebwt2ps4g-file completed")
        logger.info("Success: $successCount, Failures: $failureCount")

        if (ps4gFiles.isNotEmpty()) {
            // Write PS4G file paths to text file
            val ps4gFilePathsFile = outputDir.resolve(PS4G_FILE_PATHS_FILE)
            try {
                ps4gFilePathsFile.writeLines(ps4gFiles.map { it.toString() })
                logger.info("PS4G file paths written to: $ps4gFilePathsFile")
            } catch (e: Exception) {
                logger.error("Failed to write PS4G paths file: ${e.message}", e)
            }
        }

        logger.info("Output directory: $outputDir")

        if (failureCount > 0) {
            exitProcess(1)
        }
    }
}
