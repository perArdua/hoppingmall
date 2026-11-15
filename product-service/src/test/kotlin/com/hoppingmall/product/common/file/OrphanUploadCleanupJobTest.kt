package com.hoppingmall.product.common.file

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify

@DisplayName("OrphanUploadCleanupJob")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrphanUploadCleanupJobTest {

    @Mock
    private lateinit var orphanUploadCleaner: OrphanUploadCleaner

    @InjectMocks
    private lateinit var job: OrphanUploadCleanupJob

    @Test
    fun 주기마다_지금_시각으로_정리한다() {
        job.run()

        verify(orphanUploadCleaner).cleanUp(any())
    }
}
