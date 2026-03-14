package com.hoppingmall.mall.inventory.service

import com.hoppingmall.mall.inventory.domain.repository.InventoryRepository
import com.hoppingmall.mall.inventory.dto.response.InventoryResponse
import com.hoppingmall.mall.inventory.exception.InventoryNotFoundException
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class InventoryQueryServiceImpl(
    private val inventoryRepository: InventoryRepository
) : InventoryQueryService {

    @Cacheable(cacheNames = ["inventory"], key = "#productId", sync = true)
    override fun getStock(productId: Long): InventoryResponse {
        val inventory = inventoryRepository.findByProductId(productId)
            ?: throw InventoryNotFoundException()

        return InventoryResponse.from(inventory)
    }
}
