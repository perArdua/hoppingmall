package com.hoppingmall.mall.product.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.hoppingmall.mall.category.domain.Category
import com.hoppingmall.mall.category.domain.repository.CategoryRepository
import com.hoppingmall.mall.inventory.dto.request.InventoryInitRequest
import com.hoppingmall.mall.inventory.service.InventoryCommandService
import com.hoppingmall.mall.product.domain.BulkImportJob
import com.hoppingmall.mall.product.domain.Product
import com.hoppingmall.mall.product.domain.ProductImage
import com.hoppingmall.mall.product.domain.repository.BulkImportJobRepository
import com.hoppingmall.mall.product.domain.repository.ProductImageRepository
import com.hoppingmall.mall.product.domain.repository.ProductRepository
import com.hoppingmall.mall.product.dto.request.BulkProductRow
import com.hoppingmall.mall.product.dto.response.BulkRowError
import com.hoppingmall.mall.product.enum.BulkImportStatus
import com.hoppingmall.mall.product.exception.ProductException
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.math.BigDecimal
import java.util.*

@DisplayName("BulkImportService")
@DisplayNameGeneration(ReplaceUnderscores::class)
class BulkImportServiceTest {

    private val csvParsingService: CsvParsingService = mock()
    private val bulkImportJobRepository: BulkImportJobRepository = mock()
    private val productRepository: ProductRepository = mock()
    private val productImageRepository: ProductImageRepository = mock()
    private val categoryRepository: CategoryRepository = mock()
    private val inventoryCommandService: InventoryCommandService = mock()
    private val objectMapper = jacksonObjectMapper()
    private val cacheManager: CacheManager = mock()

    private val bulkImportService = BulkImportService(
        csvParsingService,
        bulkImportJobRepository,
        productRepository,
        productImageRepository,
        categoryRepository,
        inventoryCommandService,
        objectMapper,
        cacheManager
    )

    @Nested
    @DisplayName("validate")
    inner class Validate {

        @Test
        fun CSV_검증_결과를_반환한다() {
            val file = mock<org.springframework.web.multipart.MultipartFile>()
            val rows = listOf(
                BulkProductRow(1, "상품1", "설명1", 1L, BigDecimal("15000"), 100, emptyList()),
                BulkProductRow(2, "상품2", "설명2", 2L, BigDecimal("25000"), 50, emptyList())
            )
            val category1 = Category.fixture(name = "카테고리1").withId(1L)
            val category2 = Category.fixture(name = "카테고리2").withId(2L)

            whenever(csvParsingService.parse(file)).thenReturn(CsvParsingService.ParseResult(rows, emptyList()))
            whenever(categoryRepository.findAllById(listOf(1L, 2L))).thenReturn(listOf(category1, category2))

            val result = bulkImportService.validate(file)

            assertEquals(2, result.totalRows)
            assertEquals(2, result.validRows)
            assertEquals(0, result.invalidRows)
            assertEquals(2, result.preview.size)
        }

        @Test
        fun 존재하지_않는_카테고리가_있으면_에러를_반환한다() {
            val file = mock<org.springframework.web.multipart.MultipartFile>()
            val rows = listOf(
                BulkProductRow(1, "상품1", "설명1", 1L, BigDecimal("15000"), 100, emptyList()),
                BulkProductRow(2, "상품2", "설명2", 999L, BigDecimal("25000"), 50, emptyList())
            )
            val category1 = Category.fixture(name = "카테고리1").withId(1L)

            whenever(csvParsingService.parse(file)).thenReturn(CsvParsingService.ParseResult(rows, emptyList()))
            whenever(categoryRepository.findAllById(listOf(1L, 999L))).thenReturn(listOf(category1))

            val result = bulkImportService.validate(file)

            assertEquals(2, result.totalRows)
            assertEquals(1, result.validRows)
            assertEquals(1, result.invalidRows)
            assertTrue(result.errors.any { it.rowNumber == 2 && it.field == "categoryId" })
        }
    }

    @Nested
    @DisplayName("startImport")
    inner class StartImport {

        @Test
        fun CSV_파싱_결과가_모두_에러이면_예외_발생() {
            val file = mock<org.springframework.web.multipart.MultipartFile>()
            val parseErrors = listOf(BulkRowError(0, "header", "필수 헤더 누락"))

            whenever(csvParsingService.parse(file)).thenReturn(CsvParsingService.ParseResult(emptyList(), parseErrors))

            assertThrows(ProductException::class.java) {
                bulkImportService.startImport(5L, file)
            }
        }

        @Test
        fun 정상_CSV로_임포트_작업을_생성한다() {
            val file = mock<org.springframework.web.multipart.MultipartFile>()
            whenever(file.originalFilename).thenReturn("products.csv")
            val rows = listOf(
                BulkProductRow(1, "상품1", "설명1", 1L, BigDecimal("15000"), 100, emptyList())
            )

            whenever(csvParsingService.parse(file)).thenReturn(CsvParsingService.ParseResult(rows, emptyList()))
            val savedJob = BulkImportJob.create(5L, "products.csv", 1).withId(1L)
            whenever(bulkImportJobRepository.save(any<BulkImportJob>())).thenReturn(savedJob)
            whenever(bulkImportJobRepository.findById(1L)).thenReturn(Optional.of(savedJob))

            val category1 = Category.fixture(name = "카테고리1").withId(1L)
            whenever(categoryRepository.findAllById(listOf(1L))).thenReturn(listOf(category1))
            whenever(productRepository.save(any<Product>())).thenAnswer { invocation ->
                (invocation.arguments[0] as Product).withId(100L)
            }
            whenever(productImageRepository.saveAll(any<List<ProductImage>>())).thenAnswer { it.arguments[0] }

            val result = bulkImportService.startImport(5L, file)

            assertEquals(1L, result.jobId)
        }

        @Test
        fun 트랜잭션_커밋_후_비동기_처리를_등록한다() {
            val file = mock<org.springframework.web.multipart.MultipartFile>()
            whenever(file.originalFilename).thenReturn("products.csv")
            val rows = listOf(
                BulkProductRow(1, "상품1", "설명1", 1L, BigDecimal("15000"), 100, emptyList())
            )

            whenever(csvParsingService.parse(file)).thenReturn(CsvParsingService.ParseResult(rows, emptyList()))
            val savedJob = BulkImportJob.create(5L, "products.csv", 1).withId(1L)
            whenever(bulkImportJobRepository.save(any<BulkImportJob>())).thenReturn(savedJob)
            whenever(bulkImportJobRepository.findById(1L)).thenReturn(Optional.of(savedJob))

            val category1 = Category.fixture(name = "카테고리1").withId(1L)
            whenever(categoryRepository.findAllById(listOf(1L))).thenReturn(listOf(category1))
            whenever(productRepository.save(any<Product>())).thenAnswer { invocation ->
                (invocation.arguments[0] as Product).withId(100L)
            }
            whenever(productImageRepository.saveAll(any<List<ProductImage>>())).thenAnswer { it.arguments[0] }

            TransactionSynchronizationManager.initSynchronization()
            try {
                val result = bulkImportService.startImport(5L, file)

                assertEquals(1L, result.jobId)
                val synchronizations = TransactionSynchronizationManager.getSynchronizations()
                assertEquals(1, synchronizations.size)

                synchronizations.forEach { it.afterCommit() }
            } finally {
                TransactionSynchronizationManager.clearSynchronization()
            }
        }
    }

    @Nested
    @DisplayName("processImport")
    inner class ProcessImport {

        @Test
        fun 정상_행을_상품으로_등록한다() {
            val jobId = 1L
            val sellerId = 5L
            val rows = listOf(
                BulkProductRow(1, "상품1", "설명1", 1L, BigDecimal("15000"), 100, listOf("img1.jpg")),
                BulkProductRow(2, "상품2", "설명2", 1L, BigDecimal("25000"), 50, emptyList())
            )
            val job = BulkImportJob.create(sellerId, "test.csv", 2).withId(jobId)
            job.startImport()
            val category1 = Category.fixture(name = "카테고리1").withId(1L)

            whenever(bulkImportJobRepository.findById(jobId)).thenReturn(Optional.of(job))
            whenever(categoryRepository.findAllById(listOf(1L))).thenReturn(listOf(category1))
            whenever(productRepository.save(any<Product>())).thenAnswer { invocation ->
                (invocation.arguments[0] as Product).withId(100L)
            }
            whenever(productImageRepository.saveAll(any<List<ProductImage>>())).thenAnswer { it.arguments[0] }
            whenever(bulkImportJobRepository.save(any<BulkImportJob>())).thenAnswer { it.arguments[0] }

            bulkImportService.processImport(jobId, sellerId, rows, emptyList())

            verify(productRepository, times(2)).save(any<Product>())
            verify(inventoryCommandService, times(2)).initStock(any<InventoryInitRequest>())
            assertEquals(BulkImportStatus.COMPLETED, job.status)
            assertEquals(2, job.successCount)
            assertEquals(0, job.failCount)
        }

        @Test
        fun 존재하지_않는_카테고리_행은_실패로_기록한다() {
            val jobId = 1L
            val sellerId = 5L
            val rows = listOf(
                BulkProductRow(1, "상품1", "설명1", 1L, BigDecimal("15000"), 100, emptyList()),
                BulkProductRow(2, "상품2", "설명2", 999L, BigDecimal("25000"), 50, emptyList())
            )
            val job = BulkImportJob.create(sellerId, "test.csv", 2).withId(jobId)
            job.startImport()
            val category1 = Category.fixture(name = "카테고리1").withId(1L)

            whenever(bulkImportJobRepository.findById(jobId)).thenReturn(Optional.of(job))
            whenever(categoryRepository.findAllById(listOf(1L, 999L))).thenReturn(listOf(category1))
            whenever(productRepository.save(any<Product>())).thenAnswer { invocation ->
                (invocation.arguments[0] as Product).withId(100L)
            }
            whenever(productImageRepository.saveAll(any<List<ProductImage>>())).thenAnswer { it.arguments[0] }
            whenever(bulkImportJobRepository.save(any<BulkImportJob>())).thenAnswer { it.arguments[0] }

            bulkImportService.processImport(jobId, sellerId, rows, emptyList())

            verify(productRepository, times(1)).save(any<Product>())
            assertEquals(1, job.successCount)
            assertEquals(1, job.failCount)
            assertNotNull(job.errorDetails)
        }

        @Test
        fun 파싱_에러가_있는_행은_실패로_카운트한다() {
            val jobId = 1L
            val sellerId = 5L
            val rows = listOf(
                BulkProductRow(2, "상품1", "설명1", 1L, BigDecimal("15000"), 100, emptyList())
            )
            val parseErrors = listOf(BulkRowError(1, "name", "상품명은 필수입니다."))
            val job = BulkImportJob.create(sellerId, "test.csv", 2).withId(jobId)
            job.startImport()
            val category1 = Category.fixture(name = "카테고리1").withId(1L)

            whenever(bulkImportJobRepository.findById(jobId)).thenReturn(Optional.of(job))
            whenever(categoryRepository.findAllById(listOf(1L))).thenReturn(listOf(category1))
            whenever(productRepository.save(any<Product>())).thenAnswer { invocation ->
                (invocation.arguments[0] as Product).withId(100L)
            }
            whenever(productImageRepository.saveAll(any<List<ProductImage>>())).thenAnswer { it.arguments[0] }
            whenever(bulkImportJobRepository.save(any<BulkImportJob>())).thenAnswer { it.arguments[0] }

            bulkImportService.processImport(jobId, sellerId, rows, parseErrors)

            assertEquals(1, job.successCount)
            assertEquals(1, job.failCount)
        }
    }

    @Nested
    @DisplayName("getJobProgress")
    inner class GetJobProgress {

        @Test
        fun 작업_진행률을_조회한다() {
            val jobId = 1L
            val sellerId = 5L
            val job = BulkImportJob.create(sellerId, "test.csv", 10).withId(jobId)

            whenever(bulkImportJobRepository.findById(jobId)).thenReturn(Optional.of(job))

            val result = bulkImportService.getJobProgress(jobId, sellerId)

            assertEquals(jobId, result.jobId)
            assertEquals(10, result.totalRows)
        }

        @Test
        fun 다른_판매자의_작업_조회_시_예외_발생() {
            val jobId = 1L
            val job = BulkImportJob.create(5L, "test.csv", 10).withId(jobId)

            whenever(bulkImportJobRepository.findById(jobId)).thenReturn(Optional.of(job))

            assertThrows(ProductException::class.java) {
                bulkImportService.getJobProgress(jobId, 999L)
            }
        }
    }

    @Nested
    @DisplayName("getJobErrors")
    inner class GetJobErrors {

        @Test
        fun 에러_상세_정보를_반환한다() {
            val jobId = 1L
            val sellerId = 5L
            val errors = listOf(BulkRowError(1, "name", "상품명은 필수입니다."))
            val job = BulkImportJob.create(sellerId, "test.csv", 10).withId(jobId)
            job.errorDetails = objectMapper.writeValueAsString(errors)

            whenever(bulkImportJobRepository.findById(jobId)).thenReturn(Optional.of(job))

            val result = bulkImportService.getJobErrors(jobId, sellerId)

            assertEquals(1, result.size)
            assertEquals("name", result[0].field)
        }

        @Test
        fun 에러가_없으면_빈_리스트를_반환한다() {
            val jobId = 1L
            val sellerId = 5L
            val job = BulkImportJob.create(sellerId, "test.csv", 10).withId(jobId)

            whenever(bulkImportJobRepository.findById(jobId)).thenReturn(Optional.of(job))

            val result = bulkImportService.getJobErrors(jobId, sellerId)

            assertTrue(result.isEmpty())
        }
    }
}
