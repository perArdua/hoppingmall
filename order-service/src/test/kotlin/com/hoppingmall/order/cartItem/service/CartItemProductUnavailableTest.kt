package com.hoppingmall.order.cartItem.service

import com.hoppingmall.common.BusinessException
import com.hoppingmall.common.GlobalExceptionHandler
import com.hoppingmall.order.cartItem.domain.CartItem
import com.hoppingmall.order.cartItem.domain.repository.CartItemRepository
import com.hoppingmall.order.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.port.ProductQueryUnavailableException
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

@DisplayName("상품 서비스 연결 실패 시 장바구니 담기 응답")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class CartItemProductUnavailableTest {

    @Mock
    private lateinit var cartItemRepository: CartItemRepository

    @Mock
    private lateinit var productQueryPort: ProductQueryPort

    @InjectMocks
    private lateinit var service: CartItemCommandServiceImpl

    private val handler = GlobalExceptionHandler()

    @Test
    fun 상품_조회가_연결_실패면_상품_없음_404가_아니라_503으로_응답하고_장바구니에_담지_않는다() {
        whenever(productQueryPort.findProductById(PRODUCT_ID))
            .thenThrow(ProductQueryUnavailableException("상품 조회 실패", StatusRuntimeException(Status.UNAVAILABLE)))

        assertThatThrownBy { service.addCartItem(BUYER_ID, CartItemCreateRequest(productId = PRODUCT_ID, quantity = 1)) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                val response = handler.handleBusinessException(it)
                assertThat(response.statusCode).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                assertThat(response.body?.code).isNotEqualTo("C003")
            }

        verify(cartItemRepository, never()).save(any<CartItem>())
    }

    @Test
    fun 상품이_정말_없으면_여전히_404_C003으로_응답한다() {
        whenever(productQueryPort.findProductById(PRODUCT_ID)).thenReturn(null)

        assertThatThrownBy { service.addCartItem(BUYER_ID, CartItemCreateRequest(productId = PRODUCT_ID, quantity = 1)) }
            .isInstanceOfSatisfying(BusinessException::class.java) {
                val response = handler.handleBusinessException(it)
                assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
                assertThat(response.body?.code).isEqualTo("C003")
            }
    }

    companion object {
        private const val BUYER_ID = 1L
        private const val PRODUCT_ID = 100L
    }
}
