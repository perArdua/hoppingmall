package com.hoppingmall.mall.settlement.exception

import com.hoppingmall.mall.global.common.error.exception.BusinessException
import com.hoppingmall.mall.settlement.exception.code.SettlementErrorCode

open class SettlementException(
    errorCode: SettlementErrorCode
) : BusinessException(errorCode)
