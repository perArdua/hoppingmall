package com.hoppingmall.product.common.file

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "file.storage")
class FileStorageProperties {

    var endpoint: String = ""
    var region: String = "ap-northeast-2"
    var bucket: String = "hoppingmall-product"
    var accessKey: String = ""
    var secretKey: String = ""
    var pathStyleAccess: Boolean = true
    var publicBaseUrl: String = "http://localhost:9000/hoppingmall-product"
    var defaultImageKey: String = "product/images/default-product.jpg"
    var connectTimeoutMs: Long = 3000
    var socketTimeoutMs: Long = 10000
    var orphanTtlHours: Long = 24
    var orphanBatchSize: Int = 100
    var orphanCron: String = "0 30 * * * *"

    val defaultImageUrl: String
        get() = publicUrl(defaultImageKey)

    fun publicUrl(key: String): String = "${normalizedBase()}/$key"

    fun keyOf(url: String): String? {
        val prefix = "${normalizedBase()}/"
        if (!url.startsWith(prefix)) return null
        return url.removePrefix(prefix).takeIf { it.isNotBlank() }
    }

    private fun normalizedBase(): String = publicBaseUrl.trimEnd('/')
}
