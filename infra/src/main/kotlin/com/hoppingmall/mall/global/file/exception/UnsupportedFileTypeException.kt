package com.hoppingmall.mall.global.file.exception

import com.hoppingmall.mall.global.file.constant.FileUploadMessages
import com.hoppingmall.mall.global.file.exception.code.FileErrorCode

class UnsupportedFileTypeException(allowedExtensions: List<String>) : 
    FileException(FileErrorCode.UNSUPPORTED_FILE_TYPE) {
    override val message: String = "${FileErrorCode.UNSUPPORTED_FILE_TYPE.message} ${FileUploadMessages.ALLOWED_FORMATS_PREFIX}: ${allowedExtensions.joinToString(", ")}"
} 