package com.hoppingmall.mall.global.file.service

import com.hoppingmall.mall.global.file.config.FileUploadConfig
import com.hoppingmall.mall.global.file.constant.FileUploadMessages
import com.hoppingmall.mall.global.file.dto.request.FileUploadRequest
import com.hoppingmall.mall.global.file.dto.response.FileUploadResponse
import com.hoppingmall.mall.global.file.exception.*
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

@Service
class FileUploadServiceImpl(
    private val fileUploadConfig: FileUploadConfig
) : FileUploadService {

    override fun uploadFile(request: FileUploadRequest): FileUploadResponse {
        validateFile(request.file, request.domain)
        
        val fileName = generateFileName(request.file.originalFilename)
        val uploadDir = "${fileUploadConfig.baseUploadDir}/${request.domain}/${fileUploadConfig.subDirectory}"
        val filePath = Paths.get(uploadDir, fileName)
        
        try {
            val uploadPath = Paths.get(uploadDir)
            Files.createDirectories(uploadPath)
            
            if (!Files.exists(uploadPath) || !Files.isWritable(uploadPath)) {
                throw DirectoryAccessException(uploadDir)
            }
            
            request.file.transferTo(filePath.toFile())
            
            return FileUploadResponse(
                fileUrl = filePath.toString(),
                fileName = fileName,
                fileSize = request.file.size,
                domain = request.domain
            )
        } catch (e: IOException) {
            throw FileUploadException("${FileUploadMessages.FILE_UPLOAD_ERROR}: ${e.message}")
        }
    }

    private fun validateFile(file: MultipartFile, domain: String) {
        if (file.isEmpty) {
            throw EmptyFileException()
        }

        if (file.size > fileUploadConfig.maxFileSize) {
            throw FileSizeExceededException()
        }

        val originalFilename = file.originalFilename
        if (originalFilename.isNullOrBlank()) {
            throw InvalidFileNameException()
        }

        val extension = getFileExtension(originalFilename)
        if (!fileUploadConfig.allowedExtensions.contains(extension.lowercase())) {
            throw UnsupportedFileTypeException(fileUploadConfig.allowedExtensions.toList())
        }

        if (!fileUploadConfig.allowedDomains.contains(domain.lowercase())) {
            throw UnsupportedDomainException(fileUploadConfig.allowedDomains.toList())
        }
    }

    private fun getFileExtension(filename: String): String {
        return if (filename.contains(".")) {
            filename.substringAfterLast(".")
        } else {
            ""
        }
    }

    private fun generateFileName(originalFilename: String?): String {
        val extension = getFileExtension(originalFilename ?: "")
        val uuid = UUID.randomUUID().toString()
        return "$uuid.$extension"
    }
} 