package com.hoppingmall.product.inventory.exception

import com.hoppingmall.product.common.BusinessException
import com.hoppingmall.product.inventory.exception.code.InventoryErrorCode

class InventoryAlreadyExistsException : BusinessException(InventoryErrorCode.INVENTORY_ALREADY_EXISTS)
