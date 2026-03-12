package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.product.dto.request.BulkProductRow
import com.hoppingmall.mall.product.dto.response.BulkRowError
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.InputStreamReader
import java.math.BigDecimal

@Service
class CsvParsingService {

    private val requiredHeaders = setOf("name", "description", "categoryId", "price", "stockQuantity")
    private val maxRowLimit = 1000

    data class ParseResult(
        val rows: List<BulkProductRow>,
        val errors: List<BulkRowError>
    )

    fun parse(file: MultipartFile): ParseResult {
        val rows = mutableListOf<BulkProductRow>()
        val errors = mutableListOf<BulkRowError>()

        val reader = InputStreamReader(file.inputStream, Charsets.UTF_8)
        val csvFormat = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build()

        val parser = CSVParser(reader, csvFormat)
        val headerMap = parser.headerNames.associateBy { it.lowercase() }
        val headersLower = headerMap.keys

        val missingHeaders = requiredHeaders.map { it.lowercase() }.toSet() - headersLower
        if (missingHeaders.isNotEmpty()) {
            errors.add(BulkRowError(0, "header", "필수 헤더 누락: ${missingHeaders.joinToString(", ")}"))
            return ParseResult(emptyList(), errors)
        }

        var rowNumber = 0
        for (record in parser) {
            rowNumber++
            if (rowNumber > maxRowLimit) {
                errors.add(BulkRowError(rowNumber, "row", "최대 ${maxRowLimit}행까지 처리 가능합니다."))
                break
            }

            val rowErrors = mutableListOf<BulkRowError>()

            val name = record.get("name")?.trim() ?: ""
            if (name.isBlank()) {
                rowErrors.add(BulkRowError(rowNumber, "name", "상품명은 필수입니다."))
            }

            val description = record.get("description")?.trim() ?: ""
            if (description.isBlank()) {
                rowErrors.add(BulkRowError(rowNumber, "description", "상품 설명은 필수입니다."))
            }

            val categoryIdStr = record.get("categoryId")?.trim() ?: ""
            val categoryId = categoryIdStr.toLongOrNull()
            if (categoryId == null || categoryId <= 0) {
                rowErrors.add(BulkRowError(rowNumber, "categoryId", "유효한 카테고리 ID가 필요합니다."))
            }

            val priceStr = record.get("price")?.trim() ?: ""
            val price = priceStr.toBigDecimalOrNull()
            if (price == null || price <= BigDecimal.ZERO) {
                rowErrors.add(BulkRowError(rowNumber, "price", "가격은 0보다 커야 합니다."))
            }

            val stockStr = record.get("stockQuantity")?.trim() ?: ""
            val stockQuantity = stockStr.toIntOrNull()
            if (stockQuantity == null || stockQuantity < 0) {
                rowErrors.add(BulkRowError(rowNumber, "stockQuantity", "재고 수량은 0 이상이어야 합니다."))
            }

            val imageUrlsStr = record.isMapped("imageUrls").let {
                if (it) record.get("imageUrls")?.trim() ?: "" else ""
            }
            val imageUrls = if (imageUrlsStr.isNotBlank()) {
                imageUrlsStr.split("|").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }

            if (rowErrors.isNotEmpty()) {
                errors.addAll(rowErrors)
            } else {
                rows.add(
                    BulkProductRow(
                        rowNumber = rowNumber,
                        name = name,
                        description = description,
                        categoryId = categoryId!!,
                        price = price!!,
                        stockQuantity = stockQuantity!!,
                        imageUrls = imageUrls
                    )
                )
            }
        }

        parser.close()
        return ParseResult(rows, errors)
    }
}
