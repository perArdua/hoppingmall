package com.hoppingmall.product

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(scanBasePackages = ["com.hoppingmall.product", "com.hoppingmall.common", "com.hoppingmall.idempotency", "com.hoppingmall.cache", "com.hoppingmall.dlq"])
class ProductServiceApplication

fun main(args: Array<String>) {
    runApplication<ProductServiceApplication>(*args)
}
