package com.hoppingmall.mall.global.auth.domain.repository

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.mockito.kotlin.*
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.concurrent.TimeUnit

@DisplayName("AccessTokenBlacklistRepository")
@DisplayNameGeneration(ReplaceUnderscores::class)
class AccessTokenBlacklistRepositoryTest {

    private val redisTemplate: RedisTemplate<String, String> = mock()
    private val valueOps: ValueOperations<String, String> = mock()
    private lateinit var repository: AccessTokenBlacklistRepository

    @BeforeEach
    fun setUp() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        repository = AccessTokenBlacklistRepository(redisTemplate)
    }

    @Nested
    @DisplayName("add")
    inner class Add {
        @Test
        fun 토큰을_블랙리스트에_등록하면_Redis에_TTL과_함께_저장된다() {
            val token = "expired.jwt.token"

            repository.add(token, 1800000L)

            verify(valueOps).set("blacklist:$token", "blacklisted", 1800000L, TimeUnit.MILLISECONDS)
        }

        @Test
        fun 남은_TTL이_0_이하이면_저장하지_않는다() {
            repository.add("token", 0L)
            repository.add("token", -100L)

            verifyNoInteractions(valueOps)
        }
    }

    @Nested
    @DisplayName("exists")
    inner class Exists {
        @Test
        fun 블랙리스트에_등록된_토큰이면_true를_반환한다() {
            val token = "blacklisted.jwt.token"
            whenever(redisTemplate.hasKey("blacklist:$token")).thenReturn(true)

            assertTrue(repository.exists(token))
        }

        @Test
        fun 블랙리스트에_없는_토큰이면_false를_반환한다() {
            val token = "valid.jwt.token"
            whenever(redisTemplate.hasKey("blacklist:$token")).thenReturn(false)

            assertFalse(repository.exists(token))
        }
    }
}
