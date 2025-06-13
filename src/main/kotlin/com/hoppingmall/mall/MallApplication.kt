package com.hoppingmall.mall

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing

@SpringBootApplication
@EnableJpaAuditing
class MallApplication

fun main(args: Array<String>) {
	runApplication<MallApplication>(*args)
}
