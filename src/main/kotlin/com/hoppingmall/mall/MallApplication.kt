package com.hoppingmall.mall

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MallApplication

fun main(args: Array<String>) {
	runApplication<MallApplication>(*args)
}
