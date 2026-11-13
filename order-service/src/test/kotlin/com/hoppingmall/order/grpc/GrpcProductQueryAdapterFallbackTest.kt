package com.hoppingmall.order.grpc

import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryUnavailableException
import com.hoppingmall.product.grpc.ProductQueryServiceGrpc
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.lang.reflect.InvocationTargetException

@DisplayName("gRPC 상품 조회 어댑터의 연결 실패 처리")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class GrpcProductQueryAdapterFallbackTest {

    @Mock
    private lateinit var stub: ProductQueryServiceGrpc.ProductQueryServiceBlockingStub

    private lateinit var adapter: GrpcProductQueryAdapter

    @BeforeEach
    fun setUp() {
        adapter = GrpcProductQueryAdapter(stub)
    }

    @Test
    fun 단건_조회가_UNAVAILABLE로_실패하면_상품_없음이_아니라_조회_불가_예외를_던진다() {
        val failure = StatusRuntimeException(Status.UNAVAILABLE)

        assertThatThrownBy { findProductByIdFallback(1L, failure) }
            .isInstanceOf(ProductQueryUnavailableException::class.java)
            .hasCause(failure)
    }

    @Test
    fun 단건_조회가_시간_초과로_실패하면_조회_불가_예외를_던진다() {
        val failure = StatusRuntimeException(Status.DEADLINE_EXCEEDED)

        assertThatThrownBy { findProductByIdFallback(1L, failure) }
            .isInstanceOf(ProductQueryUnavailableException::class.java)
    }

    @Test
    fun 서킷이_열려_단건_조회를_부르지_못하면_조회_불가_예외를_던진다() {
        val failure = CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("product-query"))

        assertThatThrownBy { findProductByIdFallback(1L, failure) }
            .isInstanceOf(ProductQueryUnavailableException::class.java)
    }

    @Test
    fun 단건_조회가_NOT_FOUND면_상품_없음으로_null을_돌려준다() {
        val result = findProductByIdFallback(1L, StatusRuntimeException(Status.NOT_FOUND))

        assertThat(result).isNull()
    }

    @Test
    fun 목록_조회가_UNAVAILABLE로_실패하면_빈_목록이_아니라_조회_불가_예외를_던진다() {
        val failure = StatusRuntimeException(Status.UNAVAILABLE)

        assertThatThrownBy { findProductsByIdsFallback(listOf(1L, 2L), failure) }
            .isInstanceOf(ProductQueryUnavailableException::class.java)
            .hasCause(failure)
    }

    @Test
    fun 서킷이_열려_목록_조회를_부르지_못하면_조회_불가_예외를_던진다() {
        val failure = CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("product-query"))

        assertThatThrownBy { findProductsByIdsFallback(listOf(1L), failure) }
            .isInstanceOf(ProductQueryUnavailableException::class.java)
    }

    private fun findProductByIdFallback(productId: Long, e: Exception): ProductInfo? =
        invoke("findProductByIdFallback", arrayOf(Long::class.java, Exception::class.java), productId, e) as ProductInfo?

    @Suppress("UNCHECKED_CAST")
    private fun findProductsByIdsFallback(productIds: List<Long>, e: Exception): List<ProductInfo> =
        invoke("findProductsByIdsFallback", arrayOf(List::class.java, Exception::class.java), productIds, e) as List<ProductInfo>

    private fun invoke(name: String, parameterTypes: Array<Class<*>>, vararg args: Any): Any? {
        val method = GrpcProductQueryAdapter::class.java.getDeclaredMethod(name, *parameterTypes)
        method.isAccessible = true
        return try {
            method.invoke(adapter, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }
}
