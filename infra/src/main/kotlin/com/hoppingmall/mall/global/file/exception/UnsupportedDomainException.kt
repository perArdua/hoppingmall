package com.hoppingmall.mall.global.file.exception

import com.hoppingmall.mall.global.file.constant.FileUploadMessages
import com.hoppingmall.mall.global.file.exception.code.FileErrorCode

class UnsupportedDomainException(allowedDomains: List<String>) : 
    FileException(FileErrorCode.UNSUPPORTED_DOMAIN) {
    override val message: String = "${FileErrorCode.UNSUPPORTED_DOMAIN.message} ${FileUploadMessages.ALLOWED_DOMAINS_PREFIX}: ${allowedDomains.joinToString(", ")}"
} 