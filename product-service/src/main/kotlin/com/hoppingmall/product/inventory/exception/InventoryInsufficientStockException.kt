package com.hoppingmall.product.inventory.exception

import com.hoppingmall.product.common.BusinessException
import com.hoppingmall.product.inventory.exception.code.InventoryErrorCode

class InventoryInsufficientStockException : BusinessException(InventoryErrorCode.INVENTORY_INSUFFICIENT_STOCK)
