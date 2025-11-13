package net.maizegenetics.utils

import org.apache.logging.log4j.Logger
import java.io.File
import java.net.URI
import java.nio.file.Path
import java.util.zip.ZipInputStream

object FileDownloader {
    fun downloadAndExtractZip(url: String, destDir: Path, logger: Logger): Boolean {
        return try {
            logger.info("Downloading from: $url")
            val urlObj = URI(url).toURL()

            urlObj.openStream().use { inputStream ->
                ZipInputStream(inputStream).use { zipStream ->
                    var entry = zipStream.nextEntry
                    while (entry != null) {
                        val destFile = destDir.resolve(entry.name).toFile()

                        if (entry.isDirectory) {
                            destFile.mkdirs()
                        } else {
                            destFile.parentFile.mkdirs()
                            destFile.outputStream().use { output ->
                                zipStream.copyTo(output)
                            }
                        }
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                    }
                }
            }
            logger.info("Download and extraction completed successfully")
            true
        } catch (e: Exception) {
            logger.error("Failed to download from $url: ${e.message}", e)
            false
        }
    }

    fun copyResourceToFile(resourcePath: String, destFile: File, logger: Logger): Boolean {
        return try {
            val resourceStream = FileDownloader::class.java.getResourceAsStream(resourcePath)
            if (resourceStream == null) {
                logger.error("Resource not found: $resourcePath")
                return false
            }

            logger.debug("Copying resource $resourcePath to ${destFile.absolutePath}")
            resourceStream.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            logger.info("Resource copied successfully")
            true
        } catch (e: Exception) {
            logger.error("Failed to copy resource: ${e.message}", e)
            false
        }
    }
}
