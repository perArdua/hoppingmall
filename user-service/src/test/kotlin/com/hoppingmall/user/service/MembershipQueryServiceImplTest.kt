package com.hoppingmall.user.service

import com.hoppingmall.user.domain.repository.MembershipRepository
import com.hoppingmall.user.exception.membership.MembershipNotFoundException
import com.hoppingmall.user.support.fixture.fixture
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

@ExtendWith(MockitoExtension::class)
@DisplayName("MembershipQueryServiceImpl 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MembershipQueryServiceImplTest {

    @Mock
    private lateinit var membershipRepository: MembershipRepository

    @InjectMocks
    private lateinit var membershipQueryService: MembershipQueryServiceImpl

    @Test
    fun getMembershipByUserId는_멤버십이_있으면_조회에_성공한다() {
        val membership = com.hoppingmall.user.domain.Membership.fixture(userId = 5L)
        whenever(membershipRepository.findByUserId(5L)).thenReturn(membership)

        val response = membershipQueryService.getMembershipByUserId(5L)

        assertEquals(5L, response.userId)
        assertEquals(membership.grade, response.grade)
    }

    @Test
    fun getMembershipByUserId는_멤버십이_없으면_예외가_발생한다() {
        whenever(membershipRepository.findByUserId(6L)).thenReturn(null)

        assertThrows<MembershipNotFoundException> {
            membershipQueryService.getMembershipByUserId(6L)
        }
    }
}
