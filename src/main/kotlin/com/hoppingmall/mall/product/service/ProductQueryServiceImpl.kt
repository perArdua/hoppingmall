package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.global.common.config.cache.NotFoundMarker
import com.hoppingmall.mall.product.domain.Product
import com.hoppingmall.mall.product.domain.repository.ProductImageRepository
import com.hoppingmall.mall.product.domain.repository.ProductRepository
import com.hoppingmall.mall.product.dto.request.ProductSearchCondition
import com.hoppingmall.mall.product.dto.response.ProductResponse
import org.springframework.cache.CacheManager
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
    private val productImageRepository: ProductImageRepository,
    private val cacheManager: CacheManager
) : ProductQueryService {

    override fun getProducts(pageable: Pageable): Page<ProductResponse> {
        val productPage = productRepository.findAll(pageable)
        return toProductResponsePage(productPage, pageable)
    }

    @Cacheable(cacheNames = ["product"], key = "#productId", sync = true)
    override fun getProductById(productId: Long): ProductResponse? {
        val notFoundCache = cacheManager.getCache("product:notfound")
        val cached = notFoundCache?.get(productId)
        if (cached != null && NotFoundMarker.isNotFound(cached.get())) {
            return null
        }

        val product = productRepository.findNullableById(productId)
        if (product == null) {
            notFoundCache?.put(productId, NotFoundMarker.INSTANCE)
            return null
        }

        val image = productImageRepository.findByProductId(productId)

        return ProductResponse.from(product, image)
    }

    override fun getProductsBySellerId(
        sellerId: Long,
        pageable: Pageable
    ): Page<ProductResponse> {
        val productPage = productRepository.findBySellerId(sellerId, pageable)
        return toProductResponsePage(productPage, pageable)
    }

    override fun getProductsByCategoryId(
        categoryId: Long,
        pageable: Pageable
    ): Page<ProductResponse> {
        val productPage = productRepository.findByCategoryId(categoryId, pageable)
        return toProductResponsePage(productPage, pageable)
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
        return toProductResponsePage(productPage, pageable)
    }

    private fun toProductResponsePage(
        productPage: Page<Product>,
        pageable: Pageable
    ): Page<ProductResponse> {
        val productIds = productPage.content.mapNotNull { it.id }
        val imageMap = productImageRepository.findByProductIdIn(productIds)
            .associateBy { it.productId }

        val productResponses = productPage.content.map { product ->
            ProductResponse.from(product, imageMap[product.id])
        }

        return PageImpl(productResponses, pageable, productPage.totalElements)
    }
}
