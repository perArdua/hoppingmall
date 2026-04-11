package com.hoppingmall.product.product.service

import org.springframework.data.repository.findByIdOrNull
import com.hoppingmall.product.category.domain.repository.CategoryRepository
import com.hoppingmall.product.category.exception.CategoryNotFoundException
import com.hoppingmall.product.common.file.FileUploadConfig
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.ProductImage
import com.hoppingmall.product.product.domain.repository.ProductImageRepository
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.dto.request.ProductCreateRequest
import com.hoppingmall.product.product.dto.request.ProductUpdateRequest
import com.hoppingmall.product.product.dto.response.ProductResponse
import com.hoppingmall.product.product.exception.ProductNotFoundException
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
        if (categoryRepository.findNullableById(request.categoryId) == null) {
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
        if (request.categoryId == null || categoryRepository.findNullableById(request.categoryId) == null) {
            throw CategoryNotFoundException()
        }

        val product = productRepository.findByIdOrNull(productId) ?: throw ProductNotFoundException() 

        product.update(
            name = request.name,
            description = request.description,
            price = request.price,
            categoryId = request.categoryId,
            status = request.status
        )

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
        val product = productRepository.findByIdOrNull(productId) ?: throw ProductNotFoundException() 

        val productImages = productImageRepository.findByProductIdOrderBySortOrder(productId)
        productImages.forEach { it.softDelete() }

        product.softDelete()
    }
}
