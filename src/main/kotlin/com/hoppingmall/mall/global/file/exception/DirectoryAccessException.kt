package com.hoppingmall.mall.global.file.exception

import com.hoppingmall.mall.global.file.constant.FileUploadMessages
import com.hoppingmall.mall.global.file.exception.code.FileErrorCode

class DirectoryAccessException(directory: String) : FileException(FileErrorCode.DIRECTORY_ACCESS_ERROR) {
    override val message: String = "${FileErrorCode.DIRECTORY_ACCESS_ERROR.message} ${FileUploadMessages.DIRECTORY_PREFIX}: $directory"
} 