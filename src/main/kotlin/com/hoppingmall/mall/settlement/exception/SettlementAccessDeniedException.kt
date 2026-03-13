package com.hoppingmall.mall.settlement.exception

import com.hoppingmall.mall.settlement.exception.code.SettlementErrorCode

class SettlementAccessDeniedException : SettlementException(SettlementErrorCode.SETTLEMENT_ACCESS_DENIED)
