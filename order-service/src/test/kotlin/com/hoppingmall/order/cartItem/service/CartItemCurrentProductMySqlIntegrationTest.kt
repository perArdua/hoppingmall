package com.hoppingmall.order.cartItem.service

import com.hoppingmall.order.cartItem.dto.request.CartItemCreateRequest
import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.order.support.MySqlJpaTestSupport
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
import java.util.concurrent.ConcurrentHashMap

@DisplayName("장바구니의 지금 상품 값 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@Import(CartItemCommandServiceImpl::class, CartItemQueryServiceImpl::class)
class CartItemCurrentProductMySqlIntegrationTest : MySqlJpaTestSupport() {

    @Autowired
    private lateinit var cartItemCommandService: CartItemCommandServiceImpl

    @Autowired
    private lateinit var cartItemQueryService: CartItemQueryServiceImpl

    @MockitoBean
    private lateinit var productQueryPort: ProductQueryPort

    private val products = ConcurrentHashMap<Long, ProductInfo>()

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM cart_items")
        products.clear()
        whenever(productQueryPort.findProductById(any())).thenAnswer { invocation -> products[invocation.getArgument(0)] }
        whenever(productQueryPort.findProductsByIds(any())).thenAnswer { invocation ->
            invocation.getArgument<List<Long>>(0).mapNotNull { products[it] }
        }
    }

    @Test
    fun 같은_상품을_다시_담으면_장바구니_행의_가격과_이름이_지금_값으로_바뀐다() {
        products[PRODUCT_ID] = product(PRODUCT_ID, "1000.00", "담을 때 이름")
        add(PRODUCT_ID, 1)
        products[PRODUCT_ID] = product(PRODUCT_ID, "1500.00", "지금 이름")

        val readded = cartItemCommandService.addCartItem(BUYER_ID, CartItemCreateRequest(productId = PRODUCT_ID, quantity = 1))

        assertThat(readded.quantity).isEqualTo(2)
        assertThat(readded.productPrice).isEqualByComparingTo("1500.00")
        assertThat(readded.totalPrice).isEqualByComparingTo("3000.00")
        assertThat(readded.productName).isEqualTo("지금 이름")
        assertThat(jdbcTemplate.queryForObject("SELECT product_price FROM cart_items", BigDecimal::class.java))
            .isEqualByComparingTo("1500.00")
    }

    @Test
    fun 삭제된_상품을_담은_행도_목록에서_빠지지_않는다() {
        products[PRODUCT_ID] = product(PRODUCT_ID, "1000.00", "상품")
        products[OTHER_PRODUCT_ID] = product(OTHER_PRODUCT_ID, "2000.00", "다른 상품")
        val deletedId = add(PRODUCT_ID, 1)
        val keptId = add(OTHER_PRODUCT_ID, 1)
        products.remove(PRODUCT_ID)

        val listed = cartItemQueryService.getCartItems(BUYER_ID, PageRequest.of(0, 10, Sort.by("id"))).content.map { it.id }

        assertThat(listed).containsExactly(deletedId, keptId)
    }

    private fun add(productId: Long, quantity: Int): Long =
        cartItemCommandService.addCartItem(BUYER_ID, CartItemCreateRequest(productId = productId, quantity = quantity)).id

    private fun product(productId: Long, price: String, name: String) =
        ProductInfo(id = productId, name = name, price = BigDecimal(price), sellerId = 5L)

    companion object {
        private const val BUYER_ID = 10L
        private const val PRODUCT_ID = 100L
        private const val OTHER_PRODUCT_ID = 200L
    }
}
