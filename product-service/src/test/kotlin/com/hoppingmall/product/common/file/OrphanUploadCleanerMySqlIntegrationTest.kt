package com.hoppingmall.product.common.file

import com.hoppingmall.product.common.file.exception.FileException
import com.hoppingmall.product.common.file.storage.FileStorage
import com.hoppingmall.product.product.service.ProductImageLinker
import com.hoppingmall.product.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.io.InputStream
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.concurrent.CopyOnWriteArrayList

@DisplayName("주인 없는 업로드 정리 MySQL 통합 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class OrphanUploadCleanerMySqlIntegrationTest : MySqlIntegrationTestSupport() {

    @Autowired
    private lateinit var storageProperties: FileStorageProperties

    @Autowired
    private lateinit var productImageLinker: ProductImageLinker

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    private val storage = RecordingStorage()

    private lateinit var cleaner: OrphanUploadCleaner

    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 16, 12, 0)

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM uploaded_files")
        storage.deleted.clear()
        storage.failuresLeft = 0
        cleaner = OrphanUploadCleaner(jdbcTemplate, storage, storageProperties)
    }

    @Test
    fun 기한이_지난_연결_안_된_업로드를_저장소와_기록에서_지운다() {
        insert("product/images/old.jpg", "PENDING", now.minusHours(25))

        val deleted = cleaner.cleanUp(now)

        assertThat(deleted).isEqualTo(1)
        assertThat(storage.deleted).containsExactly("product/images/old.jpg")
        assertThat(count()).isZero()
    }

    @Test
    fun 상품에_붙은_업로드와_기한_안의_업로드는_남긴다() {
        insert("product/images/attached.jpg", "ATTACHED", now.minusDays(30))
        insert("product/images/recent.jpg", "PENDING", now.minusHours(23))

        val deleted = cleaner.cleanUp(now)

        assertThat(deleted).isZero()
        assertThat(storage.deleted).isEmpty()
        assertThat(count()).isEqualTo(2)
    }

    @Test
    fun 저장소_삭제가_실패하면_다음_주기에_다시_지운다() {
        insert("product/images/old.jpg", "PENDING", now.minusHours(25))
        storage.failuresLeft = 1

        assertThat(cleaner.cleanUp(now)).isZero()
        assertThat(status("product/images/old.jpg")).isEqualTo("DELETING")

        assertThat(cleaner.cleanUp(now)).isEqualTo(1)
        assertThat(storage.deleted).containsExactly("product/images/old.jpg")
        assertThat(count()).isZero()
    }

    @Test
    fun 상품_연결이_잡고_있는_업로드는_연결이_끝난_뒤_정리하지_않는다() {
        insert("product/images/race.jpg", "PENDING", now.minusHours(25))

        val result = runWhileHolding(
            holdingChanges = { connection ->
                connection.prepareStatement("SELECT id FROM uploaded_files WHERE object_key = ? FOR UPDATE").use {
                    it.setString(1, "product/images/race.jpg")
                    it.executeQuery().close()
                }
                connection.prepareStatement("UPDATE uploaded_files SET status = 'ATTACHED' WHERE object_key = ?").use {
                    it.setString(1, "product/images/race.jpg")
                    it.executeUpdate()
                }
            },
            actionName = "주인 없는 업로드 정리",
            waitTable = "uploaded_files"
        ) {
            cleaner.cleanUp(now)
        }

        assertThat(result.isSuccess).isTrue()
        assertThat(status("product/images/race.jpg")).isEqualTo("ATTACHED")
        assertThat(storage.deleted).isEmpty()
    }

    @Test
    fun 정리_중으로_바뀐_업로드는_상품에_연결하지_않는다() {
        insert("product/images/old.jpg", "PENDING", now.minusHours(25))
        storage.failuresLeft = 1
        cleaner.cleanUp(now)

        assertThatThrownBy {
            TransactionTemplate(transactionManager).executeWithoutResult {
                productImageLinker.link(SELLER_ID, listOf(storageProperties.publicUrl("product/images/old.jpg")))
            }
        }.isInstanceOf(FileException::class.java)
            .extracting("errorCode.code").isEqualTo("F009")
    }

    @Test
    fun 연결하면_기한이_지나도_정리하지_않는다() {
        insert("product/images/linked.jpg", "PENDING", now.minusHours(25))

        TransactionTemplate(transactionManager).executeWithoutResult {
            productImageLinker.link(SELLER_ID, listOf(storageProperties.publicUrl("product/images/linked.jpg")))
        }

        assertThat(cleaner.cleanUp(now)).isZero()
        assertThat(status("product/images/linked.jpg")).isEqualTo("ATTACHED")
    }

    private fun insert(key: String, status: String, createdAt: LocalDateTime) {
        jdbcTemplate.update(
            """
            INSERT INTO uploaded_files (object_key, uploader_id, uploader_role, content_type, file_size, status, created_at)
            VALUES (?, ?, 'SELLER', 'image/jpeg', 10, ?, ?)
            """.trimIndent(),
            key, SELLER_ID, status, Timestamp.valueOf(createdAt)
        )
    }

    private fun count(): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM uploaded_files", Int::class.java)!!

    private fun status(key: String): String =
        jdbcTemplate.queryForObject("SELECT status FROM uploaded_files WHERE object_key = ?", String::class.java, key)!!

    private class RecordingStorage : FileStorage {
        val deleted = CopyOnWriteArrayList<String>()

        @Volatile
        var failuresLeft = 0

        override fun put(key: String, content: InputStream, size: Long, contentType: String) = Unit

        override fun delete(key: String) {
            if (failuresLeft > 0) {
                failuresLeft--
                throw IllegalStateException("storage down")
            }
            deleted.add(key)
        }
    }

    companion object {
        private const val SELLER_ID = 5L
    }
}
