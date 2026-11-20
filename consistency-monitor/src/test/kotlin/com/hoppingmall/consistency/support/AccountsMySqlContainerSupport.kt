package com.hoppingmall.consistency.support

import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.MountableFile
import java.io.File
import java.util.UUID

object AccountsMySqlContainerSupport {

    const val ROOT_PASSWORD = "test"

    val passwords: Map<String, String> = mapOf(
        "USER_DB_PASSWORD" to "u'se\\r-${UUID.randomUUID()}",
        "PRODUCT_DB_PASSWORD" to UUID.randomUUID().toString(),
        "ORDER_DB_PASSWORD" to UUID.randomUUID().toString(),
        "PAYMENT_DB_PASSWORD" to UUID.randomUUID().toString(),
        "NOTIFICATION_DB_PASSWORD" to UUID.randomUUID().toString(),
        "SETTLEMENT_DB_PASSWORD" to UUID.randomUUID().toString(),
        "CONSISTENCY_MONITOR_DB_PASSWORD" to UUID.randomUUID().toString()
    )

    val container: MySQLContainer<*> by lazy {
        MySQLContainer("mysql:8.0.44")
            .withUsername("root")
            .withPassword(ROOT_PASSWORD)
            .withCopyFileToContainer(initFile("init-db.sql"), "/docker-entrypoint-initdb.d/init-db.sql")
            .withCopyFileToContainer(initFile("init-accounts.sh"), "/docker-entrypoint-initdb.d/init-accounts.sh")
            .withEnv(passwords)
            .also { it.start() }
    }

    fun jdbcUrl(database: String): String =
        "jdbc:mysql://${container.host}:${container.getMappedPort(MySQLContainer.MYSQL_PORT)}/$database" +
            "?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true"

    private fun initFile(name: String): MountableFile =
        MountableFile.forHostPath(File("../docker/$name").absolutePath, 0b111_101_101)
}
