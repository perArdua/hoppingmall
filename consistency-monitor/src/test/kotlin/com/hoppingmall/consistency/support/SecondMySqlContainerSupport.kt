package com.hoppingmall.consistency.support

import org.testcontainers.containers.MySQLContainer

object SecondMySqlContainerSupport {

    private val container: MySQLContainer<*> by lazy {
        MySQLContainer("mysql:8.0.44")
            .withUsername("root")
            .withPassword("test")
            .also { it.start() }
    }

    fun jdbcUrl(database: String): String =
        "jdbc:mysql://${container.host}:${container.getMappedPort(MySQLContainer.MYSQL_PORT)}/$database" +
            "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true"

    val username: String get() = container.username

    val password: String get() = container.password
}
