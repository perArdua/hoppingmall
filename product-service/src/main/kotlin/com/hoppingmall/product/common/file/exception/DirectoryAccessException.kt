package com.hoppingmall.product.common.file.exception

import com.hoppingmall.product.common.file.exception.code.FileErrorCode

class DirectoryAccessException(directory: String) : FileException(FileErrorCode.DIRECTORY_ACCESS_ERROR) {
    override val message: String = "${FileErrorCode.DIRECTORY_ACCESS_ERROR.message} 디렉토리: $directory"
}
