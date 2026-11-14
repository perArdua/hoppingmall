package com.hoppingmall.product.internal

import com.hoppingmall.product.inventory.service.BucketBackfillService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/api/v1/inventory/buckets")
@ConditionalOnProperty(name = ["inventory.mode"], havingValue = "bucket")
class InternalInventoryBucketController(
    private val bucketBackfillService: BucketBackfillService
) {

    @PostMapping("/backfill")
    fun backfill(): ResponseEntity<BucketBackfillService.BackfillResult> =
        ResponseEntity.ok(bucketBackfillService.backfill())
}
