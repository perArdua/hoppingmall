package com.hoppingmall.product.support

import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.MySQLContainer

object MySqlContainerSupport {

    private val container: MySQLContainer<*> by lazy {
        MySQLContainer("mysql:8.0.44")
            .withUsername("root")
            .withPassword("test")
            .withCommand("--innodb-lock-wait-timeout=20")
            .also { it.start() }
    }

    fun jdbcUrl(database: String): String =
        "jdbc:mysql://${container.host}:${container.getMappedPort(MySQLContainer.MYSQL_PORT)}/$database" +
            "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true"

    fun register(registry: DynamicPropertyRegistry, database: String) {
        registry.add("spring.datasource.url") { jdbcUrl(database) }
        registry.add("spring.datasource.username") { container.username }
        registry.add("spring.datasource.password") { container.password }
        registry.add("spring.datasource.driver-class-name") { "com.mysql.cj.jdbc.Driver" }
        registry.add("spring.jpa.database-platform") { "org.hibernate.dialect.MySQLDialect" }
    }
}
