package com.hoppingmall.user.grpc

import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCallHandler
import io.grpc.Status
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
@DisplayName("InternalTokenServerInterceptor")
@DisplayNameGeneration(ReplaceUnderscores::class)
class InternalTokenServerInterceptorTest {

    @Mock
    private lateinit var serverCall: ServerCall<Any, Any>

    @Mock
    private lateinit var next: ServerCallHandler<Any, Any>

    @Mock
    private lateinit var listener: ServerCall.Listener<Any>

    @Test
    fun 유효한_토큰이면_요청을_통과시킨다() {
        val interceptor = InternalTokenServerInterceptor("valid-token")
        val headers = Metadata()
        headers.put(InternalTokenServerInterceptor.INTERNAL_TOKEN_KEY, "valid-token")
        whenever(next.startCall(serverCall, headers)).thenReturn(listener)

        interceptor.interceptCall(serverCall, headers, next)

        verify(next).startCall(serverCall, headers)
    }

    @Test
    fun 잘못된_토큰이면_요청을_거부한다() {
        val interceptor = InternalTokenServerInterceptor("valid-token")
        val headers = Metadata()
        headers.put(InternalTokenServerInterceptor.INTERNAL_TOKEN_KEY, "wrong-token")

        interceptor.interceptCall(serverCall, headers, next)

        verify(serverCall).close(argThat { code == Status.UNAUTHENTICATED.code }, any())
        verify(next, never()).startCall(any(), any())
    }

    @Test
    fun 토큰이_없으면_요청을_거부한다() {
        val interceptor = InternalTokenServerInterceptor("valid-token")
        val headers = Metadata()

        interceptor.interceptCall(serverCall, headers, next)

        verify(serverCall).close(argThat { code == Status.UNAUTHENTICATED.code }, any())
        verify(next, never()).startCall(any(), any())
    }
}
