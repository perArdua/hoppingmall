package com.hoppingmall.product.inventory.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.product.inventory.exception.code.InventoryErrorCode

class InventoryInvalidQuantityException : BusinessException(InventoryErrorCode.INVALID_RESERVE_QUANTITY)
