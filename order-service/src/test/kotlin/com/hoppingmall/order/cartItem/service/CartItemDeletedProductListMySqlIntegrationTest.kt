package com.hoppingmall.order.cartItem.service

import com.hoppingmall.order.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.port.ProductQueryUnavailableException
import com.hoppingmall.order.support.MySqlJpaTestSupport
import io.grpc.Status
import io.grpc.StatusRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal

@DisplayName("삭제된 상품을 담은 장바구니 행의 목록 조회 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(CartItemCommandServiceImpl::class, CartItemQueryServiceImpl::class)
class CartItemDeletedProductListMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var cartItemCommandService: CartItemCommandServiceImpl

    @Autowired
    private lateinit var cartItemQueryService: CartItemQueryServiceImpl

    @MockitoBean
    private lateinit var productQueryPort: ProductQueryPort

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM cart_items")
        whenever(productQueryPort.findProductById(any())).thenAnswer { invocation -> product(invocation.getArgument(0)) }
    }

    @Test
    fun 삭제된_상품을_담은_장바구니_행은_목록에_나오지_않는다() {
        val deletedId = add(DELETED_PRODUCT_ID)
        val keptId = add(KEPT_PRODUCT_ID)
        productsAliveAre(KEPT_PRODUCT_ID)

        val listed = cartItemQueryService.getCartItems(BUYER_ID, PageRequest.of(0, 10)).content.map { it.id }

        assertThat(listed).containsExactly(keptId)
        assertThat(listed).doesNotContain(deletedId)
    }

    @Test
    fun 삭제된_상품을_빼도_다음_쪽이_있는지는_장바구니_행_기준으로_알려준다() {
        add(DELETED_PRODUCT_ID)
        val keptId = add(KEPT_PRODUCT_ID)
        add(OTHER_PRODUCT_ID)
        productsAliveAre(KEPT_PRODUCT_ID, OTHER_PRODUCT_ID)

        val page = cartItemQueryService.getCartItems(BUYER_ID, PageRequest.of(0, 2, Sort.by("id")))

        assertThat(page.content.map { it.id }).containsExactly(keptId)
        assertThat(page.hasNext()).isTrue()
    }

    @Test
    fun 상품_조회가_연결_실패면_장바구니_행을_빼지_않고_모두_보여준다() {
        val deletedId = add(DELETED_PRODUCT_ID)
        val keptId = add(KEPT_PRODUCT_ID)
        whenever(productQueryPort.findProductsByIds(any()))
            .thenThrow(ProductQueryUnavailableException("상품 목록 조회 실패", StatusRuntimeException(Status.UNAVAILABLE)))

        val listed = cartItemQueryService.getCartItems(BUYER_ID, PageRequest.of(0, 10)).content.map { it.id }

        assertThat(listed).containsExactlyInAnyOrder(deletedId, keptId)
    }

    private fun productsAliveAre(vararg productIds: Long) {
        whenever(productQueryPort.findProductsByIds(any())).thenAnswer { invocation ->
            invocation.getArgument<List<Long>>(0).filter { it in productIds }.map { product(it) }
        }
    }

    private fun add(productId: Long): Long =
        cartItemCommandService.addCartItem(BUYER_ID, CartItemCreateRequest(productId = productId, quantity = 1)).id

    private fun product(productId: Long) =
        ProductInfo(id = productId, name = "상품-$productId", price = BigDecimal("1000"), sellerId = 5L)

    companion object {
        private const val BUYER_ID = 10L
        private const val DELETED_PRODUCT_ID = 100L
        private const val KEPT_PRODUCT_ID = 200L
        private const val OTHER_PRODUCT_ID = 300L
    }
}
