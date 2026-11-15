package com.hoppingmall.product.common.file

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "file.upload")
class FileUploadConfig {

    var maxFileSize: Long = 5 * 1024 * 1024
    var allowedExtensions: Set<String> = setOf("jpg", "jpeg", "png", "gif", "webp")
    var allowedDomains: Set<String> = setOf("product")
    var subDirectory: String = "images"
}
