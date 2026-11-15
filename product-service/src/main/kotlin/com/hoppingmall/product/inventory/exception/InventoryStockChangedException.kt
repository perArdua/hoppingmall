package com.hoppingmall.product.inventory.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.product.inventory.exception.code.InventoryErrorCode

class InventoryStockChangedException : BusinessException(InventoryErrorCode.INVENTORY_STOCK_CHANGED)
