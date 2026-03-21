package com.hoppingmall.product.product.service

import com.hoppingmall.product.category.domain.repository.CategoryRepository
import com.hoppingmall.product.common.file.FileUploadConfig
import com.hoppingmall.product.product.dto.response.BulkProductPreview
import com.hoppingmall.product.product.dto.response.BulkRowError
import com.hoppingmall.product.product.dto.response.BulkValidationResponse
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile

@Component
class BulkImportValidator(
    private val csvParsingService: CsvParsingService,
    private val categoryRepository: CategoryRepository,
    private val fileUploadConfig: FileUploadConfig
) {

    fun validate(file: MultipartFile): BulkValidationResponse {
        val parseResult = csvParsingService.parse(file)
        val errors = parseResult.errors.toMutableList()
        val validRows = parseResult.rows

        val categoryIds = validRows.map { it.categoryId }.distinct()
        val existingCategoryIds = categoryRepository.findAllById(categoryIds).map { it.id!! }.toSet()
        validRows.forEach { row ->
            if (row.categoryId !in existingCategoryIds) {
                errors.add(BulkRowError(row.rowNumber, "categoryId", "존재하지 않는 카테고리입니다: ${row.categoryId}"))
            }
        }

        val errorRowNumbers = errors.filter { it.rowNumber > 0 }.map { it.rowNumber }.toSet()
        val validRowNumbers = validRows.map { it.rowNumber }.toSet() - errorRowNumbers
        val parseErrorRowNumbers = parseResult.errors.filter { it.rowNumber > 0 }.map { it.rowNumber }.toSet()

        val preview = validRows.filter { it.rowNumber in validRowNumbers }.map { row ->
            BulkProductPreview(
                rowNumber = row.rowNumber,
                name = row.name,
                categoryId = row.categoryId,
                price = row.price.toPlainString(),
                stockQuantity = row.stockQuantity
            )
        }

        val totalRows = validRows.size + parseErrorRowNumbers.size
        val invalidRowCount = errorRowNumbers.size + parseErrorRowNumbers.size

        return BulkValidationResponse(
            totalRows = totalRows,
            validRows = preview.size,
            invalidRows = invalidRowCount,
            errors = errors,
            preview = preview
        )
    }
}
