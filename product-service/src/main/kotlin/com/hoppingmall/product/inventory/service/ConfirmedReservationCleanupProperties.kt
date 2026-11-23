package com.hoppingmall.product.inventory.service

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "inventory.reservation.confirmed-cleanup")
class ConfirmedReservationCleanupProperties {

    var cron: String = "0 45 4 * * ?"
    var ageDays: Long = 60
    var batchSize: Int = 500
    var maxBatches: Int = 100
}
