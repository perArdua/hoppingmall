package com.hoppingmall.mall.global.file.exception

import com.hoppingmall.mall.global.common.error.exception.BusinessException
import com.hoppingmall.mall.global.file.exception.code.FileErrorCode

open class FileException(
    errorCode: FileErrorCode
) : BusinessException(errorCode) 