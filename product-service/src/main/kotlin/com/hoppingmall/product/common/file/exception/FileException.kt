package com.hoppingmall.product.common.file.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.product.common.file.exception.code.FileErrorCode

open class FileException(
    errorCode: FileErrorCode
) : BusinessException(errorCode)
