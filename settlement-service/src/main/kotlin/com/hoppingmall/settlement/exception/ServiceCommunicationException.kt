package com.hoppingmall.settlement.exception

import com.hoppingmall.settlement.exception.code.SettlementErrorCode

class ServiceCommunicationException(message: String) : SettlementException(SettlementErrorCode.SERVICE_COMMUNICATION_ERROR) {
    override val message: String = message
}
