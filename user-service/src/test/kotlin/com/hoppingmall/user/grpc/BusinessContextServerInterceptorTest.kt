package com.hoppingmall.user.grpc

import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.slf4j.MDC

@ExtendWith(MockitoExtension::class)
@DisplayName("BusinessContextServerInterceptor")
@DisplayNameGeneration(ReplaceUnderscores::class)
class BusinessContextServerInterceptorTest {

    @Mock
    private lateinit var serverCall: ServerCall<Any, Any>

    @Mock
    private lateinit var next: ServerCallHandler<Any, Any>

    @Mock
    private lateinit var delegate: ServerCall.Listener<Any>

    @Test
    fun userId_헤더가_있으면_MDC에_설정한다() {
        val interceptor = BusinessContextServerInterceptor()
        val headers = Metadata()
        headers.put(BusinessContextServerInterceptor.USER_ID_KEY, "42")
        whenever(next.startCall(any(), any())).thenReturn(delegate)

        interceptor.interceptCall(serverCall, headers, next)

        assertThat(MDC.get("userId")).isEqualTo("42")
        MDC.remove("userId")
    }

    @Test
    fun userId_헤더가_없어도_정상_동작한다() {
        val interceptor = BusinessContextServerInterceptor()
        val headers = Metadata()
        whenever(next.startCall(any(), any())).thenReturn(delegate)

        interceptor.interceptCall(serverCall, headers, next)

        assertThat(MDC.get("userId")).isNull()
    }

    @Test
    fun onComplete_호출시_MDC를_정리한다() {
        val interceptor = BusinessContextServerInterceptor()
        val headers = Metadata()
        headers.put(BusinessContextServerInterceptor.USER_ID_KEY, "42")
        whenever(next.startCall(any(), any())).thenReturn(delegate)

        val listener = interceptor.interceptCall(serverCall, headers, next)
        listener.onComplete()

        assertThat(MDC.get("userId")).isNull()
    }

    @Test
    fun onCancel_호출시_MDC를_정리한다() {
        val interceptor = BusinessContextServerInterceptor()
        val headers = Metadata()
        headers.put(BusinessContextServerInterceptor.USER_ID_KEY, "42")
        whenever(next.startCall(any(), any())).thenReturn(delegate)

        val listener = interceptor.interceptCall(serverCall, headers, next)
        listener.onCancel()

        assertThat(MDC.get("userId")).isNull()
    }
}
