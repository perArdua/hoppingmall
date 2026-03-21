package com.hoppingmall.order.grpc

import com.hoppingmall.order.port.ProductInfo
import com.hoppingmall.order.port.ProductQueryPort
import com.hoppingmall.product.grpc.ProductIdRequest
import com.hoppingmall.product.grpc.ProductIdsRequest
import com.hoppingmall.product.grpc.ProductQueryServiceGrpc
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import io.github.resilience4j.retry.annotation.Retry
import io.grpc.Status
import io.grpc.StatusRuntimeException
import net.devh.boot.grpc.client.inject.GrpcClient
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
@Profile("grpc")
class GrpcProductQueryAdapter(
    @GrpcClient("product-service") private val stub: ProductQueryServiceGrpc.ProductQueryServiceBlockingStub
) : ProductQueryPort {

    private val log = LoggerFactory.getLogger(GrpcProductQueryAdapter::class.java)

    @CircuitBreaker(name = "product-query", fallbackMethod = "findProductByIdFallback")
    @Retry(name = "product-query")
    override fun findProductById(productId: Long): ProductInfo? {
        val response = stub.findProductById(
            ProductIdRequest.newBuilder().setProductId(productId).build()
        )
        return ProductInfo(
            id = response.id,
            name = response.name,
            price = response.price.toBigDecimalOrNull() ?: BigDecimal.ZERO,
            sellerId = response.sellerId,
            imageUrl = response.imageUrl.takeIf { it.isNotBlank() }
        )
    }

    @CircuitBreaker(name = "product-query", fallbackMethod = "findProductsByIdsFallback")
    @Retry(name = "product-query")
    override fun findProductsByIds(productIds: List<Long>): List<ProductInfo> {
        val response = stub.findProductsByIds(
            ProductIdsRequest.newBuilder().addAllProductIds(productIds).build()
        )
        return response.productsList.map {
            ProductInfo(
                id = it.id,
                name = it.name,
                price = it.price.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                sellerId = it.sellerId
            )
        }
    }

    private fun findProductByIdFallback(productId: Long, e: Exception): ProductInfo? {
        if (e is StatusRuntimeException && e.status.code == Status.Code.NOT_FOUND) return null
        log.warn("CB fallback: 상품 조회 실패 productId=$productId", e)
        return null
    }

    private fun findProductsByIdsFallback(productIds: List<Long>, e: Exception): List<ProductInfo> {
        log.warn("CB fallback: 상품 목록 조회 실패 productIds=$productIds", e)
        return emptyList()
    }
}
