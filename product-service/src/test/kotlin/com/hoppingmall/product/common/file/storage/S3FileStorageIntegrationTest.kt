package com.hoppingmall.product.common.file.storage

import com.hoppingmall.product.common.file.FileStorageProperties
import com.hoppingmall.product.config.FileStorageConfig
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException

@DisplayName("S3 호환 저장소 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3FileStorageIntegrationTest {

    private val container = GenericContainer(S3_MOCK_IMAGE)
        .withExposedPorts(S3_PORT)
        .waitingFor(Wait.forHttp("/").forPort(S3_PORT).forStatusCode(200))

    private lateinit var s3Client: S3Client

    private lateinit var storage: S3FileStorage

    private val properties = FileStorageProperties()

    @BeforeAll
    fun startStorage() {
        container.start()
        properties.endpoint = "http://${container.host}:${container.getMappedPort(S3_PORT)}"
        properties.accessKey = "test-access"
        properties.secretKey = "test-secret-value"
        properties.bucket = BUCKET
        s3Client = FileStorageConfig().s3Client(properties)
        s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build())
        storage = S3FileStorage(s3Client, properties)
    }

    @AfterAll
    fun stopStorage() {
        s3Client.close()
        container.stop()
    }

    @Test
    fun 올린_파일을_형식과_캐시_설정과_함께_다시_읽는다() {
        val content = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)

        storage.put("product/images/a.jpg", content.inputStream(), content.size.toLong(), "image/jpeg")

        val stored = s3Client.getObjectAsBytes(GetObjectRequest.builder().bucket(BUCKET).key("product/images/a.jpg").build())
        assertThat(stored.asByteArray()).isEqualTo(content)
        assertThat(stored.response().contentType()).isEqualTo("image/jpeg")
        assertThat(stored.response().cacheControl()).isEqualTo("public, max-age=31536000, immutable")
    }

    @Test
    fun 지운_파일은_더_없고_없는_파일을_지워도_실패하지_않는다() {
        val content = "x".toByteArray()
        storage.put("product/images/b.png", content.inputStream(), 1L, "image/png")

        storage.delete("product/images/b.png")
        storage.delete("product/images/never.png")

        assertThatThrownBy {
            s3Client.headObject(HeadObjectRequest.builder().bucket(BUCKET).key("product/images/b.png").build())
        }.isInstanceOf(software.amazon.awssdk.services.s3.model.S3Exception::class.java)
        assertThatThrownBy {
            s3Client.getObject(GetObjectRequest.builder().bucket(BUCKET).key("product/images/b.png").build())
        }.isInstanceOf(NoSuchKeyException::class.java)
    }

    companion object {
        private const val S3_MOCK_IMAGE = "adobe/s3mock:5.2.3"
        private const val S3_PORT = 9090
        private const val BUCKET = "hoppingmall-product"
    }
}
