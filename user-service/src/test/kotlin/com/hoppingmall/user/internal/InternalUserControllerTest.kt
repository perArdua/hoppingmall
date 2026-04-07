package com.hoppingmall.user.internal

import com.hoppingmall.user.domain.repository.SellerRepository
import com.hoppingmall.user.support.fixture.fixture
import com.hoppingmall.user.support.withId
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
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExtendWith(MockitoExtension::class)
@DisplayName("InternalUserController 단위 테스트")
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

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(5L, response.body?.id)
        assertEquals(1L, response.body?.userId)
        assertEquals("123-45-67890", response.body?.businessNumber)
        assertEquals("PENDING", response.body?.approvalStatus)
    }

    @Test
    fun 판매자가_없으면_404를_반환한다() {
        whenever(sellerRepository.findNullableByUserId(2L)).thenReturn(null)

        val response = internalUserController.getSellerByUserId(2L)

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertNull(response.body)
    }
}
