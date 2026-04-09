package com.hoppingmall.product.product.service

import com.hoppingmall.product.common.file.FileUploadService
import com.hoppingmall.product.common.file.dto.FileUploadResponse
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.mock.web.MockMultipartFile

@DisplayName("ProductImageServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ProductImageServiceImplTest {

    @Mock
    private lateinit var fileUploadService: FileUploadService

    @InjectMocks
    private lateinit var service: ProductImageServiceImpl

    @Test
    fun 상품_이미지를_업로드한다() {
        val file = MockMultipartFile("image", "test.jpg", "image/jpeg", "test".toByteArray())
        val fileResponse = FileUploadResponse(
            fileUrl = "http://img/test.jpg", fileName = "test.jpg",
            fileSize = 4L, domain = "product"
        )

        whenever(fileUploadService.uploadFile(any())).thenReturn(fileResponse)

        val result = service.uploadProductImage(file)

        assertThat(result.imageUrl).isEqualTo("http://img/test.jpg")
        assertThat(result.fileName).isEqualTo("test.jpg")
        assertThat(result.fileSize).isEqualTo(4L)
    }
}
