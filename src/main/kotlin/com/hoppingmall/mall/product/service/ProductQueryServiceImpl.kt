package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.product.domain.repository.ProductImageRepository
import com.hoppingmall.mall.product.domain.repository.ProductRepository
import com.hoppingmall.mall.product.dto.request.ProductSearchCondition
import com.hoppingmall.mall.product.dto.response.ProductResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class ProductQueryServiceImpl(
    private val productRepository: ProductRepository,
    private val productImageRepository: ProductImageRepository
): ProductQueryService {

    override fun getProducts(pageable: Pageable): Page<ProductResponse> {
        val productPage = productRepository.findAll(pageable)

        val productResponses = productPage.content.map { product ->
            val image = productImageRepository.findByProductId(product.id!!)
            ProductResponse.from(product, image)
        }

        return PageImpl(
            productResponses,
            pageable,
            productPage.totalElements
        )
    }

    @Cacheable(cacheNames = ["product"], key = "#productId", sync = true)
    override fun getProductById(productId: Long): ProductResponse? {
        val product = productRepository.findNullableById(productId)
            ?: return null

        val image = productImageRepository.findByProductId(productId)

        return ProductResponse.from(product, image)
    }

    override fun getProductsBySellerId(
        sellerId: Long,
        pageable: Pageable
    ): Page<ProductResponse> {
        val productPage = productRepository.findBySellerId(sellerId, pageable)

        val productResponses = productPage.content.map { product ->
            val image = productImageRepository.findByProductId(product.id!!)
            ProductResponse.from(product, image)
        }

        return PageImpl(
            productResponses,
            pageable,
            productPage.totalElements
        )
    }

    override fun getProductsByCategoryId(
        categoryId: Long,
        pageable: Pageable
    ): Page<ProductResponse> {
        val productPage = productRepository.findByCategoryId(categoryId, pageable)

        val productResponses = productPage.content.map { product ->
            val image = productImageRepository.findByProductId(product.id!!)
            ProductResponse.from(product, image)
        }

        return PageImpl(
            productResponses,
            pageable,
            productPage.totalElements
        )
    }

    override fun searchProducts(
        condition: ProductSearchCondition,
        pageable: Pageable
    ): Page<ProductResponse> {
        val productPage = productRepository.searchProducts(
            keyword = condition.keyword,
            categoryId = condition.categoryId,
            status = condition.status,
            minPrice = condition.minPrice,
            maxPrice = condition.maxPrice,
            pageable = pageable
        )

        val productResponses = productPage.content.map { product ->
            val image = productImageRepository.findByProductId(product.id!!)
            ProductResponse.from(product, image)
        }

        return PageImpl(
            productResponses,
            pageable,
            productPage.totalElements
        )
    }
}
