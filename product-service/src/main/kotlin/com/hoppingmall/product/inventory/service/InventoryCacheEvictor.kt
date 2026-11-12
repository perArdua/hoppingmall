package com.hoppingmall.product.inventory.service

import org.springframework.beans.factory.ObjectProvider
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Component
class InventoryCacheEvictor(
    private val cacheManagerProvider: ObjectProvider<CacheManager>
) {

    fun evictAfterCommit(productId: Long) {
        val cache = cacheManagerProvider.getIfAvailable()?.getCache(INVENTORY_CACHE) ?: return
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                cache.evict(productId)
            }
        })
    }

    companion object {
        private const val INVENTORY_CACHE = "inventory"
    }
}
