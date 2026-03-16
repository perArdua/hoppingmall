package com.hoppingmall.settlement.exception

import com.hoppingmall.settlement.common.BusinessException
import com.hoppingmall.settlement.exception.code.SettlementErrorCode

open class SettlementException(errorCode: SettlementErrorCode) : BusinessException(errorCode)
