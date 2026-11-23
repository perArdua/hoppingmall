package com.hoppingmall.product.product.service

import com.hoppingmall.product.common.file.FileStorageProperties
import com.hoppingmall.product.common.file.UploadedImageLinker
import com.hoppingmall.product.common.file.domain.UploadStatus
import com.hoppingmall.product.common.file.domain.UploadedFile
import com.hoppingmall.product.common.file.domain.repository.UploadedFileRepository
import com.hoppingmall.product.common.file.exception.FileException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@DisplayName("ProductImageLinker")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ProductImageLinkerTest {

    @Mock
    private lateinit var uploadedFileRepository: UploadedFileRepository

    private lateinit var linker: ProductImageLinker

    private val properties = FileStorageProperties().apply { publicBaseUrl = BASE }

    @BeforeEach
    fun setUp() {
        linker = ProductImageLinker(UploadedImageLinker(uploadedFileRepository, properties), properties)
    }

    @Test
    fun 이미지가_없으면_기본_이미지_주소_하나를_준다() {
        assertThat(linker.link(SELLER_ID, null)).containsExactly("$BASE/product/images/default-product.jpg")
        assertThat(linker.link(SELLER_ID, emptyList())).containsExactly("$BASE/product/images/default-product.jpg")
        verifyNoInteractions(uploadedFileRepository)
    }

    @Test
    fun 저장소_밖의_주소는_F009() {
        assertCode { linker.link(SELLER_ID, listOf("https://tracker.example.com/pixel.gif")) }
        assertCode { linker.link(SELLER_ID, listOf("/tmp/hoppingmall/product/images/a.jpg")) }
    }

    @Test
    fun 업로드_기록이_없는_키는_F009() {
        whenever(uploadedFileRepository.findAllByObjectKeyInForUpdate(any())).thenReturn(emptyList())

        assertCode { linker.link(SELLER_ID, listOf("$BASE/$KEY")) }
    }

    @Test
    fun 다른_판매자가_올린_파일은_F009() {
        whenever(uploadedFileRepository.findAllByObjectKeyInForUpdate(any())).thenReturn(listOf(file(uploaderId = 99L)))

        assertCode { linker.link(SELLER_ID, listOf("$BASE/$KEY")) }
    }

    @Test
    fun 정리_중인_파일은_F009() {
        val deleting = file().also { it.status = UploadStatus.DELETING }
        whenever(uploadedFileRepository.findAllByObjectKeyInForUpdate(any())).thenReturn(listOf(deleting))

        assertCode { linker.link(SELLER_ID, listOf("$BASE/$KEY")) }
    }

    @Test
    fun 자기가_올린_파일을_연결하면_연결됨으로_바뀐다() {
        val uploaded = file()
        whenever(uploadedFileRepository.findAllByObjectKeyInForUpdate(listOf(KEY))).thenReturn(listOf(uploaded))

        val urls = linker.link(SELLER_ID, listOf("$BASE/$KEY", "$BASE/$KEY"))

        assertThat(urls).containsExactly("$BASE/$KEY", "$BASE/$KEY")
        assertThat(uploaded.status).isEqualTo(UploadStatus.ATTACHED)
        assertThat(uploaded.attachedAt).isNotNull()
    }

    @Test
    fun 관리자가_올린_파일은_어느_판매자_상품에도_연결된다() {
        val uploaded = file(uploaderId = 1L, role = "ADMIN")
        whenever(uploadedFileRepository.findAllByObjectKeyInForUpdate(any())).thenReturn(listOf(uploaded))

        linker.link(SELLER_ID, listOf("$BASE/$KEY"))

        assertThat(uploaded.status).isEqualTo(UploadStatus.ATTACHED)
    }

    @Test
    fun 이미_상품에_붙은_주소와_기본_이미지는_다시_확인하지_않는다() {
        val current = listOf("https://legacy.example.com/a.jpg", "$BASE/product/images/old.jpg")

        val urls = linker.link(SELLER_ID, current + "$BASE/product/images/default-product.jpg", current)

        assertThat(urls).hasSize(3)
        verifyNoInteractions(uploadedFileRepository)
    }

    private fun assertCode(action: () -> Unit) {
        assertThatThrownBy { action() }
            .isInstanceOf(FileException::class.java)
            .extracting("errorCode.code").isEqualTo("F009")
    }

    private fun file(uploaderId: Long = SELLER_ID, role: String = "SELLER") =
        UploadedFile.pending(KEY, uploaderId, role, "image/jpeg", 10L)

    companion object {
        private const val BASE = "http://cdn.test/bucket"
        private const val KEY = "product/images/a.jpg"
        private const val SELLER_ID = 3L
    }
}
