package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.dto.request.InventoryInitRequest
import com.hoppingmall.product.inventory.dto.request.InventoryUpdateRequest
import com.hoppingmall.product.inventory.dto.response.InventoryResponse
import com.hoppingmall.product.inventory.exception.InventoryAlreadyExistsException
import com.hoppingmall.product.inventory.exception.InventoryNotFoundException
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.exception.ProductNotFoundException
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class InventoryCommandServiceImpl(
    private val inventoryRepository: InventoryRepository,
    private val productRepository: ProductRepository
) : InventoryCommandService {

    override fun initStock(request: InventoryInitRequest): InventoryResponse {
        if (!productRepository.existsById(request.productId)) {
            throw ProductNotFoundException()
        }

        if (inventoryRepository.existsByProductId(request.productId)) {
            throw InventoryAlreadyExistsException()
        }

        val inventory = Inventory.create(
            productId = request.productId,
            stockQuantity = request.stockQuantity
        )
        val savedInventory = inventoryRepository.save(inventory)
        return InventoryResponse.from(savedInventory)
    }

    @CacheEvict(cacheNames = ["inventory"], key = "#productId")
    override fun updateStock(productId: Long, request: InventoryUpdateRequest): InventoryResponse {
        val inventory = inventoryRepository.findByProductIdForUpdate(productId)
            ?: throw InventoryNotFoundException()

        inventory.stockQuantity = request.stockQuantity
        return InventoryResponse.from(inventory)
    }

    @CacheEvict(cacheNames = ["inventory"], key = "#productId")
    override fun decreaseStock(productId: Long, quantity: Int) {
        val inventory = inventoryRepository.findByProductIdForUpdate(productId)
            ?: throw InventoryNotFoundException()

        inventory.decreaseStock(quantity)
    }

    @CacheEvict(cacheNames = ["inventory"], key = "#productId")
    override fun increaseStock(productId: Long, quantity: Int) {
        val inventory = inventoryRepository.findByProductIdForUpdate(productId)
            ?: throw InventoryNotFoundException()

        inventory.increaseStock(quantity)
    }
}
