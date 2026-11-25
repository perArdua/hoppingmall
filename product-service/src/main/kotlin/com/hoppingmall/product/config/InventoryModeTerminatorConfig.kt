package com.hoppingmall.product.config

import com.hoppingmall.product.inventory.service.InventoryModeTerminator
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.system.exitProcess

@Configuration
class InventoryModeTerminatorConfig {

    @Bean
    fun inventoryModeTerminator(context: ConfigurableApplicationContext): InventoryModeTerminator =
        InventoryModeTerminator {
            Thread({
                SpringApplication.exit(context, ExitCodeGenerator { 1 })
                exitProcess(1)
            }, "inventory-mode-terminator").start()
        }
}
