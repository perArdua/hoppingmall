package com.hoppingmall.product.inventory.domain

import java.io.Serializable

data class InventoryBucketId(
    val productId: Long = 0,
    val bucketNo: Int = 0
) : Serializable
