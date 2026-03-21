package com.hoppingmall.product.wishlist.exception

import com.hoppingmall.common.BusinessException
import com.hoppingmall.product.wishlist.exception.code.WishlistErrorCode

class WishlistNotFoundException : BusinessException(WishlistErrorCode.WISHLIST_NOT_FOUND)
