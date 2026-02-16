package com.hoppingmall.mall.inventory.exception

import com.hoppingmall.mall.global.common.error.exception.BusinessException
import com.hoppingmall.mall.inventory.exception.code.InventoryErrorCode

class InventoryNotFoundException : BusinessException(InventoryErrorCode.INVENTORY_NOT_FOUND)
