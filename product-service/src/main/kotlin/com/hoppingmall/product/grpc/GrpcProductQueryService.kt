package com.hoppingmall.product.grpc

import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.product.domain.repository.ProductImageRepository
import com.hoppingmall.product.product.domain.repository.ProductRepository
import io.grpc.Status
import io.grpc.StatusException
import net.devh.boot.grpc.server.service.GrpcService

@GrpcService
class GrpcProductQueryService(
    private val productRepository: ProductRepository,
    private val productImageRepository: ProductImageRepository,
    private val inventoryRepository: InventoryRepository
) : ProductQueryServiceGrpcKt.ProductQueryServiceCoroutineImplBase() {

    override suspend fun findProductById(request: ProductIdRequest): ProductResponse {
        val product = productRepository.findNullableById(request.productId)
            ?: throw StatusException(Status.NOT_FOUND)
        val firstImageUrl = productImageRepository.findByProductIdOrderBySortOrder(request.productId)
            .firstOrNull()?.imageUrl ?: ""
        return productResponse {
            id = product.id!!
            name = product.name
            price = product.price.toPlainString()
            sellerId = product.sellerId
            imageUrl = firstImageUrl
            status = product.status.name
            stockQuantity = inventoryRepository.findByProductId(request.productId)?.stockQuantity ?: 0
        }
    }

    override suspend fun findProductsByIds(request: ProductIdsRequest): ProductListResponse {
        val found = productRepository.findAllById(request.productIdsList)
        val stocks = inventoryRepository.findAllByProductIdIn(request.productIdsList).associate { it.productId to it.stockQuantity }
        return productListResponse {
            products.addAll(
                found.map { product ->
                    productResponse {
                        id = product.id!!
                        name = product.name
                        price = product.price.toPlainString()
                        sellerId = product.sellerId
                        status = product.status.name
                        stockQuantity = stocks[product.id] ?: 0
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
