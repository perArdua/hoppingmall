package com.hoppingmall.product.inventory.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.product.inventory.exception.code.InventoryErrorCode

class InventoryBusyException : BusinessException(InventoryErrorCode.INVENTORY_BUSY)
