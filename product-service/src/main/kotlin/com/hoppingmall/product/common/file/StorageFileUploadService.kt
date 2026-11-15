package com.hoppingmall.product.common.file

import com.hoppingmall.product.common.file.domain.UploadedFile
import com.hoppingmall.product.common.file.domain.repository.UploadedFileRepository
import com.hoppingmall.product.common.file.dto.FileUploadRequest
import com.hoppingmall.product.common.file.dto.FileUploadResponse
import com.hoppingmall.product.common.file.exception.*
import com.hoppingmall.product.common.file.storage.FileStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.exception.SdkException
import java.io.IOException
import java.util.UUID

@Service
class StorageFileUploadService(
    private val fileUploadConfig: FileUploadConfig,
    private val storageProperties: FileStorageProperties,
    private val fileStorage: FileStorage,
    private val uploadedFileRepository: UploadedFileRepository
) : FileUploadService {

    private val log = LoggerFactory.getLogger(javaClass)

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
        val imageType = checkContent(request, extension)

        val fileName = "${UUID.randomUUID()}.$extension"
        val key = "${request.domain}/${fileUploadConfig.subDirectory}/$fileName"
        val record = uploadedFileRepository.save(
            UploadedFile.pending(
                objectKey = key,
                uploaderId = request.uploaderId,
                uploaderRole = request.uploaderRole,
                contentType = imageType.contentType,
                fileSize = request.file.size
            )
        )

        try {
            request.file.inputStream.use { fileStorage.put(key, it, request.file.size, imageType.contentType) }
        } catch (e: IOException) {
            failUpload(record, e)
        } catch (e: SdkException) {
            failUpload(record, e)
        }

        return FileUploadResponse(
            fileUrl = storageProperties.publicUrl(key),
            fileName = fileName,
            fileSize = request.file.size,
            domain = request.domain
        )
    }

    private fun failUpload(record: UploadedFile, cause: Exception): Nothing {
        log.warn("업로드 파일을 저장소에 올리지 못함: key={}, error={}", record.objectKey, cause.toString())
        uploadedFileRepository.delete(record)
        throw FileUploadException(cause.message ?: "")
    }

    private fun checkContent(request: FileUploadRequest, extension: String): ImageType {
        val detected = try {
            request.file.inputStream.use { ImageType.detect(it) }
        } catch (e: IOException) {
            throw FileUploadException(e.message ?: "")
        }
        if (detected == null || extension !in detected.extensions) {
            throw FileContentMismatchException()
        }
        return detected
    }
}
