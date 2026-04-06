package com.hoppingmall.product.common.file.controller

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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile

@DisplayName("FileUploadController")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class FileUploadControllerTest {

    @Mock
    private lateinit var fileUploadService: FileUploadService

    @InjectMocks
    private lateinit var controller: FileUploadController

    @Test
    fun 파일_업로드_성공() {
        val mockFile = MockMultipartFile("file", "test.jpg", "image/jpeg", "content".toByteArray())
        val expectedResponse = FileUploadResponse(
            fileUrl = "/uploads/product/images/uuid-test.jpg",
            fileName = "uuid-test.jpg",
            fileSize = 7L,
            domain = "product"
        )
        whenever(fileUploadService.uploadFile(any())).thenReturn(expectedResponse)

        val result = controller.uploadFile(mockFile, "product")

        assertThat(result.code).isEqualTo("SUCCESS")
        assertThat(result.message).isEqualTo("성공")
        assertThat(result.data).isNotNull
        assertThat(result.data!!.fileUrl).isEqualTo(expectedResponse.fileUrl)
        assertThat(result.data!!.fileName).isEqualTo(expectedResponse.fileName)
        assertThat(result.data!!.fileSize).isEqualTo(expectedResponse.fileSize)
        assertThat(result.data!!.domain).isEqualTo(expectedResponse.domain)
        verify(fileUploadService).uploadFile(any())
    }
}
