package com.hoppingmall.mall.global.file.service

import com.hoppingmall.mall.global.file.config.FileUploadConfig
import com.hoppingmall.mall.global.file.dto.request.FileUploadRequest
import com.hoppingmall.mall.global.file.exception.*
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.multipart.MultipartFile
import java.io.File

@DisplayName("FileUploadServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class FileUploadServiceImplTest {

    private val fileUploadConfig: FileUploadConfig = mock()
    private val fileUploadService = FileUploadServiceImpl(fileUploadConfig)

    @Nested
    @DisplayName("uploadFile")
    inner class UploadFile {
        
        @Nested
        @DisplayName("파일 검증 실패 케이스")
        inner class FileValidationFailure {
            
            @Test
            fun 빈_파일_업로드_시_예외_발생() {
                // Data
                val file = mock<MultipartFile>()
                val request = FileUploadRequest(file, "product")
                
                // Context
                whenever(file.isEmpty).thenReturn(true)

                // Interaction & Assertions
                assertThatThrownBy { fileUploadService.uploadFile(request) }
                    .isInstanceOf(EmptyFileException::class.java)
            }

            @Test
            fun 파일_크기_초과_시_예외_발생() {
                // Data
                val file = mock<MultipartFile>()
                val request = FileUploadRequest(file, "product")
                
                // Context
                whenever(fileUploadConfig.maxFileSize).thenReturn(5242880L) // 5MB (application.yml 설정값)
                whenever(file.isEmpty).thenReturn(false)
                whenever(file.size).thenReturn(6291456L) // 6MB (5MB 초과)

                // Interaction & Assertions
                assertThatThrownBy { fileUploadService.uploadFile(request) }
                    .isInstanceOf(FileSizeExceededException::class.java)
            }

            @Test
            fun 파일명이_없는_파일_업로드_시_예외_발생() {
                // Data
                val file = mock<MultipartFile>()
                val request = FileUploadRequest(file, "product")
                
                // Context
                whenever(fileUploadConfig.maxFileSize).thenReturn(5242880L) // 5MB (application.yml 설정값)
                whenever(file.isEmpty).thenReturn(false)
                whenever(file.size).thenReturn(1024L)
                whenever(file.originalFilename).thenReturn(null)

                // Interaction & Assertions
                assertThatThrownBy { fileUploadService.uploadFile(request) }
                    .isInstanceOf(InvalidFileNameException::class.java)
            }

            @Test
            fun 빈_파일명_업로드_시_예외_발생() {
                // Data
                val file = mock<MultipartFile>()
                val request = FileUploadRequest(file, "product")
                
                // Context
                whenever(fileUploadConfig.maxFileSize).thenReturn(5242880L) // 5MB (application.yml 설정값)
                whenever(file.isEmpty).thenReturn(false)
                whenever(file.size).thenReturn(1024L)
                whenever(file.originalFilename).thenReturn("")

                // Interaction & Assertions
                assertThatThrownBy { fileUploadService.uploadFile(request) }
                    .isInstanceOf(InvalidFileNameException::class.java)
            }

            @Test
            fun 공백_파일명_업로드_시_예외_발생() {
                // Data
                val file = mock<MultipartFile>()
                val request = FileUploadRequest(file, "product")
                
                // Context
                whenever(fileUploadConfig.maxFileSize).thenReturn(5242880L) // 5MB (application.yml 설정값)
                whenever(file.isEmpty).thenReturn(false)
                whenever(file.size).thenReturn(1024L)
                whenever(file.originalFilename).thenReturn("   ")

                // Interaction & Assertions
                assertThatThrownBy { fileUploadService.uploadFile(request) }
                    .isInstanceOf(InvalidFileNameException::class.java)
            }

            @Test
            fun 지원하지_않는_파일_타입_업로드_시_예외_발생() {
                // Data
                val file = mock<MultipartFile>()
                val request = FileUploadRequest(file, "product")
                
                // Context
                whenever(fileUploadConfig.maxFileSize).thenReturn(5242880L) // 5MB (application.yml 설정값)
                whenever(fileUploadConfig.allowedExtensions).thenReturn(setOf("jpg", "jpeg", "png", "gif", "webp"))
                whenever(file.isEmpty).thenReturn(false)
                whenever(file.size).thenReturn(1024L)
                whenever(file.originalFilename).thenReturn("test.txt")

                // Interaction & Assertions
                assertThatThrownBy { fileUploadService.uploadFile(request) }
                    .isInstanceOf(UnsupportedFileTypeException::class.java)
            }

            @Test
            fun 확장자가_없는_파일_업로드_시_예외_발생() {
                // Data
                val file = mock<MultipartFile>()
                val request = FileUploadRequest(file, "product")
                
                // Context
                whenever(fileUploadConfig.maxFileSize).thenReturn(5242880L) // 5MB (application.yml 설정값)
                whenever(fileUploadConfig.allowedExtensions).thenReturn(setOf("jpg", "jpeg", "png", "gif", "webp"))
                whenever(file.isEmpty).thenReturn(false)
                whenever(file.size).thenReturn(1024L)
                whenever(file.originalFilename).thenReturn("testfile")

                // Interaction & Assertions
                assertThatThrownBy { fileUploadService.uploadFile(request) }
                    .isInstanceOf(UnsupportedFileTypeException::class.java)
            }

            @Test
            fun 지원하지_않는_도메인_업로드_시_예외_발생() {
                // Data
                val file = mock<MultipartFile>()
                val request = FileUploadRequest(file, "invalid-domain")
                
                // Context
                whenever(fileUploadConfig.maxFileSize).thenReturn(5242880L) // 5MB (application.yml 설정값)
                whenever(fileUploadConfig.allowedExtensions).thenReturn(setOf("jpg", "jpeg", "png", "gif", "webp"))
                whenever(fileUploadConfig.allowedDomains).thenReturn(setOf("product"))
                whenever(file.isEmpty).thenReturn(false)
                whenever(file.size).thenReturn(1024L)
                whenever(file.originalFilename).thenReturn("test.jpg")

                // Interaction & Assertions
                assertThatThrownBy { fileUploadService.uploadFile(request) }
                    .isInstanceOf(UnsupportedDomainException::class.java)
            }
        }

        @Nested
        @DisplayName("파일 검증 성공 케이스")
        inner class FileValidationSuccess {
            
            @Test
            fun 파일_크기_제한_경계값_테스트() {
                // Data
                val file = mock<MultipartFile>()
                val request = FileUploadRequest(file, "product")
                
                // Context
                whenever(fileUploadConfig.maxFileSize).thenReturn(5242880L) // 5MB (application.yml 설정값)
                whenever(fileUploadConfig.allowedExtensions).thenReturn(setOf("jpg", "jpeg", "png", "gif", "webp"))
                whenever(fileUploadConfig.allowedDomains).thenReturn(setOf("product"))
                whenever(file.isEmpty).thenReturn(false)
                whenever(file.size).thenReturn(5242880L) // 정확히 5MB (경계값)
                whenever(file.originalFilename).thenReturn("test.jpg")
                doNothing().`when`(file).transferTo(any<File>())

                // Interaction & Assertions
                assertThatCode { fileUploadService.uploadFile(request) }
                    .doesNotThrowAnyException()
            }

            @Test
            fun 허용된_확장자_목록_테스트() {
                // Data
                val file = mock<MultipartFile>()
                val request = FileUploadRequest(file, "product")
                
                // Context
                whenever(fileUploadConfig.maxFileSize).thenReturn(5242880L) // 5MB (application.yml 설정값)
                whenever(fileUploadConfig.allowedExtensions).thenReturn(setOf("jpg", "jpeg", "png", "gif", "webp"))
                whenever(fileUploadConfig.allowedDomains).thenReturn(setOf("product"))
                whenever(file.isEmpty).thenReturn(false)
                whenever(file.size).thenReturn(1024L)
                whenever(file.originalFilename).thenReturn("test.webp") // webp 확장자 테스트
                doNothing().`when`(file).transferTo(any<File>())

                // Interaction & Assertions
                assertThatCode { fileUploadService.uploadFile(request) }
                    .doesNotThrowAnyException()
            }
        }
    }
} 