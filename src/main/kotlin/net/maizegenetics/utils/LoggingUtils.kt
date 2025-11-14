package net.maizegenetics.utils

import net.maizegenetics.Constants
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.FileAppender
import org.apache.logging.log4j.core.layout.PatternLayout
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

object LoggingUtils {
    private const val LOG_PATTERN = "%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"

    /**
     * Sets up file logging for a command in the working directory
     *
     * @param workDir The working directory where logs will be stored
     * @param logFileName The name of the log file (e.g., "setup_environment.log")
     * @param logger The logger instance to configure
     */
    fun setupFileLogging(workDir: Path, logFileName: String, logger: Logger) {
        val logsDir = workDir.resolve(Constants.LOGS_DIR)
        if (!logsDir.exists()) {
            logsDir.createDirectories()
        }

        val logFile = logsDir.resolve(logFileName).toFile()
        val context = LogManager.getContext(false) as LoggerContext
        val config = context.configuration

        val layout = PatternLayout.newBuilder()
            .withConfiguration(config)
            .withPattern(LOG_PATTERN)
            .build()

        val appender = FileAppender.newBuilder()
            .withFileName(logFile.absolutePath)
            .withAppend(true)
            .withLocking(false)
            .setName("WorkDirFileLogger")
            .setLayout(layout)
            .setConfiguration(config)
            .build()

        appender.start()
        config.addAppender(appender)
        config.rootLogger.addAppender(appender, null, null)
        context.updateLoggers()

        logger.info("Logging to file: $logFile")
    }
}
