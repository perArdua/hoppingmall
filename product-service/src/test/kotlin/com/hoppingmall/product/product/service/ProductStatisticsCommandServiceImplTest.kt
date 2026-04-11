package com.hoppingmall.product.product.service

import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.ProductDailyStatistics
import com.hoppingmall.product.product.domain.ProductHourlyStatistics
import com.hoppingmall.product.product.domain.ProductStatistics
import com.hoppingmall.product.product.domain.repository.ProductDailyStatisticsRepository
import com.hoppingmall.product.product.domain.repository.ProductHourlyStatisticsRepository
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.domain.repository.ProductStatisticsRepository
import com.hoppingmall.product.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.function.Consumer

@DisplayName("ProductStatisticsCommandServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ProductStatisticsCommandServiceImplTest {

    @Mock
    private lateinit var productStatisticsRepository: ProductStatisticsRepository

    @Mock
    private lateinit var productDailyStatisticsRepository: ProductDailyStatisticsRepository

    @Mock
    private lateinit var productHourlyStatisticsRepository: ProductHourlyStatisticsRepository

    @Mock
    private lateinit var productRepository: ProductRepository

    @Mock
    private lateinit var transactionTemplate: TransactionTemplate

    @InjectMocks
    private lateinit var service: ProductStatisticsCommandServiceImpl

    @BeforeEach
    fun setUp() {
        org.mockito.Mockito.lenient().doAnswer { invocation ->
            val callback = invocation.getArgument<Consumer<TransactionStatus>>(0)
            callback.accept(org.mockito.kotlin.mock())
            null
        }.`when`(transactionTemplate).executeWithoutResult(any())
    }

    private fun createStats(productId: Long = 1L) = ProductStatistics.create(
        productId = productId, productName = "테스트", sellerId = 1L, categoryId = 1L
    ).withId(1L)

    @Test
    fun 기존_통계에_판매를_증가시킨다() {
        val stats = createStats()

        whenever(productStatisticsRepository.findByProductId(1L)).thenReturn(stats)
        whenever(productStatisticsRepository.save(any<ProductStatistics>())).thenReturn(stats)

        service.incrementSalesStats(1L, 5, BigDecimal("50000"))

        assertThat(stats.totalSalesQuantity).isEqualTo(5)
    }

    @Test
    fun 통계가_없으면_새로_생성하고_판매를_증가시킨다() {
        val product = Product.create(
            sellerId = 1L, categoryId = 1L, name = "테스트",
            description = "설명", price = BigDecimal("10000"), status = ProductStatus.AVAILABLE
        ).withId(1L)
        val stats = createStats()

        whenever(productStatisticsRepository.findByProductId(1L)).thenReturn(null)
        whenever(productRepository.findById(1L)).thenReturn(Optional.of(product))
        whenever(productStatisticsRepository.save(any<ProductStatistics>())).thenReturn(stats)

        service.incrementSalesStats(1L, 5, BigDecimal("50000"))

        verify(productStatisticsRepository, org.mockito.kotlin.times(2)).save(any<ProductStatistics>())
    }

    @Test
    fun 존재하지_않는_상품의_통계_생성_시_예외를_발생시킨다() {
        whenever(productStatisticsRepository.findByProductId(999L)).thenReturn(null)
        whenever(productRepository.findById(999L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.incrementSalesStats(999L, 5, BigDecimal("50000")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun 판매_통계를_감소시킨다() {
        val stats = createStats()
        stats.incrementSales(10, BigDecimal("100000"))

        whenever(productStatisticsRepository.findByProductId(1L)).thenReturn(stats)
        whenever(productStatisticsRepository.save(any<ProductStatistics>())).thenReturn(stats)

        service.decrementSalesStats(1L, 5, BigDecimal("50000"))

        assertThat(stats.totalSalesQuantity).isEqualTo(5)
    }

    @Test
    fun 통계가_없는_상품의_판매_감소는_무시한다() {
        whenever(productStatisticsRepository.findByProductId(999L)).thenReturn(null)

        service.decrementSalesStats(999L, 5, BigDecimal("50000"))

        verify(productStatisticsRepository).findByProductId(999L)
    }

    @Test
    fun 환불_통계를_증가시킨다() {
        val stats = createStats()

        whenever(productStatisticsRepository.findByProductId(1L)).thenReturn(stats)
        whenever(productStatisticsRepository.save(any<ProductStatistics>())).thenReturn(stats)

        service.incrementRefundStats(1L, 3, BigDecimal("30000"))

        assertThat(stats.totalRefundQuantity).isEqualTo(3)
    }

    @Test
    fun 통계가_없으면_일별_스냅샷을_건너뛴다() {
        whenever(productStatisticsRepository.findAll(any<Pageable>()))
            .thenReturn(PageImpl(emptyList()))

        service.flushDailySnapshot()

        verify(productDailyStatisticsRepository, org.mockito.kotlin.never())
            .saveAll(any<List<ProductDailyStatistics>>())
    }

    @Test
    fun 일별_스냅샷을_저장한다() {
        val stats = createStats()
        stats.incrementSales(10, BigDecimal("100000"))
        val daily = ProductDailyStatistics.create(productId = 1L, statisticsDate = LocalDate.now())

        whenever(productStatisticsRepository.findAll(any<Pageable>()))
            .thenReturn(PageImpl(listOf(stats)))
        whenever(productDailyStatisticsRepository.findByStatisticsDateAndProductIdIn(any(), any()))
            .thenReturn(listOf(daily))
        whenever(productDailyStatisticsRepository.sumSalesAmountByProductIdsAndDateRange(any(), any(), any()))
            .thenReturn(listOf(arrayOf<Any>(1L, BigDecimal("100000"))))
        whenever(productDailyStatisticsRepository.saveAll(any<List<ProductDailyStatistics>>()))
            .thenAnswer { it.arguments[0] }
        whenever(productStatisticsRepository.saveAll(any<List<ProductStatistics>>()))
            .thenAnswer { it.arguments[0] }

        service.flushDailySnapshot()

        assertThat(stats.todaySalesQuantity).isEqualTo(0)
    }

    @Test
    fun 일별_스냅샷_기존_레코드_없으면_새로_생성한다() {
        val stats = createStats()
        stats.incrementSales(10, BigDecimal("100000"))

        whenever(productStatisticsRepository.findAll(any<Pageable>()))
            .thenReturn(PageImpl(listOf(stats)))
        whenever(productDailyStatisticsRepository.findByStatisticsDateAndProductIdIn(any(), any()))
            .thenReturn(emptyList())
        whenever(productDailyStatisticsRepository.sumSalesAmountByProductIdsAndDateRange(any(), any(), any()))
            .thenReturn(emptyList())
        whenever(productDailyStatisticsRepository.saveAll(any<List<ProductDailyStatistics>>()))
            .thenAnswer { it.arguments[0] }
        whenever(productStatisticsRepository.saveAll(any<List<ProductStatistics>>()))
            .thenAnswer { it.arguments[0] }

        service.flushDailySnapshot()

        verify(productDailyStatisticsRepository).saveAll(any<List<ProductDailyStatistics>>())
    }

    @Test
    fun 시간별_스냅샷을_저장한다() {
        val stats = createStats()
        stats.incrementSales(10, BigDecimal("100000"))

        whenever(productStatisticsRepository.findAllActive()).thenReturn(listOf(stats))
        whenever(productHourlyStatisticsRepository.findByStatisticsDateAndHourAndProductIdIn(any(), any<Int>(), any()))
            .thenReturn(emptyList())
        whenever(productHourlyStatisticsRepository.saveAll(any<List<ProductHourlyStatistics>>()))
            .thenAnswer { it.arguments[0] }

        service.flushHourlySnapshot()

        verify(productHourlyStatisticsRepository).saveAll(any<List<ProductHourlyStatistics>>())
    }

    @Test
    fun 오늘_활동이_없는_통계는_시간별_스냅샷에서_건너뛴다() {
        whenever(productStatisticsRepository.findAllActive()).thenReturn(emptyList())

        service.flushHourlySnapshot()

        verify(productHourlyStatisticsRepository, org.mockito.kotlin.never())
            .saveAll(any<List<ProductHourlyStatistics>>())
    }

    @Test
    fun 기존_시간별_스냅샷이_있으면_업데이트한다() {
        val stats = createStats()
        stats.incrementSales(10, BigDecimal("100000"))
        val hourly = ProductHourlyStatistics.create(
            productId = 1L, statisticsDate = LocalDate.now(), hour = java.time.LocalDateTime.now().hour
        )

        whenever(productStatisticsRepository.findAllActive()).thenReturn(listOf(stats))
        whenever(productHourlyStatisticsRepository.findByStatisticsDateAndHourAndProductIdIn(any(), any<Int>(), any()))
            .thenReturn(listOf(hourly))
        whenever(productHourlyStatisticsRepository.saveAll(any<List<ProductHourlyStatistics>>()))
            .thenAnswer { it.arguments[0] }

        service.flushHourlySnapshot()

        assertThat(hourly.hourlySalesQuantity).isEqualTo(10)
    }
}
