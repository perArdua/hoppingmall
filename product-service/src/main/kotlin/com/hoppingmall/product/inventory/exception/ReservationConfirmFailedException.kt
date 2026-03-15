package com.hoppingmall.product.inventory.exception

import com.hoppingmall.product.common.BusinessException
import com.hoppingmall.product.inventory.exception.code.InventoryErrorCode

class ReservationConfirmFailedException : BusinessException(InventoryErrorCode.RESERVATION_CONFIRM_FAILED)
