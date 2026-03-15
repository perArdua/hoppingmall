package com.hoppingmall.settlement.exception

import com.hoppingmall.settlement.exception.code.SettlementErrorCode

class SettlementNotFoundException : SettlementException(SettlementErrorCode.SETTLEMENT_NOT_FOUND)
