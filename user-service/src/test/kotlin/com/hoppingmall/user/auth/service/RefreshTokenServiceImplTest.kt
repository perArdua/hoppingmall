package com.hoppingmall.user.auth.service

import com.hoppingmall.user.auth.domain.repository.RefreshTokenRepository
import com.hoppingmall.user.auth.exception.RefreshTokenMismatchException
import com.hoppingmall.user.auth.exception.RefreshTokenNotFoundException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
@DisplayName("RefreshTokenServiceImpl 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RefreshTokenServiceImplTest {

    @Mock
    private lateinit var redisTemplate: RedisTemplate<String, String>

    @Mock
    private lateinit var valueOperations: ValueOperations<String, String>

    private lateinit var refreshTokenService: RefreshTokenServiceImpl

    @BeforeEach
    fun setUp() {
        lenient().whenever(redisTemplate.opsForValue()).thenReturn(valueOperations)

        refreshTokenService = RefreshTokenServiceImpl(
            RefreshTokenRepository(redisTemplate)
        )
    }

    @Test
    fun rotateRefreshToken은_기존_토큰을_삭제하고_새_토큰을_저장한다() {
        val result = refreshTokenService.rotateRefreshToken(userId = 1L, newToken = "new-token", ttl = 3600L)

        assertEquals(1L, result.userId)
        assertEquals("new-token", result.token)
        assertEquals(3600L, result.ttl)
        verify(redisTemplate).delete("refreshToken:1")
        verify(valueOperations).set("refreshToken:1", "new-token", 3600L, TimeUnit.MILLISECONDS)
    }

    @Test
    fun validate는_저장된_토큰과_일치하면_성공한다() {
        whenever(valueOperations.get("refreshToken:2")).thenReturn("refresh-token")

        refreshTokenService.validate(2L, "refresh-token")

        verify(valueOperations).get("refreshToken:2")
    }

    @Test
    fun validate는_저장된_토큰이_없으면_예외가_발생한다() {
        whenever(valueOperations.get("refreshToken:3")).thenReturn(null)

        assertThrows<RefreshTokenNotFoundException> {
            refreshTokenService.validate(3L, "missing")
        }
    }

    @Test
    fun validate는_저장된_토큰과_다르면_예외가_발생한다() {
        whenever(valueOperations.get("refreshToken:4")).thenReturn("stored-token")

        assertThrows<RefreshTokenMismatchException> {
            refreshTokenService.validate(4L, "different-token")
        }
    }

    @Test
    fun delete는_userId_기준으로_refreshToken을_삭제한다() {
        refreshTokenService.delete(5L)

        verify(redisTemplate).delete("refreshToken:5")
    }
}
