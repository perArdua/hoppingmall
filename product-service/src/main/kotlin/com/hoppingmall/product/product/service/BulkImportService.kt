package com.hoppingmall.product.product.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.repository.findByIdOrNull
import com.hoppingmall.product.category.domain.repository.CategoryRepository
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.common.file.FileUploadConfig
import com.hoppingmall.product.inventory.dto.request.InventoryInitRequest
import com.hoppingmall.product.inventory.service.InventoryCommandService
import com.hoppingmall.product.product.domain.BulkImportJob
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.ProductImage
import com.hoppingmall.product.product.domain.repository.BulkImportJobRepository
import com.hoppingmall.product.product.domain.repository.ProductImageRepository
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.dto.request.BulkProductRow
import com.hoppingmall.product.product.dto.response.*
import com.hoppingmall.product.product.exception.ProductException
import com.hoppingmall.product.product.exception.code.ProductErrorCode
import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile

@Service
class BulkImportService(
    private val bulkImportValidator: BulkImportValidator,
    private val csvParsingService: CsvParsingService,
    private val bulkImportJobRepository: BulkImportJobRepository,
    private val productRepository: ProductRepository,
    private val productImageRepository: ProductImageRepository,
    private val categoryRepository: CategoryRepository,
    private val inventoryCommandService: InventoryCommandService,
    private val objectMapper: ObjectMapper,
    private val cacheManager: CacheManager,
    private val fileUploadConfig: FileUploadConfig
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val CHUNK_SIZE = 50
    }

    fun validate(file: MultipartFile): BulkValidationResponse {
        return bulkImportValidator.validate(file)
    }

    @Transactional
    fun startImport(sellerId: Long, file: MultipartFile): BulkImportProgressResponse {
        val parseResult = csvParsingService.parse(file)

        if (parseResult.rows.isEmpty() && parseResult.errors.isNotEmpty()) {
            throw ProductException(ProductErrorCode.BULK_IMPORT_INVALID_CSV)
        }

        val totalRows = parseResult.rows.size + parseResult.errors.filter { it.rowNumber > 0 }
            .map { it.rowNumber }.distinct().size
        val job = bulkImportJobRepository.save(
            BulkImportJob.create(sellerId = sellerId, fileName = file.originalFilename ?: "unknown.csv", totalRows = totalRows)
        )
        job.startImport()
        bulkImportJobRepository.save(job)

        val jobId = job.id!!
        val rows = parseResult.rows
        val errors = parseResult.errors

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    processImportAsync(jobId, sellerId, rows, errors)
                }
            })
        } else {
            processImportAsync(jobId, sellerId, rows, errors)
        }

        return BulkImportProgressResponse.from(job)
    }

    @Async("bulkImportExecutor")
    fun processImportAsync(jobId: Long, sellerId: Long, rows: List<BulkProductRow>, parseErrors: List<BulkRowError>) {
        processImport(jobId, sellerId, rows, parseErrors)
    }

    @Transactional
    fun processImport(jobId: Long, sellerId: Long, rows: List<BulkProductRow>, parseErrors: List<BulkRowError>) {
        val job = bulkImportJobRepository.findByIdOrNull(jobId)
            ?: throw ProductException(ProductErrorCode.BULK_IMPORT_JOB_NOT_FOUND)

        val allErrors = parseErrors.toMutableList()
        val categoryIds = rows.map { it.categoryId }.distinct()
        val existingCategoryIds = categoryRepository.findAllById(categoryIds).map { it.id!! }.toSet()

        parseErrors.filter { it.rowNumber > 0 }.map { it.rowNumber }.distinct().forEach { _ ->
            job.recordFailure()
        }

        val allProductImages = mutableListOf<ProductImage>()

        for (row in rows) {
            try {
                if (row.categoryId !in existingCategoryIds) {
                    allErrors.add(BulkRowError(row.rowNumber, "categoryId", "존재하지 않는 카테고리입니다: ${row.categoryId}"))
                    job.recordFailure()
                    continue
                }

                val product = productRepository.save(
                    Product.create(
                        sellerId = sellerId,
                        categoryId = row.categoryId,
                        name = row.name,
                        description = row.description,
                        price = row.price,
                        status = ProductStatus.AVAILABLE
                    )
                )

                val imageUrls = row.imageUrls.ifEmpty { listOf(fileUploadConfig.defaultImagePath) }
                val productImages = imageUrls.mapIndexed { index, url ->
                    ProductImage.create(productId = product.id!!, imageUrl = url, sortOrder = index)
                }
                allProductImages.addAll(productImages)

                inventoryCommandService.initStock(
                    InventoryInitRequest(productId = product.id!!, stockQuantity = row.stockQuantity)
                )

                job.recordSuccess()
            } catch (e: Exception) {
                log.warn("대량 등록 행 {} 실패: {}", row.rowNumber, e.message)
                allErrors.add(BulkRowError(row.rowNumber, "system", e.message ?: "알 수 없는 오류"))
                job.recordFailure()
            }
        }

        if (allProductImages.isNotEmpty()) {
            allProductImages.chunked(CHUNK_SIZE).forEach { chunk ->
                productImageRepository.saveAll(chunk)
            }
        }

        if (allErrors.isNotEmpty()) {
            job.errorDetails = objectMapper.writeValueAsString(allErrors)
        }
        job.complete()
        bulkImportJobRepository.save(job)

        evictProductCaches()

        log.info("대량 등록 완료: jobId={}, success={}, fail={}", jobId, job.successCount, job.failCount)
    }

    private fun evictProductCaches() {
        try {
            cacheManager.getCache("product")?.clear()
            log.info("대량 등록 후 product 캐시 초기화 완료")
        } catch (e: Exception) {
            log.warn("캐시 초기화 실패: {}", e.message)
        }
    }

    @Transactional(readOnly = true)
    fun getJobProgress(jobId: Long, sellerId: Long): BulkImportProgressResponse {
        val job = bulkImportJobRepository.findByIdOrNull(jobId)
            ?: throw ProductException(ProductErrorCode.BULK_IMPORT_JOB_NOT_FOUND)
        if (job.sellerId != sellerId) {
            throw ProductException(ProductErrorCode.BULK_IMPORT_ACCESS_DENIED)
        }
        return BulkImportProgressResponse.from(job)
    }

    @Transactional(readOnly = true)
    fun getJobErrors(jobId: Long, sellerId: Long): List<BulkRowError> {
        val job = bulkImportJobRepository.findByIdOrNull(jobId)
            ?: throw ProductException(ProductErrorCode.BULK_IMPORT_JOB_NOT_FOUND)
        if (job.sellerId != sellerId) {
            throw ProductException(ProductErrorCode.BULK_IMPORT_ACCESS_DENIED)
        }
        if (job.errorDetails == null) {
            return emptyList()
        }
        return objectMapper.readValue(
            job.errorDetails,
            objectMapper.typeFactory.constructCollectionType(List::class.java, BulkRowError::class.java)
        )
    }
}
