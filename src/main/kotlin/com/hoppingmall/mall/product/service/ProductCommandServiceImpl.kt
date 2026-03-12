package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.category.domain.repository.CategoryRepository
import com.hoppingmall.mall.category.exception.CategoryNotFoundException
import com.hoppingmall.mall.global.file.config.FileUploadConfig
import com.hoppingmall.mall.product.domain.Product
import com.hoppingmall.mall.product.domain.ProductImage
import com.hoppingmall.mall.product.domain.repository.ProductImageRepository
import com.hoppingmall.mall.product.domain.repository.ProductRepository
import com.hoppingmall.mall.product.dto.request.ProductCreateRequest
import com.hoppingmall.mall.product.dto.request.ProductUpdateRequest
import com.hoppingmall.mall.product.dto.response.ProductResponse
import com.hoppingmall.mall.product.exception.ProductNotFoundException
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ProductCommandServiceImpl(
    private val productRepository: ProductRepository,
    private val productImageRepository: ProductImageRepository,
    private val categoryRepository: CategoryRepository,
    private val fileUploadConfig: FileUploadConfig
) : ProductCommandService {

    override fun createProduct(request: ProductCreateRequest): ProductResponse {
        if (!categoryRepository.existsById(request.categoryId)) {
            throw CategoryNotFoundException()
        }

        val product = Product.create(
            sellerId = request.sellerId,
            categoryId = request.categoryId,
            name = request.name,
            description = request.description,
            price = request.price,
            status = request.status
        )
        val savedProduct = productRepository.save(product)

        val imageUrls = request.imageUrls?.ifEmpty { null } ?: listOf(fileUploadConfig.defaultImagePath)
        val productImages = imageUrls.mapIndexed { index, url ->
            ProductImage.create(
                productId = savedProduct.id!!,
                imageUrl = url,
                sortOrder = index
            )
        }
        val savedImages = productImageRepository.saveAll(productImages)

        return ProductResponse.from(savedProduct, savedImages)
    }

    @CacheEvict(cacheNames = ["product"], key = "#productId")
    override fun updateProduct(productId: Long, request: ProductUpdateRequest): ProductResponse {
        if (!categoryRepository.existsById(request.categoryId)) {
            throw CategoryNotFoundException()
        }

        val product = productRepository.findById(productId)
            .orElseThrow { ProductNotFoundException() }

        product.categoryId = request.categoryId
        product.name = request.name
        product.description = request.description
        product.price = request.price
        product.status = request.status

        val updatedProduct = productRepository.save(product)

        val existingImages = productImageRepository.findByProductIdOrderBySortOrder(productId)
        existingImages.forEach { it.softDelete() }

        val imageUrls = request.imageUrls?.ifEmpty { null } ?: listOf(fileUploadConfig.defaultImagePath)
        val newImages = imageUrls.mapIndexed { index, url ->
            ProductImage.create(
                productId = productId,
                imageUrl = url,
                sortOrder = index
            )
        }
        val savedImages = productImageRepository.saveAll(newImages)

        return ProductResponse.from(updatedProduct, savedImages)
    }

    @CacheEvict(cacheNames = ["product"], key = "#productId")
    override fun deleteProduct(productId: Long) {
        val product = productRepository.findById(productId)
            .orElseThrow { ProductNotFoundException() }

        val productImages = productImageRepository.findByProductIdOrderBySortOrder(productId)
        productImages.forEach { it.softDelete() }

        product.softDelete()
    }
}
