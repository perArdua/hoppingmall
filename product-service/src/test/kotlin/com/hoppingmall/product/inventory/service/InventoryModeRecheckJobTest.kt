package com.hoppingmall.product.inventory.service

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify

@DisplayName("InventoryModeRecheckJob")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class InventoryModeRecheckJobTest {

    @Mock
    private lateinit var inventoryModeGuard: InventoryModeGuard

    @InjectMocks
    private lateinit var job: InventoryModeRecheckJob

    @Test
    fun 주기마다_재고_모드_기록을_다시_확인한다() {
        job.run()

        verify(inventoryModeGuard).recheck()
    }
}
