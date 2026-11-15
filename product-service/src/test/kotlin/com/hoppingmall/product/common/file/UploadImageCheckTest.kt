package com.hoppingmall.product.common.file

import com.hoppingmall.product.common.file.dto.FileUploadRequest
import com.hoppingmall.product.common.file.exception.FileException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.web.MockMultipartFile
import java.nio.file.Path

@DisplayName("업로드 이미지 확장자·내용 검사")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class UploadImageCheckTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var service: LocalFileUploadService

    @BeforeEach
    fun setUp() {
        val config = FileUploadConfig().apply { baseUploadDir = tempDir.toString() }
        service = LocalFileUploadService(config)
    }

    @Test
    fun 대문자_확장자의_JPEG_사진을_받는다() {
        val response = service.uploadFile(request("IMG_0001.JPG", JPEG))

        assertThat(response.fileName).endsWith(".jpg")
    }

    @Test
    fun 대문자_확장자의_PNG_사진을_받는다() {
        val response = service.uploadFile(request("Screenshot.PNG", PNG))

        assertThat(response.fileName).endsWith(".png")
    }

    @Test
    fun 네_가지_이미지_형식을_내용으로_판별해_받는다() {
        assertThat(service.uploadFile(request("a.jpeg", JPEG)).fileName).endsWith(".jpeg")
        assertThat(service.uploadFile(request("b.gif", GIF)).fileName).endsWith(".gif")
        assertThat(service.uploadFile(request("c.webp", WEBP)).fileName).endsWith(".webp")
    }

    @Test
    fun 이름만_이미지인_텍스트_파일을_거절한다() {
        assertThatThrownBy { service.uploadFile(request("evil.jpg", "<script>alert(1)</script>".toByteArray())) }
            .isInstanceOf(FileException::class.java)
            .extracting("errorCode.code").isEqualTo("F008")
    }

    @Test
    fun 확장자와_내용_형식이_다르면_거절한다() {
        assertThatThrownBy { service.uploadFile(request("photo.png", JPEG)) }
            .isInstanceOf(FileException::class.java)
            .extracting("errorCode.code").isEqualTo("F008")
    }

    @Test
    fun 머리만_있고_짧은_파일을_거절한다() {
        assertThatThrownBy { service.uploadFile(request("tiny.webp", "RIFF".toByteArray())) }
            .isInstanceOf(FileException::class.java)
            .extracting("errorCode.code").isEqualTo("F008")
    }

    private fun request(name: String, content: ByteArray) =
        FileUploadRequest(MockMultipartFile("image", name, "application/octet-stream", content), "product")

    companion object {
        private val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10, 'J'.code.toByte(), 'F'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 0, 1)
        private val PNG = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D)
        private val GIF = "GIF89a".toByteArray() + byteArrayOf(1, 0, 1, 0, 0, 0)
        private val WEBP = "RIFF".toByteArray() + byteArrayOf(0x24, 0, 0, 0) + "WEBPVP8 ".toByteArray()
    }
}
