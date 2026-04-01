package com.hoppingmall.product.common.file.exception

import com.hoppingmall.product.common.file.exception.code.FileErrorCode

class UnsupportedDomainException(allowedDomains: Collection<String>) :
    FileException(FileErrorCode.UNSUPPORTED_DOMAIN) {
    override val message: String = "${FileErrorCode.UNSUPPORTED_DOMAIN.message} 허용된 도메인: ${allowedDomains.joinToString(", ")}"
}
