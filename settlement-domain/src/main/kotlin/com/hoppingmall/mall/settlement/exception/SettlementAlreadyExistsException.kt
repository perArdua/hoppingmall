package com.hoppingmall.mall.settlement.exception

import com.hoppingmall.mall.settlement.exception.code.SettlementErrorCode

class SettlementAlreadyExistsException : SettlementException(SettlementErrorCode.SETTLEMENT_ALREADY_EXISTS)
