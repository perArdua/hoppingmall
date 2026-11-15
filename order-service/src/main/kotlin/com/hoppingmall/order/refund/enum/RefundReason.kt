package com.hoppingmall.order.refund.enum

enum class RefundReason(
    val acceptsNewRequest: Boolean,
    val isSellerFault: Boolean
) {
    CHANGE_OF_MIND(acceptsNewRequest = true, isSellerFault = false),
    DEFECTIVE(acceptsNewRequest = true, isSellerFault = true),
    WRONG_ITEM(acceptsNewRequest = true, isSellerFault = true),
    NOT_AS_DESCRIBED(acceptsNewRequest = true, isSellerFault = true),
    DEFECTIVE_PRODUCT(acceptsNewRequest = false, isSellerFault = true),
    WRONG_PRODUCT(acceptsNewRequest = false, isSellerFault = true),
    LATE_DELIVERY(acceptsNewRequest = false, isSellerFault = false),
    OTHER(acceptsNewRequest = false, isSellerFault = false),
}
