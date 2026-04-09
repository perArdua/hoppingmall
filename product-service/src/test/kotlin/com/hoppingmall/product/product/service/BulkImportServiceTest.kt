package com.hoppingmall.product.product.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.product.category.domain.Category
import com.hoppingmall.product.category.domain.repository.CategoryRepository
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.common.file.FileUploadConfig
import com.hoppingmall.product.inventory.service.InventoryCommandService
import com.hoppingmall.product.product.domain.BulkImportJob
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.ProductImage
import com.hoppingmall.product.product.domain.repository.BulkImportJobRepository
import com.hoppingmall.product.product.domain.repository.ProductImageRepository
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.dto.request.BulkProductRow
import com.hoppingmall.product.product.dto.response.BulkRowError
import com.hoppingmall.product.product.enum.BulkImportStatus
import com.hoppingmall.product.product.exception.ProductException
import com.hoppingmall.product.support.withId
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
import org.mockito.kotlin.whenever
import org.springframework.cache.CacheManager
import org.springframework.cache.support.NoOpCache
import org.springframework.mock.web.MockMultipartFile
import java.math.BigDecimal
import java.util.Optional

@DisplayName("BulkImportService")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class BulkImportServiceTest {

    @Mock
    private lateinit var bulkImportValidator: BulkImportValidator

    @Mock
    private lateinit var csvParsingService: CsvParsingService

    @Mock
    private lateinit var bulkImportJobRepository: BulkImportJobRepository

    @Mock
    private lateinit var productRepository: ProductRepository

    @Mock
    private lateinit var productImageRepository: ProductImageRepository

    @Mock
    private lateinit var categoryRepository: CategoryRepository

    @Mock
    private lateinit var inventoryCommandService: InventoryCommandService

    @Mock
    private lateinit var objectMapper: ObjectMapper

    @Mock
    private lateinit var cacheManager: CacheManager

    @Mock
    private lateinit var fileUploadConfig: FileUploadConfig

    @InjectMocks
    private lateinit var service: BulkImportService

    @Test
    fun validate를_위임한다() {
        val file = MockMultipartFile("file", "test.csv", "text/csv", "data".toByteArray())
        val expected = com.hoppingmall.product.product.dto.response.BulkValidationResponse(
            totalRows = 1, validRows = 1, invalidRows = 0, errors = emptyList(), preview = emptyList()
        )

        whenever(bulkImportValidator.validate(any())).thenReturn(expected)

        val result = service.validate(file)

        assertThat(result.totalRows).isEqualTo(1)
    }

    @Test
    fun 파싱_실패_시_예외를_발생시킨다() {
        val file = MockMultipartFile("file", "test.csv", "text/csv", "data".toByteArray())
        val errors = listOf(BulkRowError(0, "header", "필수 헤더 누락"))

        whenever(csvParsingService.parse(any())).thenReturn(CsvParsingService.ParseResult(emptyList(), errors))

        assertThatThrownBy { service.startImport(1L, file) }
            .isInstanceOf(ProductException::class.java)
    }

    @Test
    fun startImport_트랜잭션_동기화가_활성화된_경우_afterCommit으로_처리한다() {
        val file = MockMultipartFile("file", "test.csv", "text/csv", "data".toByteArray())
        val rows = listOf(BulkProductRow(1, "상품A", "설명", 1L, BigDecimal("1000"), 10, emptyList()))
        val job = BulkImportJob.create(sellerId = 1L, fileName = "test.csv", totalRows = 1).withId(1L)
        val category = Category.create(name = "전자기기", parentCategoryId = null, depth = 0).withId(1L)
        val product = Product.create(
            sellerId = 1L, categoryId = 1L, name = "상품A",
            description = "설명", price = BigDecimal("1000"), status = ProductStatus.AVAILABLE
        ).withId(1L)

        whenever(csvParsingService.parse(any())).thenReturn(CsvParsingService.ParseResult(rows, emptyList()))
        whenever(bulkImportJobRepository.save(any<BulkImportJob>())).thenReturn(job)
        whenever(bulkImportJobRepository.findById(1L)).thenReturn(Optional.of(job))
        whenever(categoryRepository.findAllById(any<List<Long>>())).thenReturn(listOf(category))
        whenever(productRepository.save(any<Product>())).thenReturn(product)
        whenever(fileUploadConfig.defaultImagePath).thenReturn("/default.jpg")
        whenever(productImageRepository.saveAll(any<List<ProductImage>>())).thenReturn(emptyList())
        whenever(cacheManager.getCache("product")).thenReturn(NoOpCache("product"))

        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization()
        try {
            val result = service.startImport(1L, file)
            assertThat(result.totalRows).isEqualTo(1)

            val synchronizations = org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
            synchronizations.forEach { it.afterCommit() }
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun startImport로_대량_등록을_시작한다() {
        val file = MockMultipartFile("file", "test.csv", "text/csv", "data".toByteArray())
        val rows = listOf(BulkProductRow(1, "상품A", "설명", 1L, BigDecimal("1000"), 10, emptyList()))
        val job = BulkImportJob.create(sellerId = 1L, fileName = "test.csv", totalRows = 1).withId(1L)
        val category = Category.create(name = "전자기기", parentCategoryId = null, depth = 0).withId(1L)
        val product = Product.create(
            sellerId = 1L, categoryId = 1L, name = "상품A",
            description = "설명", price = BigDecimal("1000"), status = ProductStatus.AVAILABLE
        ).withId(1L)

        whenever(csvParsingService.parse(any())).thenReturn(CsvParsingService.ParseResult(rows, emptyList()))
        whenever(bulkImportJobRepository.save(any<BulkImportJob>())).thenReturn(job)
        whenever(bulkImportJobRepository.findById(1L)).thenReturn(Optional.of(job))
        whenever(categoryRepository.findAllById(any<List<Long>>())).thenReturn(listOf(category))
        whenever(productRepository.save(any<Product>())).thenReturn(product)
        whenever(fileUploadConfig.defaultImagePath).thenReturn("/default.jpg")
        whenever(productImageRepository.saveAll(any<List<ProductImage>>())).thenReturn(emptyList())
        whenever(cacheManager.getCache("product")).thenReturn(NoOpCache("product"))

        val result = service.startImport(1L, file)

        assertThat(result.totalRows).isEqualTo(1)
    }

    @Test
    fun processImport에서_상품_저장_시_예외가_발생하면_실패_처리한다() {
        val job = BulkImportJob.create(sellerId = 1L, fileName = "test.csv", totalRows = 1).withId(1L)
        job.startImport()
        val category = Category.create(name = "전자기기", parentCategoryId = null, depth = 0).withId(1L)
        val rows = listOf(BulkProductRow(1, "상품A", "설명", 1L, BigDecimal("1000"), 10, emptyList()))

        whenever(bulkImportJobRepository.findById(1L)).thenReturn(Optional.of(job))
        whenever(categoryRepository.findAllById(listOf(1L))).thenReturn(listOf(category))
        whenever(productRepository.save(any<Product>())).thenThrow(RuntimeException("DB 에러"))
        whenever(bulkImportJobRepository.save(any<BulkImportJob>())).thenReturn(job)
        whenever(objectMapper.writeValueAsString(any())).thenReturn("[]")
        whenever(cacheManager.getCache("product")).thenReturn(NoOpCache("product"))

        service.processImport(1L, 1L, rows, emptyList())

        assertThat(job.failCount).isEqualTo(1)
    }

    @Test
    fun processImport에서_캐시_초기화_실패_시에도_정상_완료한다() {
        val job = BulkImportJob.create(sellerId = 1L, fileName = "test.csv", totalRows = 0).withId(1L)
        job.startImport()

        whenever(bulkImportJobRepository.findById(1L)).thenReturn(Optional.of(job))
        whenever(categoryRepository.findAllById(emptyList())).thenReturn(emptyList())
        whenever(bulkImportJobRepository.save(any<BulkImportJob>())).thenReturn(job)
        whenever(cacheManager.getCache("product")).thenThrow(RuntimeException("캐시 에러"))

        service.processImport(1L, 1L, emptyList(), emptyList())

        assertThat(job.status).isEqualTo(com.hoppingmall.product.product.enum.BulkImportStatus.COMPLETED)
    }

    @Test
    fun processImport에서_이미지_URL이_있는_행을_처리한다() {
        val job = BulkImportJob.create(sellerId = 1L, fileName = "test.csv", totalRows = 1).withId(1L)
        job.startImport()
        val category = Category.create(name = "전자기기", parentCategoryId = null, depth = 0).withId(1L)
        val product = Product.create(
            sellerId = 1L, categoryId = 1L, name = "상품A",
            description = "설명", price = BigDecimal("1000"), status = ProductStatus.AVAILABLE
        ).withId(1L)
        val rows = listOf(BulkProductRow(1, "상품A", "설명", 1L, BigDecimal("1000"), 10, listOf("http://img.jpg")))

        whenever(bulkImportJobRepository.findById(1L)).thenReturn(Optional.of(job))
        whenever(categoryRepository.findAllById(listOf(1L))).thenReturn(listOf(category))
        whenever(productRepository.save(any<Product>())).thenReturn(product)
        whenever(productImageRepository.saveAll(any<List<com.hoppingmall.product.product.domain.ProductImage>>())).thenReturn(emptyList())
        whenever(bulkImportJobRepository.save(any<BulkImportJob>())).thenReturn(job)
        whenever(cacheManager.getCache("product")).thenReturn(NoOpCache("product"))

        service.processImport(1L, 1L, rows, emptyList())

        assertThat(job.successCount).isEqualTo(1)
    }

    @Test
    fun processImport로_상품을_대량_등록한다() {
        val job = BulkImportJob.create(sellerId = 1L, fileName = "test.csv", totalRows = 1).withId(1L)
        job.startImport()
        val category = Category.create(name = "전자기기", parentCategoryId = null, depth = 0).withId(1L)
        val product = Product.create(
            sellerId = 1L, categoryId = 1L, name = "상품A",
            description = "설명", price = BigDecimal("1000"), status = ProductStatus.AVAILABLE
        ).withId(1L)
        val rows = listOf(BulkProductRow(1, "상품A", "설명", 1L, BigDecimal("1000"), 10, emptyList()))

        whenever(bulkImportJobRepository.findById(1L)).thenReturn(Optional.of(job))
        whenever(categoryRepository.findAllById(listOf(1L))).thenReturn(listOf(category))
        whenever(productRepository.save(any<Product>())).thenReturn(product)
        whenever(fileUploadConfig.defaultImagePath).thenReturn("/default.jpg")
        whenever(productImageRepository.saveAll(any<List<com.hoppingmall.product.product.domain.ProductImage>>())).thenReturn(emptyList())
        whenever(bulkImportJobRepository.save(any<BulkImportJob>())).thenReturn(job)
        whenever(cacheManager.getCache("product")).thenReturn(NoOpCache("product"))

        service.processImport(1L, 1L, rows, emptyList())

        assertThat(job.successCount).isEqualTo(1)
        assertThat(job.status).isEqualTo(BulkImportStatus.COMPLETED)
    }

    @Test
    fun 작업_진행_상태를_조회한다() {
        val job = BulkImportJob.create(sellerId = 1L, fileName = "test.csv", totalRows = 10).withId(1L)

        whenever(bulkImportJobRepository.findById(1L)).thenReturn(Optional.of(job))

        val result = service.getJobProgress(1L, 1L)

        assertThat(result.totalRows).isEqualTo(10)
    }

    @Test
    fun 다른_판매자의_작업_진행_조회_시_예외를_발생시킨다() {
        val job = BulkImportJob.create(sellerId = 1L, fileName = "test.csv", totalRows = 10).withId(1L)

        whenever(bulkImportJobRepository.findById(1L)).thenReturn(Optional.of(job))

        assertThatThrownBy { service.getJobProgress(1L, 999L) }
            .isInstanceOf(ProductException::class.java)
    }

    @Test
    fun 존재하지_않는_작업_진행_조회_시_예외를_발생시킨다() {
        whenever(bulkImportJobRepository.findById(999L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.getJobProgress(999L, 1L) }
            .isInstanceOf(ProductException::class.java)
    }

    @Test
    fun 작업_에러를_조회한다() {
        val job = BulkImportJob.create(sellerId = 1L, fileName = "test.csv", totalRows = 10).withId(1L)
        job.errorDetails = """[{"rowNumber":1,"field":"name","message":"err"}]"""
        val errors = listOf(BulkRowError(1, "name", "err"))

        whenever(bulkImportJobRepository.findById(1L)).thenReturn(Optional.of(job))
        val typeFactory = ObjectMapper().typeFactory
        whenever(objectMapper.typeFactory).thenReturn(typeFactory)
        whenever(objectMapper.readValue<List<BulkRowError>>(any<String>(), any<com.fasterxml.jackson.databind.JavaType>())).thenReturn(errors)

        val result = service.getJobErrors(1L, 1L)

        assertThat(result).hasSize(1)
    }

    @Test
    fun 에러가_없는_작업의_에러_조회_시_빈_리스트를_반환한다() {
        val job = BulkImportJob.create(sellerId = 1L, fileName = "test.csv", totalRows = 10).withId(1L)

        whenever(bulkImportJobRepository.findById(1L)).thenReturn(Optional.of(job))

        val result = service.getJobErrors(1L, 1L)

        assertThat(result).isEmpty()
    }

    @Test
    fun 다른_판매자의_에러_조회_시_예외를_발생시킨다() {
        val job = BulkImportJob.create(sellerId = 1L, fileName = "test.csv", totalRows = 10).withId(1L)

        whenever(bulkImportJobRepository.findById(1L)).thenReturn(Optional.of(job))

        assertThatThrownBy { service.getJobErrors(1L, 999L) }
            .isInstanceOf(ProductException::class.java)
    }

    @Test
    fun processImport에서_존재하지_않는_카테고리_행을_실패_처리한다() {
        val job = BulkImportJob.create(sellerId = 1L, fileName = "test.csv", totalRows = 1).withId(1L)
        job.startImport()
        val rows = listOf(BulkProductRow(1, "상품A", "설명", 999L, BigDecimal("1000"), 10, emptyList()))

        whenever(bulkImportJobRepository.findById(1L)).thenReturn(Optional.of(job))
        whenever(categoryRepository.findAllById(listOf(999L))).thenReturn(emptyList())
        whenever(bulkImportJobRepository.save(any<BulkImportJob>())).thenReturn(job)
        whenever(objectMapper.writeValueAsString(any())).thenReturn("[]")
        whenever(cacheManager.getCache("product")).thenReturn(NoOpCache("product"))

        service.processImport(1L, 1L, rows, emptyList())

        assertThat(job.failCount).isEqualTo(1)
    }

    @Test
    fun processImport에서_파싱_에러_행을_실패_처리한다() {
        val job = BulkImportJob.create(sellerId = 1L, fileName = "test.csv", totalRows = 1).withId(1L)
        job.startImport()
        val parseErrors = listOf(BulkRowError(1, "name", "에러"))

        whenever(bulkImportJobRepository.findById(1L)).thenReturn(Optional.of(job))
        whenever(categoryRepository.findAllById(emptyList())).thenReturn(emptyList())
        whenever(bulkImportJobRepository.save(any<BulkImportJob>())).thenReturn(job)
        whenever(objectMapper.writeValueAsString(any())).thenReturn("[]")
        whenever(cacheManager.getCache("product")).thenReturn(NoOpCache("product"))

        service.processImport(1L, 1L, emptyList(), parseErrors)

        assertThat(job.failCount).isEqualTo(1)
    }
}
