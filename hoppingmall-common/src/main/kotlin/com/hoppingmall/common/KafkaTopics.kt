package com.hoppingmall.common

object KafkaTopics {
    const val PAYMENT = "payment"
    const val POINT_EARN_REQUEST = "point-earn-request"
    const val NOTIFICATION = "notification"
    const val MEMBERSHIP_UPDATE_REQUEST = "membership-update-request"
    const val PAYMENT_COMPENSATION = "payment-compensation"
    const val PAYMENT_REVERSAL = "payment-reversal"
    const val REFUND_COMPLETION = "refund-completion"
    const val COUPON_RESTORE = "coupon-restore"
    const val COUPON_RESTORE_DLQ = "coupon-restore-dlq"
}
