package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.product.domain.Product
import com.hoppingmall.mall.product.domain.repository.ProductRepository
import com.hoppingmall.mall.product.exception.ProductNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ProductQueryServiceImpl(
    private val productRepository: ProductRepository
): ProductQueryService {

    override fun getProducts(pageable: Pageable): Page<Product> {
        return productRepository.findAll(pageable)
    }

    override fun getProductById(productId: Long): Product {
        return productRepository.findNullableById(productId)
            ?: throw ProductNotFoundException()
    }

    override fun getProductsBySellerId(
        sellerId: Long,
        pageable: Pageable
    ): Page<Product> {
        return productRepository.findBySellerId(sellerId, pageable)
    }
}