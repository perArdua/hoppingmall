package com.hoppingmall.mall.global.file.controller

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

@DisplayName("FileUploadController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class FileUploadControllerTest {

    private val fileUploadService: FileUploadService = mock()
    private val fileUploadController = FileUploadController(fileUploadService)

    @Nested
    @DisplayName("uploadFile")
    inner class UploadFile {
        
        @Nested
        @DisplayName("파일 업로드 성공 케이스")
        inner class FileUploadSuccess {
            
            @Test
            fun 파일_업로드_성공() {
                // Data
                val file = mock<org.springframework.web.multipart.MultipartFile>()
                val domain = "product"
                val fileUploadResponse = FileUploadResponse(
                    fileUrl = "https://example.com/files/uploaded-file.jpg",
                    fileName = "uploaded-file.jpg",
                    fileSize = 2048L,
                    domain = domain
                )

                // Context
                whenever(fileUploadService.uploadFile(any())).thenReturn(fileUploadResponse)

                // Interaction
                val result = fileUploadController.uploadFile(file, domain)

                // Assertions
                assertThat(result).isNotNull()
                assertThat(result.code).isEqualTo("SUCCESS")
                assertThat(result.message).isEqualTo("성공")
                assertThat(result.data).isNotNull()
                assertThat(result.data!!.fileUrl).isEqualTo(fileUploadResponse.fileUrl)
                assertThat(result.data!!.fileName).isEqualTo(fileUploadResponse.fileName)
                assertThat(result.data!!.fileSize).isEqualTo(fileUploadResponse.fileSize)
                
                verify(fileUploadService).uploadFile(any())
            }
        }
    }
} 