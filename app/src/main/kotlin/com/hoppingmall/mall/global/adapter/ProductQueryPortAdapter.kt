package com.hoppingmall.mall.global.adapter

import com.hoppingmall.mall.product.api.ProductInfo
import com.hoppingmall.mall.product.api.ProductQueryPort
import com.hoppingmall.mall.product.domain.repository.ProductImageRepository
import com.hoppingmall.mall.product.domain.repository.ProductRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class ProductQueryPortAdapter(
    private val productRepository: ProductRepository,
    private val productImageRepository: ProductImageRepository
) : ProductQueryPort {

    override fun findById(id: Long): ProductInfo? {
        return productRepository.findByIdOrNull(id)?.let {
            ProductInfo(
                id = it.id!!,
                name = it.name,
                price = it.price,
                sellerId = it.sellerId,
                status = it.status.name
            )
        }
    }

    override fun findAllByIds(ids: List<Long>): List<ProductInfo> {
        return productRepository.findAllById(ids).map {
            ProductInfo(
                id = it.id!!,
                name = it.name,
                price = it.price,
                sellerId = it.sellerId,
                status = it.status.name
            )
        }
    }

    override fun findMainImageUrl(productId: Long): String? {
        return productImageRepository.findByProductIdOrderBySortOrder(productId)
            .firstOrNull()?.imageUrl
    }
}
