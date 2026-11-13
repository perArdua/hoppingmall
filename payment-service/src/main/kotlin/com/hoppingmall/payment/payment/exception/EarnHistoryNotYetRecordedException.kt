package com.hoppingmall.payment.payment.exception

class EarnHistoryNotYetRecordedException(paymentId: Long) :
    IllegalStateException("적립 이력이 아직 없어 포인트를 회수할 수 없음: paymentId=$paymentId")
