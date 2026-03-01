package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.category.domain.repository.CategoryRepository
import com.hoppingmall.mall.category.exception.CategoryNotFoundException
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
    private val categoryRepository: CategoryRepository
) : ProductCommandService {

    companion object {
        private const val DEFAULT_IMAGE_PATH = "D:/hoppingmall/product/images/default-product.jpg"
    }

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

        val imageUrl = request.imageUrl ?: DEFAULT_IMAGE_PATH
        val productImage = ProductImage.create(
            productId = savedProduct.id!!,
            imageUrl = imageUrl
        )
        val savedProductImage = productImageRepository.save(productImage)

        return ProductResponse.from(savedProduct, savedProductImage)
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

        val imageUrl = request.imageUrl ?: DEFAULT_IMAGE_PATH
        val existingImage = productImageRepository.findByProductId(productId)
        
        val updatedProductImage = if (existingImage != null) {
            existingImage.imageUrl = imageUrl
            productImageRepository.save(existingImage)
        } else {
            val newProductImage = ProductImage.create(
                productId = productId,
                imageUrl = imageUrl
            )
            productImageRepository.save(newProductImage)
        }

        return ProductResponse.from(updatedProduct, updatedProductImage)
    }

    @CacheEvict(cacheNames = ["product"], key = "#productId")
    override fun deleteProduct(productId: Long) {
        if (!productRepository.existsById(productId)) {
            throw ProductNotFoundException()
        }

        val productImage = productImageRepository.findByProductId(productId)
        productImage?.let { productImageRepository.delete(it) }

        productRepository.deleteById(productId)
    }
} 