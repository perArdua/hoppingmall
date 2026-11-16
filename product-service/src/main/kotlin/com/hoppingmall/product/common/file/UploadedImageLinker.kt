package com.hoppingmall.product.common.file

import com.hoppingmall.product.common.file.domain.UploadStatus
import com.hoppingmall.product.common.file.domain.repository.UploadedFileRepository
import com.hoppingmall.product.common.file.exception.UnregisteredImageException
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class UploadedImageLinker(
    private val uploadedFileRepository: UploadedFileRepository,
    private val storageProperties: FileStorageProperties
) {

    fun attach(ownerId: Long, urls: Collection<String>) {
        val newUrls = urls.filter { it != storageProperties.defaultImageUrl }.distinct()
        if (newUrls.isEmpty()) return

        val keys = newUrls.map { storageProperties.keyOf(it) ?: throw UnregisteredImageException() }
        val files = uploadedFileRepository.findAllByObjectKeyInForUpdate(keys).associateBy { it.objectKey }
        val now = LocalDateTime.now()
        keys.forEach { key ->
            val file = files[key] ?: throw UnregisteredImageException()
            if (file.status == UploadStatus.DELETING || !file.canBeLinkedBy(ownerId)) {
                throw UnregisteredImageException()
            }
            file.attach(now)
        }
    }
}
