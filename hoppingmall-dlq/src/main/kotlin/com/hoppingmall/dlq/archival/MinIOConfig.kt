package com.hoppingmall.dlq.archival

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.NoSuchBucketException
import java.net.URI

@Configuration
@ConditionalOnProperty(name = ["dlq.archival.enabled"], havingValue = "true")
@EnableConfigurationProperties(DLQArchivalProperties::class)
class MinIOConfig(
    private val properties: DLQArchivalProperties
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun s3Client(): S3Client {
        val client = S3Client.builder()
            .endpointOverride(URI(properties.endpoint))
            .region(Region.of(properties.region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey, properties.secretKey)
                )
            )
            .forcePathStyle(true)
            .build()

        ensureBucketExists(client)
        return client
    }

    private fun ensureBucketExists(client: S3Client) {
        try {
            client.headBucket(
                HeadBucketRequest.builder()
                    .bucket(properties.bucket)
                    .build()
            )
            log.info("DLQ 아카이브 버킷 확인: {}", properties.bucket)
        } catch (e: NoSuchBucketException) {
            client.createBucket(
                CreateBucketRequest.builder()
                    .bucket(properties.bucket)
                    .build()
            )
            log.info("DLQ 아카이브 버킷 생성: {}", properties.bucket)
        } catch (e: Exception) {
            log.warn("DLQ 아카이브 버킷 확인 실패 (서비스 시작은 계속): {}", e.message)
        }
    }
}
