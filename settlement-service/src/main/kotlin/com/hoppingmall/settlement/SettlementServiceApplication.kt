package com.hoppingmall.settlement

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(scanBasePackages = ["com.hoppingmall.settlement", "com.hoppingmall.common"])
@EnableScheduling
class SettlementServiceApplication

fun main(args: Array<String>) {
    runApplication<SettlementServiceApplication>(*args)
}
