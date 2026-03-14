package com.hoppingmall.product.inventory.exception

import com.hoppingmall.product.common.BusinessException
import com.hoppingmall.product.inventory.exception.code.InventoryErrorCode

class InventoryNotFoundException : BusinessException(InventoryErrorCode.INVENTORY_NOT_FOUND)
