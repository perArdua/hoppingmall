package com.hoppingmall.payment.grpc

import com.hoppingmall.order.grpc.OrderItemListResponse
import com.hoppingmall.order.grpc.OrderItemResponse
import com.hoppingmall.order.grpc.OrderQueryServiceGrpc
import com.hoppingmall.payment.port.exception.OrderItemQueryFailedException
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.junit.jupiter.MockitoExtension

@DisplayName("GrpcOrderQueryAdapter")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class GrpcOrderQueryAdapterTest {

    private val stub: OrderQueryServiceGrpc.OrderQueryServiceBlockingStub = mock()
    private val adapter = GrpcOrderQueryAdapter(stub)

    @Test
    fun 주문_상품_조회_성공_시_리스트를_반환한다() {
        val response = OrderItemListResponse.newBuilder()
            .addItems(
                OrderItemResponse.newBuilder()
                    .setId(1L)
                    .setProductId(10L)
                    .setQuantity(2)
                    .setTotalPrice("20000")
                    .build()
            )
            .build()
        whenever(stub.findOrderItemsByOrderId(any())).thenReturn(response)

        val result = adapter.findOrderItemsByOrderId(1L)

        assertThat(result).hasSize(1)
        assertThat(result[0].productId).isEqualTo(10L)
        assertThat(result[0].quantity).isEqualTo(2)
    }

    @Test
    fun gRPC_호출_실패_시_OrderItemQueryFailedException을_던진다() {
        whenever(stub.findOrderItemsByOrderId(any()))
            .thenThrow(StatusRuntimeException(Status.UNAVAILABLE))

        assertThatThrownBy { adapter.findOrderItemsByOrderId(1L) }
            .isInstanceOf(OrderItemQueryFailedException::class.java)
            .hasMessageContaining("orderId=1")
    }
}
