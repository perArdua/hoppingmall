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

        val extension = originalFilename.substringAfterLast(".", "")

        if (extension !in fileUploadConfig.allowedExtensions) {
            throw UnsupportedFileTypeException(fileUploadConfig.allowedExtensions.toList())
        }
        if (request.file.size > fileUploadConfig.maxFileSize) {
            throw FileSizeExceededException()
        }
        if (request.domain !in fileUploadConfig.allowedDomains) {
            throw UnsupportedDomainException(fileUploadConfig.allowedDomains.toList())
        }

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
}
