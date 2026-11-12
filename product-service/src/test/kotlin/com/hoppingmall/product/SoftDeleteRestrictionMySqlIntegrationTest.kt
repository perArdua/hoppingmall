package com.hoppingmall.product

import com.hoppingmall.product.category.domain.repository.CategoryRepository
import com.hoppingmall.product.category.dto.request.CategoryCreateRequest
import com.hoppingmall.product.category.dto.request.CategoryUpdateRequest
import com.hoppingmall.product.category.exception.CategoryNotFoundException
import com.hoppingmall.product.category.service.CategoryCommandService
import com.hoppingmall.product.category.service.CategoryQueryService
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.domain.ProductStatistics
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.product.product.dto.request.ProductCreateRequest
import com.hoppingmall.product.product.dto.request.ProductSearchCondition
import com.hoppingmall.product.product.dto.request.ProductUpdateRequest
import com.hoppingmall.product.product.exception.ProductNotFoundException
import com.hoppingmall.product.product.service.ProductCommandService
import com.hoppingmall.product.product.service.ProductQueryService
import com.hoppingmall.product.product.service.ProductStatisticsCommandService
import com.hoppingmall.product.review.domain.Review
import com.hoppingmall.product.review.domain.repository.ReviewRepository
import com.hoppingmall.product.review.dto.request.ReviewCreateRequest
import com.hoppingmall.product.review.port.OrderItemInfo
import com.hoppingmall.product.review.port.OrderQueryPort
import com.hoppingmall.product.review.service.ReviewCommandService
import com.hoppingmall.product.review.service.ReviewCommandServiceImpl
import com.hoppingmall.product.review.service.ReviewQueryService
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import com.hoppingmall.product.wishlist.dto.request.WishlistCreateRequest
import com.hoppingmall.product.wishlist.service.WishlistCommandService
import com.hoppingmall.product.wishlist.service.WishlistQueryService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

@DisplayName("소프트 삭제한 상품·이미지·카테고리·리뷰·찜 조회 MySQL 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class SoftDeleteRestrictionMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var productCommandService: ProductCommandService

    @Autowired
    private lateinit var productQueryService: ProductQueryService

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var productStatisticsCommandService: ProductStatisticsCommandService

    @Autowired
    private lateinit var productStatisticsRepository: ProductStatisticsRepository

    @Autowired
    private lateinit var categoryCommandService: CategoryCommandService

    @Autowired
    private lateinit var categoryQueryService: CategoryQueryService

    @Autowired
    private lateinit var categoryRepository: CategoryRepository

    @Autowired
    private lateinit var reviewCommandService: ReviewCommandService

    @Autowired
    private lateinit var reviewQueryService: ReviewQueryService

    @Autowired
    private lateinit var reviewRepository: ReviewRepository

    @Autowired
    private lateinit var wishlistCommandService: WishlistCommandService

    @Autowired
    private lateinit var wishlistQueryService: WishlistQueryService

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val page = PageRequest.of(0, 20)

    @BeforeEach
    fun cleanCatalog() {
        CATALOG_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @AfterEach
    fun cleanCatalogAfter() {
        CATALOG_TABLES.forEach { jdbcTemplate.update("DELETE FROM $it") }
    }

    @Test
    fun 삭제한_상품은_단건_조회와_목록과_검색과_내부_조회에_나오지_않는다() {
        val categoryId = createCategory("소프트삭제-상품")
        val productId = createProduct(categoryId, listOf("a.jpg"))
        val keptId = createProduct(categoryId, listOf("b.jpg"))
        assertThat(productQueryService.getProductById(productId)).isNotNull()

        productCommandService.deleteProduct(productId)

        assertThat(productQueryService.getProductById(productId)).isNull()
        assertThat(productRepository.findNullableById(productId)).isNull()
        assertThat(productRepository.findAllById(listOf(productId, keptId)).map { it.id }).containsExactly(keptId)
        assertThat(productRepository.existsById(productId)).isFalse()
        assertThat(productQueryService.getProducts(page).content.map { it.id }).containsExactly(keptId)
        assertThat(productQueryService.getProductsByCategoryId(categoryId, page).content.map { it.id }).containsExactly(keptId)
        assertThat(productQueryService.getProductsBySellerId(SELLER_ID, page).content.map { it.id }).containsExactly(keptId)
        assertThat(
            productQueryService.searchProducts(ProductSearchCondition(categoryId = categoryId), page).content.map { it.id }
        ).containsExactly(keptId)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products WHERE id = ?", Long::class.java, productId)).isEqualTo(1L)
    }

    @Test
    fun 삭제한_상품은_다시_수정하거나_삭제할_수_없다() {
        val categoryId = createCategory("소프트삭제-수정")
        val productId = createProduct(categoryId, listOf("a.jpg"))
        productCommandService.deleteProduct(productId)

        val updateThrown = catchThrowable { productCommandService.updateProduct(productId, updateRequest(categoryId, listOf("c.jpg"))) }
        val deleteThrown = catchThrowable { productCommandService.deleteProduct(productId) }

        assertThat(updateThrown).isInstanceOf(ProductNotFoundException::class.java)
        assertThat(deleteThrown).isInstanceOf(ProductNotFoundException::class.java)
    }

    @Test
    fun 상품을_수정하면_이전_이미지가_상세와_목록에_남지_않는다() {
        val categoryId = createCategory("소프트삭제-이미지")
        val productId = createProduct(categoryId, listOf("old-1.jpg", "old-2.jpg"))

        productCommandService.updateProduct(productId, updateRequest(categoryId, listOf("new-1.jpg")))

        assertThat(productQueryService.getProductById(productId)!!.imageUrls).containsExactly("new-1.jpg")
        assertThat(productQueryService.getProducts(page).content.single().imageUrls).containsExactly("new-1.jpg")
    }

    @Test
    fun 삭제한_카테고리는_단건_조회와_하위_목록에_나오지_않고_그_카테고리로_상품을_만들_수_없다() {
        val parentId = createCategory("소프트삭제-부모")
        val childId = createCategory("소프트삭제-자식", parentId)

        categoryCommandService.deleteCategory(childId)

        assertThat(categoryQueryService.getCategory(childId)).isNull()
        assertThat(categoryQueryService.getSubCategories(parentId)).isEmpty()
        assertThat(catchThrowable { createProduct(childId, listOf("a.jpg")) }).isInstanceOf(CategoryNotFoundException::class.java)
    }

    @Test
    fun 하위_카테고리가_모두_삭제된_부모는_삭제할_수_있다() {
        val parentId = createCategory("소프트삭제-부모2")
        val childId = createCategory("소프트삭제-자식2", parentId)
        categoryCommandService.deleteCategory(childId)

        categoryCommandService.deleteCategory(parentId)

        assertThat(categoryQueryService.getRootCategories().map { it.id }).doesNotContain(parentId)
    }

    @Test
    fun 삭제한_카테고리_이름으로_새_카테고리를_만들_수_있다() {
        val deletedId = createCategory("소프트삭제-재사용")
        categoryCommandService.deleteCategory(deletedId)

        val recreated = categoryCommandService.createCategory(CategoryCreateRequest(name = "소프트삭제-재사용"))

        assertThat(recreated.id).isNotEqualTo(deletedId)
        assertThat(categoryQueryService.getRootCategories().map { it.name }).containsExactly("소프트삭제-재사용")
    }

    @Test
    fun 다른_카테고리의_이름을_삭제한_카테고리_이름으로_바꿀_수_있다() {
        val deletedId = createCategory("소프트삭제-옛이름")
        val renamedId = createCategory("소프트삭제-바꿀이름")
        categoryCommandService.deleteCategory(deletedId)

        categoryCommandService.updateCategory(renamedId, CategoryUpdateRequest(name = "소프트삭제-옛이름"))

        assertThat(categoryRepository.findNullableById(renamedId)!!.name).isEqualTo("소프트삭제-옛이름")
    }

    @Test
    fun 삭제한_리뷰는_단건_조회와_목록에_나오지_않고_평점과_개수에서_빠진다() {
        productStatisticsRepository.save(ProductStatistics.create(productId = PRODUCT_ID, productName = "리뷰 상품", sellerId = SELLER_ID, categoryId = 1L))
        val deletedId = reviewRepository.save(Review.create(BUYER_ID, ORDER_ITEM_ID, PRODUCT_ID, 1, "삭제할 리뷰 내용입니다")).id!!
        val keptId = reviewRepository.save(Review.create(OTHER_BUYER_ID, ORDER_ITEM_ID + 1, PRODUCT_ID, 5, "남는 리뷰 내용입니다")).id!!

        reviewCommandService.deleteReview(deletedId, BUYER_ID)

        assertThat(reviewRepository.findNullableById(deletedId)).isNull()
        assertThat(reviewQueryService.getReviewsByProductId(PRODUCT_ID, page).content.map { it.id }).containsExactly(keptId)
        assertThat(reviewQueryService.getMyReviews(BUYER_ID, page).content).isEmpty()
        val stats = productStatisticsRepository.findByProductId(PRODUCT_ID)!!
        assertThat(stats.reviewCount).isEqualTo(1L)
        assertThat(stats.averageRating).isEqualByComparingTo(BigDecimal("5.00"))
    }

    @Test
    fun 삭제한_리뷰의_주문_항목으로_리뷰를_다시_쓸_수_있다() {
        val orderQueryPort = mock<OrderQueryPort>()
        whenever(orderQueryPort.findOrderItemById(ORDER_ITEM_ID)).thenReturn(
            OrderItemInfo(ORDER_ITEM_ID, ORDER_ID, SELLER_ID, PRODUCT_ID, "리뷰 상품", BigDecimal("10000"), 1, BigDecimal("10000"))
        )
        whenever(orderQueryPort.isDelivered(any(), any())).thenReturn(true)
        val service = ReviewCommandServiceImpl(reviewRepository, orderQueryPort, productStatisticsRepository)
        val tx = TransactionTemplate(transactionManager)
        val request = ReviewCreateRequest(orderItemId = ORDER_ITEM_ID, rating = 2, content = "처음 쓴 리뷰 내용입니다")
        val firstId = tx.execute { service.createReview(BUYER_ID, request) }!!.id
        tx.executeWithoutResult { service.deleteReview(firstId, BUYER_ID) }

        val rewritten = tx.execute { service.createReview(BUYER_ID, request.copy(rating = 4, content = "다시 쓴 리뷰 내용입니다")) }!!

        assertThat(rewritten.id).isNotEqualTo(firstId)
        assertThat(reviewQueryService.getMyReviews(BUYER_ID, page).content.map { it.content }).containsExactly("다시 쓴 리뷰 내용입니다")
    }

    @Test
    fun 삭제한_찜은_목록과_찜_여부에_나오지_않는다() {
        val productId = createProduct(createCategory("소프트삭제-찜"), listOf("a.jpg"))
        val wishlistId = wishlistCommandService.addWishlist(BUYER_ID, WishlistCreateRequest(productId)).id

        wishlistCommandService.removeWishlist(BUYER_ID, wishlistId)

        assertThat(wishlistQueryService.getWishlists(BUYER_ID, page).content).isEmpty()
        assertThat(wishlistQueryService.isWishlisted(BUYER_ID, productId)).isFalse()
    }

    @Test
    fun 삭제한_찜을_다시_찜할_수_있다() {
        val productId = createProduct(createCategory("소프트삭제-다시찜"), listOf("a.jpg"))
        val firstId = wishlistCommandService.addWishlist(BUYER_ID, WishlistCreateRequest(productId)).id
        wishlistCommandService.removeWishlist(BUYER_ID, firstId)

        val again = wishlistCommandService.addWishlist(BUYER_ID, WishlistCreateRequest(productId))

        assertThat(again.id).isNotEqualTo(firstId)
        assertThat(wishlistQueryService.getWishlists(BUYER_ID, page).content.map { it.id }).containsExactly(again.id)
        assertThat(wishlistQueryService.isWishlisted(BUYER_ID, productId)).isTrue()
    }

    @Test
    fun 삭제한_상품은_찜할_수_없다() {
        val productId = createProduct(createCategory("소프트삭제-삭제상품찜"), listOf("a.jpg"))
        productCommandService.deleteProduct(productId)

        val thrown = catchThrowable { wishlistCommandService.addWishlist(BUYER_ID, WishlistCreateRequest(productId)) }

        assertThat(thrown).isInstanceOf(ProductNotFoundException::class.java)
    }

    @Test
    fun 찜한_상품이_삭제되면_찜_목록에_나오지_않는다() {
        val productId = createProduct(createCategory("소프트삭제-찜목록"), listOf("a.jpg"))
        wishlistCommandService.addWishlist(BUYER_ID, WishlistCreateRequest(productId))
        productCommandService.deleteProduct(productId)

        val wishlists = wishlistQueryService.getWishlists(BUYER_ID, page).content

        assertThat(wishlists).isEmpty()
    }

    @Test
    fun 삭제된_상품의_판매가_뒤늦게_들어와도_통계가_만들어진다() {
        val productId = createProduct(createCategory("소프트삭제-통계"), listOf("a.jpg"))
        productCommandService.deleteProduct(productId)

        productStatisticsCommandService.incrementSalesStats(productId, 2, BigDecimal("20000"))

        val stats = productStatisticsRepository.findByProductId(productId)!!
        assertThat(stats.productName).isEqualTo("소프트삭제 상품")
        assertThat(stats.totalSalesQuantity).isEqualTo(2L)
    }

    private fun createCategory(name: String, parentId: Long? = null): Long =
        categoryCommandService.createCategory(CategoryCreateRequest(name = name, parentCategoryId = parentId)).id

    private fun createProduct(categoryId: Long, imageUrls: List<String>): Long =
        productCommandService.createProduct(
            ProductCreateRequest(
                sellerId = SELLER_ID,
                categoryId = categoryId,
                name = "소프트삭제 상품",
                description = "소프트삭제 설명",
                price = BigDecimal("10000"),
                imageUrls = imageUrls,
                status = ProductStatus.AVAILABLE
            )
        ).id

    private fun updateRequest(categoryId: Long, imageUrls: List<String>) =
        ProductUpdateRequest(
            categoryId = categoryId,
            name = "소프트삭제 상품 수정",
            description = "소프트삭제 설명 수정",
            price = BigDecimal("12000"),
            imageUrls = imageUrls
        )

    companion object {
        private const val SELLER_ID = 7_501L
        private const val BUYER_ID = 7_502L
        private const val OTHER_BUYER_ID = 7_503L
        private const val PRODUCT_ID = 7_504L
        private const val ORDER_ID = 7_505L
        private const val ORDER_ITEM_ID = 7_506L
        private val CATALOG_TABLES = listOf("wishlists", "reviews", "product_statistics", "product_images", "products", "categories")
    }
}
