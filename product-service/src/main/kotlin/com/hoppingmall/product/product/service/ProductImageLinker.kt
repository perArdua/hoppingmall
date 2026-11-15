package com.hoppingmall.product.product.service

import com.hoppingmall.product.common.file.FileStorageProperties
import com.hoppingmall.product.common.file.domain.UploadStatus
import com.hoppingmall.product.common.file.domain.repository.UploadedFileRepository
import com.hoppingmall.product.common.file.exception.UnregisteredImageException
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class ProductImageLinker(
    private val uploadedFileRepository: UploadedFileRepository,
    private val storageProperties: FileStorageProperties
) {

    fun link(sellerId: Long, requestedUrls: List<String>?, currentUrls: Collection<String> = emptyList()): List<String> {
        val defaultUrl = storageProperties.defaultImageUrl
        val urls = requestedUrls?.ifEmpty { null } ?: return listOf(defaultUrl)
        val newUrls = urls.filter { it != defaultUrl && it !in currentUrls }.distinct()
        if (newUrls.isEmpty()) return urls

        val keys = newUrls.map { storageProperties.keyOf(it) ?: throw UnregisteredImageException() }
        val files = uploadedFileRepository.findAllByObjectKeyInForUpdate(keys).associateBy { it.objectKey }
        val now = LocalDateTime.now()
        keys.forEach { key ->
            val file = files[key] ?: throw UnregisteredImageException()
            if (file.status == UploadStatus.DELETING || !file.canBeLinkedBy(sellerId)) {
                throw UnregisteredImageException()
            }
            file.attach(now)
        }
        return urls
    }
}
