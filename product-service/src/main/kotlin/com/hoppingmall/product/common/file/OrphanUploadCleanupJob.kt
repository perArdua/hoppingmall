package com.hoppingmall.product.common.file

import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
@Profile("!test")
class OrphanUploadCleanupJob(
    private val orphanUploadCleaner: OrphanUploadCleaner
) {

    @Scheduled(cron = "#{@fileStorageProperties.orphanCron}")
    fun run() {
        orphanUploadCleaner.cleanUp(LocalDateTime.now())
    }
}
