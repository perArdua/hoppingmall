package com.hoppingmall.user.auth.domain.repository

import com.hoppingmall.user.auth.domain.RefreshToken
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.concurrent.TimeUnit

@ExtendWith(MockitoExtension::class)
@DisplayName("RefreshTokenRepository")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefreshTokenRepositoryTest {

    @Mock
    private lateinit var stringRedisTemplate: RedisTemplate<String, String>

    @Mock
    private lateinit var valueOperations: ValueOperations<String, String>

    @InjectMocks
    private lateinit var repository: RefreshTokenRepository

    @Test
    fun 리프레시_토큰을_저장한다() {
        val refreshToken = RefreshToken(userId = 1L, token = "test-token", ttl = 86400000L)
        whenever(stringRedisTemplate.opsForValue()).thenReturn(valueOperations)

        repository.save(refreshToken)

        verify(valueOperations).set(eq("refreshToken:1"), eq("test-token"), eq(86400000L), eq(TimeUnit.MILLISECONDS))
    }

    @Test
    fun 사용자_ID로_리프레시_토큰을_조회한다() {
        whenever(stringRedisTemplate.opsForValue()).thenReturn(valueOperations)
        whenever(valueOperations.get("refreshToken:1")).thenReturn("stored-token")

        val result = repository.findByUserId(1L)

        assertThat(result).isEqualTo("stored-token")
    }

    @Test
    fun 존재하지_않는_사용자의_리프레시_토큰_조회시_null을_반환한다() {
        whenever(stringRedisTemplate.opsForValue()).thenReturn(valueOperations)
        whenever(valueOperations.get("refreshToken:999")).thenReturn(null)

        val result = repository.findByUserId(999L)

        assertThat(result).isNull()
    }

    @Test
    fun 사용자_ID로_리프레시_토큰을_삭제한다() {
        repository.deleteByUserId(1L)

        verify(stringRedisTemplate).delete("refreshToken:1")
    }
}
