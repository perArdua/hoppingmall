package com.hoppingmall.consistency.support

import org.flywaydb.core.Flyway
import java.io.File

object FlywaySchemas {

    val serviceOfSource = mapOf(
        "order" to "order-service",
        "payment" to "payment-service",
        "product" to "product-service",
        "user" to "user-service",
        "notification" to "notification-service"
    )

    private val createTable = Regex("""CREATE TABLE\s+(?:IF NOT EXISTS\s+)?`?(\w+)`?""", RegexOption.IGNORE_CASE)
    private val createTableBody = Regex("""CREATE TABLE\s+(?:IF NOT EXISTS\s+)?`?(\w+)`?([^;]*)""", RegexOption.IGNORE_CASE)
    private val inlineKey = Regex("""\b(?:KEY|INDEX)\s+`?(\w+)`?\s*\(""", RegexOption.IGNORE_CASE)
    private val createIndex = Regex("""CREATE\s+(?:UNIQUE\s+)?INDEX\s+`?(\w+)`?\s+ON\s+`?(\w+)`?""", RegexOption.IGNORE_CASE)
    private val alterAddKey = Regex(
        """ALTER TABLE\s+`?(\w+)`?\s+ADD\s+(?:CONSTRAINT\s+`?(\w+)`?\s+UNIQUE|(?:UNIQUE\s+)?(?:KEY|INDEX)\s+`?(\w+)`?)""",
        RegexOption.IGNORE_CASE
    )

    fun migrationDir(service: String): File = File("../$service/src/main/resources/db/migration").absoluteFile

    fun migrate(jdbcUrl: String, username: String, password: String, service: String): Int =
        Flyway.configure()
            .dataSource(jdbcUrl, username, password)
            .locations("filesystem:" + migrationDir(service).invariantSeparatorsPath)
            .load()
            .migrate()
            .migrationsExecuted

    fun tablesOf(service: String): Set<String> =
        migrationDir(service).listFiles { file -> file.name.endsWith(".sql") }!!
            .flatMap { file -> createTable.findAll(file.readText()).map { it.groupValues[1].lowercase() }.toList() }
            .toSet()

    fun indexesOf(service: String): Map<String, Set<String>> {
        val ddl = migrationDir(service).listFiles { file -> file.name.endsWith(".sql") }!!.joinToString("\n") { it.readText() }
        val inline = createTableBody.findAll(ddl).map { match ->
            match.groupValues[1].lowercase() to inlineKey.findAll(match.groupValues[2]).map { it.groupValues[1].lowercase() }.toSet()
        }
        val created = createIndex.findAll(ddl).map { it.groupValues[2].lowercase() to setOf(it.groupValues[1].lowercase()) }
        val altered = alterAddKey.findAll(ddl).map { match ->
            match.groupValues[1].lowercase() to setOf(match.groupValues[2].ifEmpty { match.groupValues[3] }.lowercase())
        }
        return (inline + created + altered)
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, names) -> names.flatten().toSet() + PRIMARY }
    }

    private const val PRIMARY = "primary"
}
