package com.hoppingmall.product.common.file.exception

import com.hoppingmall.product.common.file.exception.code.FileErrorCode

class FileUploadException(detail: String) : FileException(FileErrorCode.FILE_UPLOAD_ERROR) {
    override val message: String = "${FileErrorCode.FILE_UPLOAD_ERROR.message} $detail"
}
