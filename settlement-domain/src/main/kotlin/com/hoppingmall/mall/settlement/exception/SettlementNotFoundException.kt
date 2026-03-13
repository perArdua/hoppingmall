package com.hoppingmall.mall.settlement.exception

import com.hoppingmall.mall.settlement.exception.code.SettlementErrorCode

class SettlementNotFoundException : SettlementException(SettlementErrorCode.SETTLEMENT_NOT_FOUND)
