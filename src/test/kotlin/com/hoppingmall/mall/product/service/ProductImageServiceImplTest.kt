package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.global.file.dto.response.FileUploadResponse
import com.hoppingmall.mall.global.file.service.FileUploadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("ProductImageServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ProductImageServiceImplTest {

    private val fileUploadService: FileUploadService = mock()
    private val productImageService = ProductImageServiceImpl(fileUploadService)

    @Nested
    @DisplayName("uploadProductImage")
    inner class UploadProductImage {
        
        @Nested
        @DisplayName("상품 이미지 업로드 성공 케이스")
        inner class ProductImageUploadSuccess {
            
            @Test
            fun 상품_이미지_업로드_성공() {
                // Data
                val imageFile = mock<org.springframework.web.multipart.MultipartFile>()
                val fileUploadResponse = FileUploadResponse(
                    fileUrl = "https://example.com/product/image.jpg",
                    fileName = "product-image.jpg",
                    fileSize = 1024L,
                    domain = "product"
                )

                // Context
                whenever(fileUploadService.uploadFile(any())).thenReturn(fileUploadResponse)

                // Interaction
                val result = productImageService.uploadProductImage(imageFile)

                // Assertions
                assertThat(result).isNotNull()
                assertThat(result.imageUrl).isEqualTo(fileUploadResponse.fileUrl)
                assertThat(result.fileName).isEqualTo(fileUploadResponse.fileName)
                assertThat(result.fileSize).isEqualTo(fileUploadResponse.fileSize)
                
                verify(fileUploadService).uploadFile(any())
            }
        }
    }
} 