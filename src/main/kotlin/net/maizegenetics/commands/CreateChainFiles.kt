package net.maizegenetics.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
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

class CreateChainFiles : CliktCommand(name = "create-chain-files") {
    companion object {
        private const val LOG_FILE_NAME = "07_create_chain_files.log"
        private const val OUTPUT_DIR = "output"
        private const val CHAIN_RESULTS_DIR = "07_chain_results"
        private const val CHAIN_PATHS_FILE = "chain_file_paths.txt"
        private const val BASH_SCRIPT = "src/python/cross/create_chains.sh"
        private const val DEFAULT_JOBS = 8
    }

    private val logger: Logger = LogManager.getLogger(CreateChainFiles::class.java)

    private val workDir by option(
        "--work-dir", "-w",
        help = "Working directory for files and scripts"
    ).path(mustExist = false, canBeFile = false, canBeDir = true)
        .default(Path.of(Constants.DEFAULT_WORK_DIR))

    private val mafInput by option(
        "--maf-input", "-m",
        help = "MAF file, directory of MAF files, or text file with paths to MAF files (one per line)"
    ).path(mustExist = true)
        .required()

    private val jobs by option(
        "--jobs", "-j",
        help = "Number of parallel jobs"
    ).int()
        .default(DEFAULT_JOBS)

    private val outputDirOption by option(
        "--output-dir", "-o",
        help = "Custom output directory (default: work_dir/output/07_chain_results)"
    ).path(mustExist = false, canBeFile = false, canBeDir = true)

    private fun collectMafFiles(): List<Path> {
        val mafFiles = mutableListOf<Path>()

        when {
            mafInput.isDirectory() -> {
                // Collect all MAF files from directory
                logger.info("Collecting MAF files from directory: $mafInput")
                mafInput.listDirectoryEntries().forEach { file ->
                    if (file.isRegularFile() && file.extension in Constants.MAF_EXTENSIONS) {
                        mafFiles.add(file)
                    } else if (file.isRegularFile() && file.name.endsWith(".maf.gz")) {
                        mafFiles.add(file)
                    }
                }
                if (mafFiles.isEmpty()) {
                    logger.error("No MAF files (*.maf, *.maf.gz) found in directory: $mafInput")
                    exitProcess(1)
                }
                logger.info("Found ${mafFiles.size} MAF file(s) in directory")
            }
            mafInput.isRegularFile() -> {
                // Check if it's a .txt file with paths or a single MAF file
                if (mafInput.extension == Constants.TEXT_FILE_EXTENSION) {
                    // It's a text file with paths
                    logger.info("Reading MAF file paths from: $mafInput")
                    mafInput.readLines().forEach { line ->
                        val trimmedLine = line.trim()
                        if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) {
                            val mafFile = Path(trimmedLine)
                            if (mafFile.exists() && mafFile.isRegularFile()) {
                                mafFiles.add(mafFile)
                            } else {
                                logger.warn("MAF file not found or not a file: $trimmedLine")
                            }
                        }
                    }
                    if (mafFiles.isEmpty()) {
                        logger.error("No valid MAF files found in list file: $mafInput")
                        exitProcess(1)
                    }
                    logger.info("Found ${mafFiles.size} MAF file(s) in list")
                } else if (mafInput.extension in Constants.MAF_EXTENSIONS || mafInput.name.endsWith(".maf.gz")) {
                    // It's a single MAF file
                    logger.info("Using single MAF file: $mafInput")
                    mafFiles.add(mafInput)
                } else {
                    logger.error("MAF file must have .maf or .maf.gz extension or be a .txt file with paths: $mafInput")
                    exitProcess(1)
                }
            }
            else -> {
                logger.error("MAF input is neither a file nor a directory: $mafInput")
                exitProcess(1)
            }
        }

        return mafFiles
    }

    override fun run() {
        // Validate working directory exists
        if (!workDir.exists()) {
            logger.error("Working directory does not exist: $workDir")
            logger.error("Please run 'setup-environment' command first")
            exitProcess(1)
        }

        // Configure file logging to working directory
        LoggingUtils.setupFileLogging(workDir, LOG_FILE_NAME, logger)

        logger.info("Starting create chain files")
        logger.info("Working directory: $workDir")
        logger.info("Parallel jobs: $jobs")

        // Validate MLImpute directory exists
        val mlimputeDir = workDir.resolve(Constants.SRC_DIR).resolve(Constants.MLIMPUTE_DIR)
        if (!mlimputeDir.exists()) {
            logger.error("MLImpute directory not found: $mlimputeDir")
            logger.error("Please run 'setup-environment' command first")
            exitProcess(1)
        }

        // Validate bash script exists
        val bashScript = mlimputeDir.resolve(BASH_SCRIPT)
        if (!bashScript.exists()) {
            logger.error("Bash script not found: $bashScript")
            exitProcess(1)
        }

        // Collect MAF files
        val mafFiles = collectMafFiles()
        logger.info("Processing ${mafFiles.size} MAF file(s)")

        // Create output directory (use custom or default)
        val outputDir = outputDirOption ?: workDir.resolve(OUTPUT_DIR).resolve(CHAIN_RESULTS_DIR)
        if (!outputDir.exists()) {
            logger.debug("Creating output directory: $outputDir")
            outputDir.createDirectories()
            logger.info("Output directory created: $outputDir")
        }

        // Create temporary directory for MAF files
        val tempMafDir = outputDir.resolve("temp_maf_files")
        if (!tempMafDir.exists()) {
            tempMafDir.createDirectories()
        }

        // Copy/link MAF files to temporary directory
        logger.info("Preparing MAF files in temporary directory")
        mafFiles.forEach { mafFile ->
            val targetFile = tempMafDir.resolve(mafFile.fileName)
            if (!targetFile.exists()) {
                mafFile.copyTo(targetFile)
            }
        }

        // Run create_chains.sh
        logger.info("Running create_chains.sh")
        val exitCode = ProcessRunner.runCommand(
            "bash", bashScript.toString(),
            "-i", tempMafDir.toString(),
            "-o", outputDir.toString(),
            "-j", jobs.toString(),
            workingDir = workDir.toFile(),
            logger = logger
        )

        if (exitCode != 0) {
            logger.error("create_chains.sh failed with exit code $exitCode")
            exitProcess(exitCode)
        }

        logger.info("Create chain files completed successfully")

        // Clean up temporary directory
        logger.info("Cleaning up temporary MAF directory")
        tempMafDir.toFile().deleteRecursively()

        // Collect generated chain files
        val chainFiles = outputDir.listDirectoryEntries()
            .filter { it.isRegularFile() && it.extension == "chain" }
            .sorted()

        if (chainFiles.isEmpty()) {
            logger.warn("No chain files generated")
        } else {
            logger.info("Generated ${chainFiles.size} chain file(s):")
            chainFiles.forEach { logger.info("  $it") }

            // Write chain file paths to text file
            val chainPathsFile = outputDir.resolve(CHAIN_PATHS_FILE)
            try {
                chainPathsFile.writeLines(chainFiles.map { it.toString() })
                logger.info("Chain file paths written to: $chainPathsFile")
            } catch (e: Exception) {
                logger.error("Failed to write chain paths file: ${e.message}", e)
            }
        }

        logger.info("Output directory: $outputDir")
    }
}
