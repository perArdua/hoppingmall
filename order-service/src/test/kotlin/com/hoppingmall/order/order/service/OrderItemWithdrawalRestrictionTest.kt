package com.hoppingmall.order.order.service

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.protobuf.ByteString
import com.google.protobuf.CodedOutputStream
import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.config.OrderMetrics
import com.hoppingmall.order.grpc.GrpcProductQueryAdapter
import com.hoppingmall.order.order.domain.Order
import com.hoppingmall.order.order.domain.OrderItem
import com.hoppingmall.order.order.domain.repository.OrderItemRepository
import com.hoppingmall.order.order.domain.repository.OrderRepository
import com.hoppingmall.order.order.dto.request.OrderCreateRequest
import com.hoppingmall.order.port.InventoryCommandPort
import com.hoppingmall.order.port.PaymentCommandPort
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.refund.domain.repository.RefundRepository
import com.hoppingmall.outbox.service.TransactionalEventPublisher
import com.hoppingmall.product.grpc.ProductListResponse
import com.hoppingmall.product.grpc.ProductQueryServiceGrpc
import com.hoppingmall.product.grpc.ProductResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("주문 항목의 청약철회 제한 찍기")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class OrderItemWithdrawalRestrictionTest {

    @Mock
    private lateinit var orderRepository: OrderRepository

    @Mock
    private lateinit var orderItemRepository: OrderItemRepository

    @Mock
    private lateinit var cartItemRepository: CartItemRepository

    @Mock
    private lateinit var productQueryPort: ProductQueryPort

    @Mock
    private lateinit var inventoryCommandPort: InventoryCommandPort

    @Mock
    private lateinit var transactionTemplate: TransactionTemplate

    @Mock
    private lateinit var stub: ProductQueryServiceGrpc.ProductQueryServiceBlockingStub

    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    @Test
    fun 상품_HTTP_응답의_제한과_사유를_읽는다() {
        val info = productFromHttp(RESTRICTED_PRODUCT_ID, restricted = true)

        val json = json(info)
        assertThat(json.path("withdrawal_restricted").asBoolean()).isTrue()
        assertThat(json.path("withdrawal_restriction_reason").asText()).isEqualTo("CUSTOM_MADE")
    }

    @Test
    fun 상품_gRPC_응답의_제한과_사유를_읽는다() {
        val restricted = grpcProduct(RESTRICTED_PRODUCT_ID, restricted = true)
        val free = grpcProduct(FREE_PRODUCT_ID, restricted = false)
        whenever(stub.findProductById(any())).thenReturn(restricted)
        whenever(stub.findProductsByIds(any())).thenReturn(
            ProductListResponse.newBuilder().addProducts(restricted).addProducts(free).build()
        )
        val adapter = GrpcProductQueryAdapter(stub)

        val single = json(adapter.findProductById(RESTRICTED_PRODUCT_ID)!!)
        val many = adapter.findProductsByIds(listOf(RESTRICTED_PRODUCT_ID, FREE_PRODUCT_ID)).map { json(it) }

        listOf(single, many[0]).forEach {
            assertThat(it.path("withdrawal_restricted").asBoolean()).isTrue()
            assertThat(it.path("withdrawal_restriction_reason").asText()).isEqualTo("CUSTOM_MADE")
        }
        assertThat(many[1].path("withdrawal_restricted").asBoolean()).isFalse()
        assertThat(many[1].path("withdrawal_restriction_reason").isNull).isTrue()
    }

    @Test
    fun 주문_생성은_상품의_제한과_사유를_항목에_찍고_응답에_내려_준다() {
        val service = OrderCommandServiceImpl(
            orderRepository, orderItemRepository, cartItemRepository, productQueryPort, inventoryCommandPort,
            mock<PaymentCommandPort>(), mock<TransactionalEventPublisher>(), mock<OrderMetrics>(), transactionTemplate,
            mock<RefundRepository>()
        )
        whenever(cartItemRepository.findAllById(listOf(1L, 2L))).thenReturn(
            listOf(cartItem(1L, RESTRICTED_PRODUCT_ID), cartItem(2L, FREE_PRODUCT_ID))
        )
        whenever(productQueryPort.findProductsByIds(listOf(RESTRICTED_PRODUCT_ID, FREE_PRODUCT_ID))).thenReturn(
            listOf(productFromHttp(RESTRICTED_PRODUCT_ID, restricted = true), productFromHttp(FREE_PRODUCT_ID, restricted = false))
        )
        whenever(inventoryCommandPort.batchReserveStock(any()))
            .thenReturn(mapOf(RESTRICTED_PRODUCT_ID to "rsv-1", FREE_PRODUCT_ID to "rsv-2"))
        whenever(transactionTemplate.execute(any<TransactionCallback<Any>>())).thenAnswer {
            it.getArgument<TransactionCallback<Any>>(0).doInTransaction(mock())
        }
        whenever(orderRepository.save(any<Order>())).thenAnswer {
            it.getArgument<Order>(0).also { order ->
                ReflectionTestUtils.setField(order, "id", ORDER_ID)
                order.updatedAt = LocalDateTime.now()
            }
        }
        whenever(orderItemRepository.saveAll(any<List<OrderItem>>())).thenAnswer {
            it.getArgument<List<OrderItem>>(0).onEachIndexed { index, item -> ReflectionTestUtils.setField(item, "id", 10L + index) }
        }

        val response = json(service.createOrder(BUYER_ID, OrderCreateRequest(cartItemIds = listOf(1L, 2L))))

        val saved = argumentCaptor<List<OrderItem>>()
        verify(orderItemRepository).saveAll(saved.capture())
        val byProduct = saved.firstValue.associateBy { it.productId }
        assertThat(ReflectionTestUtils.getField(byProduct.getValue(RESTRICTED_PRODUCT_ID), "withdrawalRestricted")).isEqualTo(true)
        assertThat(ReflectionTestUtils.getField(byProduct.getValue(RESTRICTED_PRODUCT_ID), "withdrawalRestrictionReason")).isEqualTo("CUSTOM_MADE")
        assertThat(ReflectionTestUtils.getField(byProduct.getValue(FREE_PRODUCT_ID), "withdrawalRestricted")).isEqualTo(false)
        assertThat(ReflectionTestUtils.getField(byProduct.getValue(FREE_PRODUCT_ID), "withdrawalRestrictionReason")).isNull()

        val items = response.path("items").associateBy { it.path("product_id").asLong() }
        assertThat(items.getValue(RESTRICTED_PRODUCT_ID).path("withdrawal_restricted").asBoolean()).isTrue()
        assertThat(items.getValue(RESTRICTED_PRODUCT_ID).path("withdrawal_restriction_reason").asText()).isEqualTo("CUSTOM_MADE")
        assertThat(items.getValue(FREE_PRODUCT_ID).path("withdrawal_restricted").isBoolean).isTrue()
        assertThat(items.getValue(FREE_PRODUCT_ID).path("withdrawal_restricted").asBoolean()).isFalse()
        assertThat(items.getValue(FREE_PRODUCT_ID).path("withdrawal_restriction_reason").isNull).isTrue()
    }

    private fun productFromHttp(id: Long, restricted: Boolean): ProductInfo {
        val restriction = if (restricted) ""","withdrawal_restricted":true,"withdrawal_restriction_reason":"CUSTOM_MADE""""
        else ""","withdrawal_restricted":false,"withdrawal_restriction_reason":null"""
        return objectMapper.readValue(
            """{"id":$id,"name":"상품$id","price":20000,"seller_id":$SELLER_ID$restriction}""",
            ProductInfo::class.java
        )
    }

    private fun grpcProduct(id: Long, restricted: Boolean): ProductResponse {
        val bytes = ByteArrayOutputStream()
        val out = CodedOutputStream.newInstance(bytes)
        out.writeInt64(1, id)
        out.writeString(2, "상품$id")
        out.writeString(3, "20000")
        out.writeInt64(4, SELLER_ID)
        if (restricted) {
            out.writeBool(8, true)
            out.writeString(9, "CUSTOM_MADE")
        }
        out.flush()
        return ProductResponse.parseFrom(ByteString.copyFrom(bytes.toByteArray()))
    }

    private fun cartItem(id: Long, productId: Long): CartItem {
        val cartItem = CartItem.create(
            buyerId = BUYER_ID, productId = productId, productName = "상품$productId",
            productPrice = BigDecimal("20000"), productImageUrl = null, quantity = 1
        )
        ReflectionTestUtils.setField(cartItem, "id", id)
        return cartItem
    }

    private fun json(value: Any): JsonNode = objectMapper.valueToTree(value)

    companion object {
        private const val ORDER_ID = 1L
        private const val BUYER_ID = 10L
        private const val SELLER_ID = 20L
        private const val RESTRICTED_PRODUCT_ID = 100L
        private const val FREE_PRODUCT_ID = 200L
    }
}
