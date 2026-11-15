package com.hoppingmall.idempotency

import com.hoppingmall.common.ApiResponse
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.aop.framework.ProxyFactory
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.http.ResponseEntity

@DisplayName("@Idempotent 메서드의 반환형 기동 검사")
@DisplayNameGeneration(ReplaceUnderscores::class)
class IdempotentReturnTypeVerifierTest {

    private val verifier = IdempotentReturnTypeVerifier()

    @Test
    fun ResponseEntity가_아닌_값을_돌려주는_Idempotent_메서드가_있으면_빈을_만들지_않는다() {
        assertThatThrownBy { verifier.postProcessBeforeInitialization(ApiResponseEntry(), "apiResponseEntry") }
            .hasMessageContaining("ApiResponseEntry.cancel")
    }

    @Test
    fun ResponseEntity가_아닌_Idempotent_메서드가_있으면_애플리케이션이_뜨지_않는다() {
        val context = AnnotationConfigApplicationContext()
        context.register(IdempotentReturnTypeVerifier::class.java, ApiResponseEntry::class.java)

        assertThatThrownBy { context.refresh() }
            .hasStackTraceContaining("ApiResponseEntry.cancel")
    }

    @Test
    fun 프록시로_감싼_빈도_원래_클래스의_메서드를_검사한다() {
        val proxy = ProxyFactory(ApiResponseEntry()).apply { isProxyTargetClass = true }.proxy

        assertThatThrownBy { verifier.postProcessBeforeInitialization(proxy, "apiResponseEntry") }
            .hasMessageContaining("ApiResponseEntry.cancel")
    }

    @Test
    fun ResponseEntity를_돌려주는_Idempotent_메서드는_그대로_통과한다() {
        val bean = ResponseEntityEntry()

        assertThat(verifier.postProcessBeforeInitialization(bean, "responseEntityEntry")).isSameAs(bean)
    }

    @Test
    fun Idempotent가_없는_메서드는_반환형을_보지_않는다() {
        val bean = PlainEntry()

        assertThat(verifier.postProcessBeforeInitialization(bean, "plainEntry")).isSameAs(bean)
    }

    open class ApiResponseEntry {
        @Idempotent
        open fun cancel(): ApiResponse<String> = ApiResponse.success("cancelled")
    }

    open class ResponseEntityEntry {
        @Idempotent
        open fun cancel(): ResponseEntity<ApiResponse<String>> = ResponseEntity.ok(ApiResponse.success("cancelled"))
    }

    open class PlainEntry {
        open fun get(): ApiResponse<String> = ApiResponse.success("ok")
    }
}
