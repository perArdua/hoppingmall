package com.hoppingmall.mall.settlement.exception

import com.hoppingmall.mall.settlement.exception.code.SettlementErrorCode

class SettlementSellerNotFoundException : SettlementException(SettlementErrorCode.SETTLEMENT_SELLER_NOT_FOUND)
