package com.hoppingmall.product.product.service

import com.hoppingmall.cache.NotFoundMarker
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.repository.ProductImageRepository
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.dto.request.ProductSearchCondition
import com.hoppingmall.product.product.dto.response.ProductResponse
import org.springframework.cache.CacheManager
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
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

    override fun getProducts(pageable: Pageable): Slice<ProductResponse> {
        val productSlice = productRepository.findBy(pageable)
        return toProductResponseSlice(productSlice, pageable)
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

        val images = productImageRepository.findByProductIdOrderBySortOrder(productId)

        return ProductResponse.from(product, images)
    }

    override fun getProductsBySellerId(
        sellerId: Long,
        pageable: Pageable
    ): Slice<ProductResponse> {
        val productSlice = productRepository.findBySellerId(sellerId, pageable)
        return toProductResponseSlice(productSlice, pageable)
    }

    override fun getProductsByCategoryId(
        categoryId: Long,
        pageable: Pageable
    ): Slice<ProductResponse> {
        val productSlice = productRepository.findByCategoryId(categoryId, pageable)
        return toProductResponseSlice(productSlice, pageable)
    }

    override fun searchProducts(
        condition: ProductSearchCondition,
        pageable: Pageable
    ): Slice<ProductResponse> {
        val productSlice = productRepository.searchProducts(
            keyword = condition.keyword,
            categoryId = condition.categoryId,
            status = condition.status,
            minPrice = condition.minPrice,
            maxPrice = condition.maxPrice,
            pageable = pageable
        )
        return toProductResponseSlice(productSlice, pageable)
    }

    private fun toProductResponseSlice(
        productSlice: Slice<Product>,
        pageable: Pageable
    ): Slice<ProductResponse> {
        val productIds = productSlice.content.mapNotNull { it.id }
        val imageMap = productImageRepository.findByProductIdIn(productIds)
            .groupBy { it.productId }

        val productResponses = productSlice.content.map { product ->
            ProductResponse.from(product, imageMap[product.id!!] ?: emptyList())
        }

        return SliceImpl(productResponses, pageable, productSlice.hasNext())
    }
}
