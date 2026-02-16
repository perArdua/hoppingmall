package com.hoppingmall.mall.inventory.exception

import com.hoppingmall.mall.global.common.error.exception.BusinessException
import com.hoppingmall.mall.inventory.exception.code.InventoryErrorCode

class InventoryInsufficientStockException : BusinessException(InventoryErrorCode.INVENTORY_INSUFFICIENT_STOCK)
