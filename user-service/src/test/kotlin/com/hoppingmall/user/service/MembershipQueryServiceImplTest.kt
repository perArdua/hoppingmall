package com.hoppingmall.user.service

import com.hoppingmall.user.domain.repository.MembershipRepository
import com.hoppingmall.user.exception.membership.MembershipNotFoundException
import com.hoppingmall.user.support.fixture.fixture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
@DisplayName("MembershipQueryServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class MembershipQueryServiceImplTest {

    @Mock
    private lateinit var membershipRepository: MembershipRepository

    @InjectMocks
    private lateinit var membershipQueryService: MembershipQueryServiceImpl

    @Test
    fun 멤버십이_있으면_조회에_성공한다() {
        val membership = com.hoppingmall.user.domain.Membership.fixture(userId = 5L)
        whenever(membershipRepository.findByUserId(5L)).thenReturn(membership)

        val response = membershipQueryService.getMembershipByUserId(5L)

        assertThat(response.userId).isEqualTo(5L)
        assertThat(response.grade).isEqualTo(membership.grade)
    }

    @Test
    fun 멤버십이_없으면_예외가_발생한다() {
        whenever(membershipRepository.findByUserId(6L)).thenReturn(null)

        assertThatThrownBy { membershipQueryService.getMembershipByUserId(6L) }
            .isInstanceOf(MembershipNotFoundException::class.java)
    }
}
