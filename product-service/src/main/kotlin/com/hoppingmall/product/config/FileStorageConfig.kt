package com.hoppingmall.product.config

import com.hoppingmall.product.common.file.FileStorageProperties
import com.hoppingmall.product.common.file.storage.S3FileStorage
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.net.URI
import java.time.Duration

@Configuration
class FileStorageConfig {

    @Bean(destroyMethod = "close")
    fun fileStorage(properties: FileStorageProperties): S3FileStorage = S3FileStorage(s3Client(properties), properties)

    fun s3Client(properties: FileStorageProperties): S3Client {
        val builder = S3Client.builder()
            .region(Region.of(properties.region))
            .forcePathStyle(properties.pathStyleAccess)
            .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
            .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
            .credentialsProvider(credentialsProvider(properties))
            .httpClientBuilder(
                ApacheHttpClient.builder()
                    .connectionTimeout(Duration.ofMillis(properties.connectTimeoutMs))
                    .socketTimeout(Duration.ofMillis(properties.socketTimeoutMs))
            )
        if (properties.endpoint.isNotBlank()) {
            builder.endpointOverride(URI.create(properties.endpoint))
        }
        return builder.build()
    }

    private fun credentialsProvider(properties: FileStorageProperties): AwsCredentialsProvider =
        if (properties.accessKey.isNotBlank()) {
            StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey, properties.secretKey))
        } else {
            DefaultCredentialsProvider.builder().build()
        }
}
