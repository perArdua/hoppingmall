package com.hoppingmall.user.auth.domain.repository

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
@DisplayName("AccessTokenBlacklistRepository")
@DisplayNameGeneration(ReplaceUnderscores::class)
class AccessTokenBlacklistRepositoryTest {

    @Mock
    private lateinit var customStringRedisTemplate: RedisTemplate<String, String>

    @Mock
    private lateinit var valueOperations: ValueOperations<String, String>

    @InjectMocks
    private lateinit var repository: AccessTokenBlacklistRepository

    @Test
    fun 토큰을_블랙리스트에_추가한다() {
        whenever(customStringRedisTemplate.opsForValue()).thenReturn(valueOperations)

        repository.add("test-token", 3600000L)

        verify(valueOperations).set(eq("blacklist:test-token"), eq("blacklisted"), eq(3600000L), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun TTL이_0이하이면_블랙리스트에_추가하지_않는다() {
        repository.add("test-token", 0L)

        verify(customStringRedisTemplate, never()).opsForValue()
    }

    @Test
    fun 블랙리스트에_토큰이_존재하는지_확인한다() {
        whenever(customStringRedisTemplate.hasKey("blacklist:test-token")).thenReturn(true)

        val result = repository.exists("test-token")

        assertThat(result).isTrue()
    }

    @Test
    fun 블랙리스트에_토큰이_존재하지_않으면_false를_반환한다() {
        whenever(customStringRedisTemplate.hasKey("blacklist:test-token")).thenReturn(false)

        val result = repository.exists("test-token")

        assertThat(result).isFalse()
    }
}
