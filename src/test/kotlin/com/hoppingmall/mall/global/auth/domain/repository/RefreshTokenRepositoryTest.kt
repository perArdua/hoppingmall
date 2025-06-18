package com.hoppingmall.mall.global.auth.domain.repository

import com.hoppingmall.mall.global.auth.domain.RefreshToken
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.concurrent.TimeUnit

class RefreshTokenRepositoryTest {

    private val redisTemplate: StringRedisTemplate = mock()
    private val valueOps: ValueOperations<String, String> = mock()
    private lateinit var repository: RefreshTokenRepository

    @BeforeEach
    fun setUp() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        repository = RefreshTokenRepository(redisTemplate)
    }

    @Test
    fun `리프레시 토큰을 저장하면 Redis에 key와 TTL이 함께 저장된다`() {
        // given
        val token = RefreshToken(userId = 1L, token = "token-123", ttl = 3600000L)

        // when
        repository.save(token)

        // then
        verify(valueOps).set("refreshToken:1", "token-123", 3600000L, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `userId로 리프레시 토큰을 조회하면 Redis에서 값을 가져온다`() {
        // given
        whenever(valueOps.get("refreshToken:1")).thenReturn("stored-token")

        // when
        val result = repository.findByUserId(1L)

        // then
        assert(result == "stored-token")
    }

    @Test
    fun `userId로 리프레시 토큰을 삭제하면 Redis에서 해당 key가 삭제된다`() {
        // when
        repository.deleteByUserId(1L)

        // then
        verify(redisTemplate).delete("refreshToken:1")
    }
}
