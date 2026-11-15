package com.hoppingmall.product.support

import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.LocalDateTime

object UploadedImageSupport {

    private const val PUBLIC_BASE_URL = "http://localhost:9000/hoppingmall-product"

    fun url(name: String): String = "$PUBLIC_BASE_URL/${key(name)}"

    fun register(jdbcTemplate: JdbcTemplate, names: List<String>): List<String> =
        names.map { name ->
            jdbcTemplate.update(
                """
                INSERT INTO uploaded_files (object_key, uploader_id, uploader_role, content_type, file_size, status, created_at)
                SELECT ?, 0, 'ADMIN', 'image/jpeg', 1, 'PENDING', ?
                WHERE NOT EXISTS (SELECT 1 FROM uploaded_files WHERE object_key = ?)
                """.trimIndent(),
                key(name), Timestamp.valueOf(LocalDateTime.now()), key(name)
            )
            url(name)
        }

    private fun key(name: String): String = "product/images/$name"
}
