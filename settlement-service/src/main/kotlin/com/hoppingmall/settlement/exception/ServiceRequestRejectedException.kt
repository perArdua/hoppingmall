package com.hoppingmall.settlement.exception

import com.hoppingmall.settlement.exception.code.SettlementErrorCode

class ServiceRequestRejectedException(message: String) : SettlementException(SettlementErrorCode.SERVICE_REQUEST_REJECTED) {
    override val message: String = message
}
