package com.hoppingmall.product.product.service

import org.springframework.data.repository.findByIdOrNull
import com.hoppingmall.product.category.domain.repository.CategoryRepository
import com.hoppingmall.product.category.exception.CategoryNotFoundException
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.dto.request.InventoryInitRequest
import com.hoppingmall.product.inventory.service.InventoryCommandService
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.ProductImage
import com.hoppingmall.product.product.domain.repository.ProductImageRepository
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.dto.request.ProductCreateRequest
import com.hoppingmall.product.product.dto.request.ProductUpdateRequest
import com.hoppingmall.product.product.dto.response.ProductResponse
import com.hoppingmall.product.product.exception.ProductNotFoundException
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class ProductCommandServiceImpl(
    private val productRepository: ProductRepository,
    private val productImageRepository: ProductImageRepository,
    private val categoryRepository: CategoryRepository,
    private val productImageLinker: ProductImageLinker,
    private val inventoryCommandService: InventoryCommandService,
    private val inventoryRepository: InventoryRepository
) : ProductCommandService {

    override fun createProduct(request: ProductCreateRequest): ProductResponse {
        val categoryId = requireNotNull(request.categoryId)
        val category = categoryRepository.findNullableById(categoryId) ?: throw CategoryNotFoundException()
        val sellerId = requireNotNull(request.sellerId)
        val imageUrls = productImageLinker.link(sellerId, request.imageUrls)

        val product = Product.create(
            sellerId = sellerId,
            categoryId = categoryId,
            name = request.name,
            description = request.description,
            price = request.price,
            status = request.status
        )
        val savedProduct = productRepository.save(product)

        val productImages = imageUrls.mapIndexed { index, url ->
            ProductImage.create(
                productId = savedProduct.id!!,
                imageUrl = url,
                sortOrder = index
            )
        }
        val savedImages = productImageRepository.saveAll(productImages)
        inventoryCommandService.initStock(
            InventoryInitRequest(productId = savedProduct.id!!, stockQuantity = request.stockQuantity ?: 0)
        )

        return ProductResponse.from(savedProduct, savedImages, category.name)
    }

    @CacheEvict(cacheNames = ["product"], key = "#productId")
    override fun updateProduct(productId: Long, request: ProductUpdateRequest): ProductResponse {
        val categoryId = request.categoryId ?: throw CategoryNotFoundException()
        val category = categoryRepository.findNullableById(categoryId) ?: throw CategoryNotFoundException()

        val product = productRepository.findByIdOrNull(productId) ?: throw ProductNotFoundException() 

        product.update(
            name = request.name,
            description = request.description,
            price = request.price,
            categoryId = categoryId,
            status = request.status
        )

        val updatedProduct = productRepository.save(product)

        val existingImages = productImageRepository.findByProductIdOrderBySortOrder(productId)
        val requestedImageUrls = request.imageUrls
            ?: return ProductResponse.from(updatedProduct, existingImages, category.name)
        val imageUrls = productImageLinker.link(product.sellerId, requestedImageUrls, existingImages.map { it.imageUrl })
        existingImages.forEach { it.softDelete() }

        val newImages = imageUrls.mapIndexed { index, url ->
            ProductImage.create(
                productId = productId,
                imageUrl = url,
                sortOrder = index
            )
        }
        val savedImages = productImageRepository.saveAll(newImages)

        return ProductResponse.from(updatedProduct, savedImages, category.name)
    }

    @Caching(
        evict = [
            CacheEvict(cacheNames = ["product"], key = "#productId"),
            CacheEvict(cacheNames = ["inventory"], key = "#productId")
        ]
    )
    override fun deleteProduct(productId: Long) {
        val product = productRepository.findByIdOrNull(productId) ?: throw ProductNotFoundException() 

        val productImages = productImageRepository.findByProductIdOrderBySortOrder(productId)
        productImages.forEach { it.softDelete() }

        inventoryRepository.findByProductIdForUpdate(productId)?.softDelete()
        product.softDelete()
    }
}
