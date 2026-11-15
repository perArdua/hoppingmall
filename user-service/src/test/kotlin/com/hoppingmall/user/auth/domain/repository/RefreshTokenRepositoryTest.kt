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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations

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
