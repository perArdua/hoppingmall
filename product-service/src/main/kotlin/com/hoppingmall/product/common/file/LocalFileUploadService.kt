package com.hoppingmall.product.common.file

import com.hoppingmall.product.common.file.dto.FileUploadRequest
import com.hoppingmall.product.common.file.dto.FileUploadResponse
import com.hoppingmall.product.common.file.exception.*
import org.springframework.stereotype.Service
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

@Service
class LocalFileUploadService(
    private val fileUploadConfig: FileUploadConfig
) : FileUploadService {

    override fun uploadFile(request: FileUploadRequest): FileUploadResponse {
        if (request.file.isEmpty) throw EmptyFileException()

        val originalFilename = request.file.originalFilename
            ?: throw InvalidFileNameException()
        if (originalFilename.isBlank()) throw InvalidFileNameException()

        val extension = originalFilename.substringAfterLast(".", "").lowercase()
        val allowedExtensions = fileUploadConfig.allowedExtensions.map { it.lowercase() }

        if (extension !in allowedExtensions) {
            throw UnsupportedFileTypeException(allowedExtensions)
        }
        if (request.file.size > fileUploadConfig.maxFileSize) {
            throw FileSizeExceededException()
        }
        if (request.domain !in fileUploadConfig.allowedDomains) {
            throw UnsupportedDomainException(fileUploadConfig.allowedDomains.toList())
        }
        checkContent(request, extension)

        val fileName = "${UUID.randomUUID()}.$extension"
        val uploadDir = Paths.get(fileUploadConfig.baseUploadDir, request.domain, fileUploadConfig.subDirectory)

        try {
            Files.createDirectories(uploadDir)
        } catch (e: IOException) {
            throw DirectoryAccessException(uploadDir.toString())
        }

        val filePath = uploadDir.resolve(fileName)
        try {
            request.file.transferTo(filePath.toFile())
        } catch (e: IOException) {
            throw FileUploadException(e.message ?: "")
        }

        return FileUploadResponse(
            fileUrl = filePath.toString(),
            fileName = fileName,
            fileSize = request.file.size,
            domain = request.domain
        )
    }

    private fun checkContent(request: FileUploadRequest, extension: String) {
        val detected = try {
            request.file.inputStream.use { ImageType.detect(it) }
        } catch (e: IOException) {
            throw FileUploadException(e.message ?: "")
        }
        if (detected == null || extension !in detected.extensions) {
            throw FileContentMismatchException()
        }
    }
}
