package com.hoppingmall.mall.settlement.exception

import com.hoppingmall.mall.settlement.exception.code.SettlementErrorCode

class SettlementInvalidStatusException : SettlementException(SettlementErrorCode.SETTLEMENT_INVALID_STATUS)
