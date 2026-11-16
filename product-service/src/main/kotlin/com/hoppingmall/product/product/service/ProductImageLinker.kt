package com.hoppingmall.product.product.service

import com.hoppingmall.product.common.file.FileStorageProperties
import com.hoppingmall.product.common.file.UploadedImageLinker
import org.springframework.stereotype.Component

@Component
class ProductImageLinker(
    private val uploadedImageLinker: UploadedImageLinker,
    private val storageProperties: FileStorageProperties
) {

    fun link(sellerId: Long, requestedUrls: List<String>?, currentUrls: Collection<String> = emptyList()): List<String> {
        val urls = requestedUrls?.ifEmpty { null } ?: return listOf(storageProperties.defaultImageUrl)
        uploadedImageLinker.attach(sellerId, urls.filter { it !in currentUrls })
        return urls
    }
}
