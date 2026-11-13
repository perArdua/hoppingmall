package com.hoppingmall.product.product.service

import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.exception.ProductException
import com.hoppingmall.product.product.exception.code.ProductErrorCode
import org.springframework.stereotype.Component

@Component
class ProductOwnershipPolicy(
    private val productRepository: ProductRepository
) {

    fun sellerIdForCreate(requester: UserPrincipal, requestedSellerId: Long?): Long {
        if (!requester.isAdmin()) return requester.getUserId()
        return requestedSellerId ?: throw ProductException(ProductErrorCode.PRODUCT_SELLER_REQUIRED)
    }

    fun checkOwner(productId: Long, requester: UserPrincipal) {
        if (requester.isAdmin()) return
        val product = productRepository.findNullableById(productId) ?: return
        if (product.sellerId != requester.getUserId()) {
            throw ProductException(ProductErrorCode.PRODUCT_ACCESS_DENIED)
        }
    }

    private fun UserPrincipal.isAdmin(): Boolean = getRole() == ADMIN_ROLE

    companion object {
        private const val ADMIN_ROLE = "ADMIN"
    }
}
