package net.maizegenetics.utils

import org.apache.logging.log4j.Logger

object ProcessRunner {
    fun runCommand(vararg command: String, logger: Logger): Int {
        return try {
            logger.debug("Executing: ${command.joinToString(" ")}")
            val process = ProcessBuilder(*command)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start()

            val exitCode = process.waitFor()
            if (exitCode != 0) {
                logger.error("Command failed with exit code $exitCode")
            }
            exitCode
        } catch (e: Exception) {
            logger.error("Failed to execute command: ${e.message}", e)
            -1
        }
    }
}
