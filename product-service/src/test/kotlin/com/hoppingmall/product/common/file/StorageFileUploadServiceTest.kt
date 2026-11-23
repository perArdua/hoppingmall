package com.hoppingmall.product.common.file

import com.hoppingmall.product.common.file.domain.UploadStatus
import com.hoppingmall.product.common.file.domain.UploadedFile
import com.hoppingmall.product.common.file.domain.repository.UploadedFileRepository
import com.hoppingmall.product.common.file.dto.FileUploadRequest
import com.hoppingmall.product.common.file.exception.FileException
import com.hoppingmall.product.common.file.storage.FileStorage
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockMultipartFile
import software.amazon.awssdk.core.exception.SdkClientException
import java.io.InputStream

@DisplayName("StorageFileUploadService")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class StorageFileUploadServiceTest {

    @Mock
    private lateinit var fileStorage: FileStorage

    @Mock
    private lateinit var uploadedFileRepository: UploadedFileRepository

    private lateinit var service: StorageFileUploadService

    private val storageProperties = FileStorageProperties().apply { publicBaseUrl = "http://cdn.test/bucket/" }

    @BeforeEach
    fun setUp() {
        service = StorageFileUploadService(FileUploadConfig(), storageProperties, fileStorage, uploadedFileRepository)
    }

    @Test
    fun 올린_이미지의_주소는_서버_디스크_경로가_아니라_공개_주소다() {
        stubSave()

        val response = service.uploadFile(request("photo.jpg", JPEG))

        assertThat(response.fileUrl).startsWith("http://cdn.test/bucket/product/images/").endsWith(".jpg")
        assertThat(response.fileUrl).isEqualTo("http://cdn.test/bucket/product/images/${response.fileName}")
        assertThat(response.fileSize).isEqualTo(JPEG.size.toLong())
        verify(fileStorage).put(eq("product/images/${response.fileName}"), any<InputStream>(), eq(JPEG.size.toLong()), eq("image/jpeg"))
    }

    @Test
    fun 올린_사람과_내용_형식을_연결_전_기록으로_남긴다() {
        stubSave()

        service.uploadFile(request("Screenshot.PNG", PNG, uploaderId = 7L, role = "SELLER"))

        val captor = argumentCaptor<UploadedFile>()
        verify(uploadedFileRepository).save(captor.capture())
        val saved = captor.firstValue
        assertThat(saved.objectKey).startsWith("product/images/").endsWith(".png")
        assertThat(saved.uploaderId).isEqualTo(7L)
        assertThat(saved.uploaderRole).isEqualTo("SELLER")
        assertThat(saved.contentType).isEqualTo("image/png")
        assertThat(saved.status).isEqualTo(UploadStatus.PENDING)
    }

    @Test
    fun 저장소에_올리지_못하면_기록을_지우고_F007() {
        stubSave()
        doThrow(SdkClientException.create("down")).whenever(fileStorage).put(any(), any(), any(), any())

        assertThatThrownBy { service.uploadFile(request("photo.jpg", JPEG)) }
            .isInstanceOf(FileException::class.java)
            .extracting("errorCode.code").isEqualTo("F007")
        verify(uploadedFileRepository).delete(any())
    }

    @Test
    fun 대문자_확장자의_사진을_받는다() {
        stubSave()

        assertThat(service.uploadFile(request("IMG_0001.JPG", JPEG)).fileName).endsWith(".jpg")
        assertThat(service.uploadFile(request("b.gif", GIF)).fileName).endsWith(".gif")
        assertThat(service.uploadFile(request("c.WEBP", WEBP)).fileName).endsWith(".webp")
    }

    @Test
    fun 이름만_이미지인_파일은_저장하지_않고_F008() {
        assertThatThrownBy { service.uploadFile(request("evil.jpg", "<script>".toByteArray())) }
            .isInstanceOf(FileException::class.java)
            .extracting("errorCode.code").isEqualTo("F008")
        verify(uploadedFileRepository, never()).save(any())
    }

    @Test
    fun 확장자와_내용_형식이_다르면_F008() {
        assertCode(request("photo.png", JPEG), "F008")
    }

    @Test
    fun 빈_파일은_F001() {
        assertCode(request("a.jpg", ByteArray(0)), "F001")
    }

    @Test
    fun 파일명이_없으면_F003() {
        assertCode(request("", JPEG), "F003")
        assertCode(request("   ", JPEG), "F003")
    }

    @Test
    fun 허용되지_않은_확장자는_F004() {
        assertCode(request("a.exe", JPEG), "F004")
        assertCode(request("noext", JPEG), "F004")
    }

    @Test
    fun 크기_상한을_넘으면_F002() {
        val big = JPEG + ByteArray((5 * 1024 * 1024) - JPEG.size + 1)
        assertCode(request("big.jpg", big), "F002")
    }

    @Test
    fun 허용되지_않은_도메인은_F005() {
        val request = FileUploadRequest(MockMultipartFile("file", "a.jpg", null, JPEG), "user", 1L, "SELLER")
        assertCode(request, "F005")
    }

    @Test
    fun 구매자는_리뷰_이미지를_올린다() {
        stubSave()

        val response = service.uploadFile(FileUploadRequest(MockMultipartFile("file", "a.jpg", null, JPEG), "review", 5L, "BUYER"))

        assertThat(response.fileUrl).startsWith("http://cdn.test/bucket/review/images/")
        val captor = argumentCaptor<UploadedFile>()
        verify(uploadedFileRepository).save(captor.capture())
        assertThat(captor.firstValue.uploaderId).isEqualTo(5L)
        assertThat(captor.firstValue.uploaderRole).isEqualTo("BUYER")
    }

    @Test
    fun 구매자가_상품_도메인에_올리면_F005() {
        assertCode(request("a.jpg", JPEG, uploaderId = 5L, role = "BUYER"), "F005")
        verifyNoInteractions(uploadedFileRepository)
    }

    private fun assertCode(request: FileUploadRequest, code: String) {
        assertThatThrownBy { service.uploadFile(request) }
            .isInstanceOf(FileException::class.java)
            .extracting("errorCode.code").isEqualTo(code)
    }

    private fun stubSave() {
        whenever(uploadedFileRepository.save(any<UploadedFile>())).thenAnswer { it.arguments[0] }
    }

    private fun request(name: String, content: ByteArray, uploaderId: Long = 1L, role: String = "SELLER") =
        FileUploadRequest(MockMultipartFile("image", name, "application/octet-stream", content), "product", uploaderId, role)

    companion object {
        private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10, 0x4A, 0x46, 0x49, 0x46, 0, 1)
        private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D)
        private val GIF = "GIF89a".toByteArray() + byteArrayOf(1, 0, 1, 0, 0, 0)
        private val WEBP = "RIFF".toByteArray() + byteArrayOf(0x24, 0, 0, 0) + "WEBPVP8 ".toByteArray()
    }
}
