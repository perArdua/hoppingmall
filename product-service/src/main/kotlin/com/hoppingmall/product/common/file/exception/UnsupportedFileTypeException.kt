package com.hoppingmall.product.common.file.exception

import com.hoppingmall.product.common.file.exception.code.FileErrorCode

class UnsupportedFileTypeException(allowedExtensions: Collection<String>) :
    FileException(FileErrorCode.UNSUPPORTED_FILE_TYPE) {
    override val message: String = "${FileErrorCode.UNSUPPORTED_FILE_TYPE.message} 허용된 형식: ${allowedExtensions.joinToString(", ")}"
}
