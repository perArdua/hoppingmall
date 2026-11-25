package com.hoppingmall.product.inventory.service

import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("!test")
class InventoryModeRecheckJob(
    private val inventoryModeGuard: InventoryModeGuard
) {

    @Scheduled(fixedDelayString = "\${inventory.mode-guard.recheck-ms:5000}", initialDelayString = "\${inventory.mode-guard.recheck-ms:5000}")
    fun run() {
        inventoryModeGuard.recheck()
    }
}
