package com.hoppingmall.product.inventory.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.product.inventory.exception.code.InventoryErrorCode

class InventoryBudgetExceededException : BusinessException(InventoryErrorCode.INVENTORY_BUDGET_EXCEEDED)
