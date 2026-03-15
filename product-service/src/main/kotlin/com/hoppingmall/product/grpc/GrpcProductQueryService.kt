package com.hoppingmall.product.grpc

import com.hoppingmall.product.product.domain.repository.ProductImageRepository
import com.hoppingmall.product.product.domain.repository.ProductRepository
import io.grpc.Status
import io.grpc.StatusException
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class GrpcProductQueryService(
    private val productRepository: ProductRepository,
    private val productImageRepository: ProductImageRepository
) : ProductQueryServiceGrpcKt.ProductQueryServiceCoroutineImplBase() {

    override suspend fun findProductById(request: ProductIdRequest): ProductResponse {
        val product = productRepository.findNullableById(request.productId)
            ?: throw StatusException(Status.NOT_FOUND)
        return productResponse {
            id = product.id!!
            name = product.name
            price = product.price.toPlainString()
            sellerId = product.sellerId
        }
    }

    override suspend fun findProductsByIds(request: ProductIdsRequest): ProductListResponse {
        val found = productRepository.findAllById(request.productIdsList)
        return productListResponse {
            products.addAll(
                found.map { product ->
                    productResponse {
                        id = product.id!!
                        name = product.name
                        price = product.price.toPlainString()
                        sellerId = product.sellerId
                    }
                }
            )
        }
    }

    override suspend fun findProductImageUrl(request: ProductIdRequest): ImageUrlResponse {
        val images = productImageRepository.findByProductIdOrderBySortOrder(request.productId)
        val first = images.firstOrNull()
        return imageUrlResponse {
            imageUrl = first?.imageUrl ?: ""
            found = first != null
        }
    }
}
