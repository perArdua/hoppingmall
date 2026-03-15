package com.hoppingmall.payment.port.exception

class InventoryRestoreFailedException(productId: Long, quantity: Int, cause: Throwable) :
    RuntimeException("재고 복구 실패: productId=$productId, quantity=$quantity", cause)
