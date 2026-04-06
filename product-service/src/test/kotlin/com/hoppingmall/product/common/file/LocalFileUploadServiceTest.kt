package com.hoppingmall.product.common.file

import com.hoppingmall.product.common.file.dto.FileUploadRequest
import com.hoppingmall.product.common.file.exception.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.web.multipart.MultipartFile

@DisplayName("LocalFileUploadService")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class LocalFileUploadServiceTest {

    @Mock
    private lateinit var fileUploadConfig: FileUploadConfig

    @Mock
    private lateinit var file: MultipartFile

    @InjectMocks
    private lateinit var service: LocalFileUploadService

    @Test
    fun 빈_파일_업로드_시_예외_발생() {
        whenever(file.isEmpty).thenReturn(true)
        val request = FileUploadRequest(file = file, domain = "product")
        assertThatThrownBy { service.uploadFile(request) }
            .isInstanceOf(EmptyFileException::class.java)
    }

    @Test
    fun 파일_크기_초과_시_예외_발생() {
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.originalFilename).thenReturn("test.jpg")
        whenever(fileUploadConfig.allowedExtensions).thenReturn(setOf("jpg", "jpeg", "png", "gif", "webp"))
        whenever(file.size).thenReturn(5L * 1024 * 1024 + 1)
        whenever(fileUploadConfig.maxFileSize).thenReturn(5L * 1024 * 1024)
        val request = FileUploadRequest(file = file, domain = "product")
        assertThatThrownBy { service.uploadFile(request) }
            .isInstanceOf(FileSizeExceededException::class.java)
    }

    @Test
    fun 파일명이_없는_파일_업로드_시_예외_발생() {
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.originalFilename).thenReturn(null)
        val request = FileUploadRequest(file = file, domain = "product")
        assertThatThrownBy { service.uploadFile(request) }
            .isInstanceOf(InvalidFileNameException::class.java)
    }

    @Test
    fun 빈_파일명_업로드_시_예외_발생() {
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.originalFilename).thenReturn("")
        val request = FileUploadRequest(file = file, domain = "product")
        assertThatThrownBy { service.uploadFile(request) }
            .isInstanceOf(InvalidFileNameException::class.java)
    }

    @Test
    fun 공백_파일명_업로드_시_예외_발생() {
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.originalFilename).thenReturn("   ")
        val request = FileUploadRequest(file = file, domain = "product")
        assertThatThrownBy { service.uploadFile(request) }
            .isInstanceOf(InvalidFileNameException::class.java)
    }

    @Test
    fun 지원하지_않는_파일_타입_업로드_시_예외_발생() {
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.originalFilename).thenReturn("test.exe")
        whenever(fileUploadConfig.allowedExtensions).thenReturn(setOf("jpg", "jpeg", "png", "gif", "webp"))
        val request = FileUploadRequest(file = file, domain = "product")
        assertThatThrownBy { service.uploadFile(request) }
            .isInstanceOf(UnsupportedFileTypeException::class.java)
    }

    @Test
    fun 확장자가_없는_파일_업로드_시_예외_발생() {
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.originalFilename).thenReturn("testfile")
        whenever(fileUploadConfig.allowedExtensions).thenReturn(setOf("jpg", "jpeg", "png", "gif", "webp"))
        val request = FileUploadRequest(file = file, domain = "product")
        assertThatThrownBy { service.uploadFile(request) }
            .isInstanceOf(UnsupportedFileTypeException::class.java)
    }

    @Test
    fun 지원하지_않는_도메인_업로드_시_예외_발생() {
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.originalFilename).thenReturn("test.jpg")
        whenever(fileUploadConfig.allowedExtensions).thenReturn(setOf("jpg", "jpeg", "png", "gif", "webp"))
        whenever(file.size).thenReturn(1024L)
        whenever(fileUploadConfig.maxFileSize).thenReturn(5L * 1024 * 1024)
        whenever(fileUploadConfig.allowedDomains).thenReturn(setOf("product"))
        val request = FileUploadRequest(file = file, domain = "invalid")
        assertThatThrownBy { service.uploadFile(request) }
            .isInstanceOf(UnsupportedDomainException::class.java)
    }

    @Test
    fun 파일_크기_제한_경계값_테스트() {
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.originalFilename).thenReturn("test.jpg")
        whenever(fileUploadConfig.allowedExtensions).thenReturn(setOf("jpg", "jpeg", "png", "gif", "webp"))
        whenever(file.size).thenReturn(5L * 1024 * 1024)
        whenever(fileUploadConfig.maxFileSize).thenReturn(5L * 1024 * 1024)
        whenever(fileUploadConfig.allowedDomains).thenReturn(setOf("product"))
        whenever(fileUploadConfig.baseUploadDir).thenReturn(System.getProperty("java.io.tmpdir"))
        whenever(fileUploadConfig.subDirectory).thenReturn("images")
        val request = FileUploadRequest(file = file, domain = "product")
        val response = service.uploadFile(request)
        assertThat(response.domain).isEqualTo("product")
        assertThat(response.fileSize).isEqualTo(5L * 1024 * 1024)
        assertThat(response.fileName).endsWith(".jpg")
    }

    @Test
    fun 허용된_확장자_목록_테스트() {
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.originalFilename).thenReturn("image.webp")
        whenever(fileUploadConfig.allowedExtensions).thenReturn(setOf("jpg", "jpeg", "png", "gif", "webp"))
        whenever(file.size).thenReturn(1024L)
        whenever(fileUploadConfig.maxFileSize).thenReturn(5L * 1024 * 1024)
        whenever(fileUploadConfig.allowedDomains).thenReturn(setOf("product"))
        whenever(fileUploadConfig.baseUploadDir).thenReturn(System.getProperty("java.io.tmpdir"))
        whenever(fileUploadConfig.subDirectory).thenReturn("images")
        val request = FileUploadRequest(file = file, domain = "product")
        val response = service.uploadFile(request)
        assertThat(response.domain).isEqualTo("product")
        assertThat(response.fileName).endsWith(".webp")
    }

    @Test
    fun 파일_업로드_성공_시_응답을_반환한다() {
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.originalFilename).thenReturn("photo.png")
        whenever(fileUploadConfig.allowedExtensions).thenReturn(setOf("jpg", "jpeg", "png", "gif", "webp"))
        whenever(file.size).thenReturn(2048L)
        whenever(fileUploadConfig.maxFileSize).thenReturn(5L * 1024 * 1024)
        whenever(fileUploadConfig.allowedDomains).thenReturn(setOf("product"))
        whenever(fileUploadConfig.baseUploadDir).thenReturn(System.getProperty("java.io.tmpdir"))
        whenever(fileUploadConfig.subDirectory).thenReturn("images")
        val request = FileUploadRequest(file = file, domain = "product")
        val response = service.uploadFile(request)
        assertThat(response.fileUrl).contains("product")
        assertThat(response.fileUrl).contains("images")
        assertThat(response.fileName).endsWith(".png")
        assertThat(response.fileSize).isEqualTo(2048L)
        assertThat(response.domain).isEqualTo("product")
    }
}
