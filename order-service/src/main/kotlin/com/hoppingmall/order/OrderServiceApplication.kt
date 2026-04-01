package com.hoppingmall.order

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(scanBasePackages = ["com.hoppingmall.order", "com.hoppingmall.common", "com.hoppingmall.idempotency", "com.hoppingmall.dlq"])
@EnableAsync
@EnableScheduling
class OrderServiceApplication

fun main(args: Array<String>) {
    runApplication<OrderServiceApplication>(*args)
}
