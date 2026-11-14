package com.hoppingmall.product.inventory.service

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
@Profile("!test")
@ConditionalOnProperty(name = ["inventory.mode"], havingValue = "bucket")
class BucketBackfillOnStartup(
    private val bucketBackfillService: BucketBackfillService
) {
    private val logger = LoggerFactory.getLogger(BucketBackfillOnStartup::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun backfill() {
        try {
            bucketBackfillService.backfill()
        } catch (e: Exception) {
            logger.error("기동 때 버킷 채우기 실패", e)
        }
    }
}
