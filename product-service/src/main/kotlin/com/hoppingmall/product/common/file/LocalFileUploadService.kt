package com.hoppingmall.product.common.file

import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID

@Service
class LocalFileUploadService(
    private val fileUploadConfig: FileUploadConfig
) : FileUploadService {

    override fun uploadFile(request: FileUploadRequest): FileUploadResponse {
        val originalFilename = request.file.originalFilename ?: "unknown"
        val extension = originalFilename.substringAfterLast(".", "")

        require(extension in fileUploadConfig.allowedExtensions) { "허용되지 않는 확장자입니다: $extension" }
        require(request.file.size <= fileUploadConfig.maxFileSize) { "파일 크기가 초과되었습니다" }
        require(request.domain in fileUploadConfig.allowedDomains) { "허용되지 않는 도메인입니다: ${request.domain}" }

        val fileName = "${UUID.randomUUID()}.$extension"
        val uploadDir = Paths.get(fileUploadConfig.baseUploadDir, request.domain, fileUploadConfig.subDirectory)
        Files.createDirectories(uploadDir)

        val filePath = uploadDir.resolve(fileName)
        request.file.transferTo(filePath.toFile())

        return FileUploadResponse(
            fileUrl = filePath.toString(),
            fileName = fileName,
            fileSize = request.file.size,
            domain = request.domain
        )
    }
}
