package com.hoppingmall.dlq.archival

import com.hoppingmall.dlq.domain.repository.DLQMessageRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.boot.test.context.runner.ApplicationContextRunner

@DisplayName("보관 없이 지우는 DLQ 퍼지 스케줄러의 등록 조건")
@DisplayNameGeneration(ReplaceUnderscores::class)
class DLQRetentionPurgeSchedulerConditionTest {

    private val contextRunner = ApplicationContextRunner()
        .withBean(DLQMessageRepository::class.java, { Mockito.mock(DLQMessageRepository::class.java) })
        .withUserConfiguration(DLQRetentionPurgeScheduler::class.java)

    @Test
    fun 보관_설정이_없으면_보관_없이_지우는_스케줄러가_뜬다() {
        contextRunner.run { context ->
            assertThat(context).hasSingleBean(DLQRetentionPurgeScheduler::class.java)
        }
    }

    @Test
    fun 보관이_꺼져_있으면_보관_없이_지우는_스케줄러가_뜬다() {
        contextRunner.withPropertyValues("dlq.archival.enabled=false").run { context ->
            assertThat(context).hasSingleBean(DLQRetentionPurgeScheduler::class.java)
        }
    }

    @Test
    fun 보관이_켜져_있으면_보관_없이_지우는_스케줄러는_뜨지_않는다() {
        contextRunner.withPropertyValues("dlq.archival.enabled=true").run { context ->
            assertThat(context).doesNotHaveBean(DLQRetentionPurgeScheduler::class.java)
        }
    }
}
