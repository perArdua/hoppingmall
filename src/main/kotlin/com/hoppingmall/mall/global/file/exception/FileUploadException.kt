package com.hoppingmall.mall.global.file.exception

import com.hoppingmall.mall.global.file.exception.code.FileErrorCode

class FileUploadException(message: String) : FileException(FileErrorCode.FILE_UPLOAD_ERROR) {
    override val message: String = "${FileErrorCode.FILE_UPLOAD_ERROR.message} $message"
} 