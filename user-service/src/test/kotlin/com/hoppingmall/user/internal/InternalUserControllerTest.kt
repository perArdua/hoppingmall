package com.hoppingmall.user.internal

import com.hoppingmall.user.domain.repository.SellerRepository
import com.hoppingmall.user.support.fixture.fixture
import com.hoppingmall.user.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus

@ExtendWith(MockitoExtension::class)
@DisplayName("InternalUserController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class InternalUserControllerTest {

    @Mock
    private lateinit var sellerRepository: SellerRepository

    @InjectMocks
    private lateinit var internalUserController: InternalUserController

    @Test
    fun 판매자가_있으면_SellerResponse를_반환한다() {
        val seller = com.hoppingmall.user.domain.Seller.fixture().withId(5L)
        whenever(sellerRepository.findNullableByUserId(1L)).thenReturn(seller)

        val response = internalUserController.getSellerByUserId(1L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.id).isEqualTo(5L)
        assertThat(response.body?.userId).isEqualTo(1L)
        assertThat(response.body?.businessNumber).isEqualTo("123-45-67890")
        assertThat(response.body?.approvalStatus).isEqualTo("PENDING")
    }

    @Test
    fun 판매자가_없으면_404를_반환한다() {
        whenever(sellerRepository.findNullableByUserId(2L)).thenReturn(null)

        val response = internalUserController.getSellerByUserId(2L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body).isNull()
    }
}
