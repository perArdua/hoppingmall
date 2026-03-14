package com.hoppingmall.product.product.dto.response

data class BulkValidationResponse(
    val totalRows: Int,
    val validRows: Int,
    val invalidRows: Int,
    val errors: List<BulkRowError>,
    val preview: List<BulkProductPreview>
)

data class BulkRowError(
    val rowNumber: Int,
    val field: String,
    val message: String
)

data class BulkProductPreview(
    val rowNumber: Int,
    val name: String,
    val categoryId: Long,
    val price: String,
    val stockQuantity: Int
)
