package com.hoppingmall.mall.global.auth.domain.repository

import com.hoppingmall.mall.global.auth.domain.RefreshToken
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.mockito.kotlin.*
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.concurrent.TimeUnit

@DisplayName("RefreshTokenRepository")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefreshTokenRepositoryTest {

    private val redisTemplate: StringRedisTemplate = mock()
    private val valueOps: ValueOperations<String, String> = mock()
    private lateinit var repository: RefreshTokenRepository

    @BeforeEach
    fun setUp() {
        whenever(redisTemplate.opsForValue()).thenReturn(valueOps)
        repository = RefreshTokenRepository(redisTemplate)
    }

    @Nested
    @DisplayName("save")
    inner class Save {
        @Test
        fun 리프레시_토큰을_저장하면_Redis에_key와_TTL이_함께_저장된다() {
            val token = RefreshToken(userId = 1L, token = "token-123", ttl = 3600000L)

            repository.save(token)

            verify(valueOps).set("refreshToken:1", "token-123", 3600000L, TimeUnit.MILLISECONDS)
        }
    }

    @Nested
    @DisplayName("findByUserId")
    inner class FindByUserId {
        @Test
        fun userId로_리프레시_토큰을_조회하면_Redis에서_값을_가져온다() {
            whenever(valueOps.get("refreshToken:1")).thenReturn("stored-token")

            val result = repository.findByUserId(1L)

            assert(result == "stored-token")
        }
    }

    @Nested
    @DisplayName("deleteByUserId")
    inner class DeleteByUserId {
        @Test
        fun userId로_리프레시_토큰을_삭제하면_Redis에서_해당_key가_삭제된다() {
            repository.deleteByUserId(1L)

            verify(redisTemplate).delete("refreshToken:1")
        }
    }
}
