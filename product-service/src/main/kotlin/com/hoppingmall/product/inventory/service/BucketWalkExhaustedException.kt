package com.hoppingmall.product.inventory.service

class BucketWalkExhaustedException(val productId: Long) : RuntimeException("bucket walk exhausted for product $productId")
