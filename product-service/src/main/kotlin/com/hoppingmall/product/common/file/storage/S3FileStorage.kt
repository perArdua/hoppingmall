package com.hoppingmall.product.common.file.storage

import com.hoppingmall.product.common.file.FileStorageProperties
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.io.InputStream

class S3FileStorage(
    private val s3Client: S3Client,
    private val properties: FileStorageProperties
) : FileStorage, AutoCloseable {

    override fun put(key: String, content: InputStream, size: Long, contentType: String) {
        val request = PutObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key)
            .contentType(contentType)
            .contentLength(size)
            .cacheControl(CACHE_CONTROL)
            .build()
        s3Client.putObject(request, RequestBody.fromInputStream(content, size))
    }

    override fun delete(key: String) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(properties.bucket).key(key).build())
    }

    override fun close() {
        s3Client.close()
    }

    companion object {
        private const val CACHE_CONTROL = "public, max-age=31536000, immutable"
    }
}
