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
import org.mockito.kotlin.whenever

@DisplayName("BucketBackfillOnStartup")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class BucketBackfillOnStartupTest {

    @Mock
    private lateinit var bucketBackfillService: BucketBackfillService

    @InjectMocks
    private lateinit var onStartup: BucketBackfillOnStartup

    @Test
    fun 기동하면_버킷이_없는_상품을_한_번_채운다() {
        whenever(bucketBackfillService.backfill()).thenReturn(BucketBackfillService.BackfillResult(3, 0))

        onStartup.backfill()

        verify(bucketBackfillService).backfill()
    }

    @Test
    fun 채우기가_실패해도_기동을_막지_않는다() {
        whenever(bucketBackfillService.backfill()).thenThrow(IllegalStateException("db down"))

        onStartup.backfill()

        verify(bucketBackfillService).backfill()
    }
}
