package com.hoppingmall.user.controller

import com.hoppingmall.common.ApiResponse
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.user.auth.service.AuthService
import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.dto.request.SignInRequest
import com.hoppingmall.user.dto.request.SignUpRequest
import com.hoppingmall.user.dto.request.UpdateUserRequest
import com.hoppingmall.user.dto.response.SignInResponse
import com.hoppingmall.user.dto.response.SignUpResponse
import com.hoppingmall.user.dto.response.UserProfileResponse
import com.hoppingmall.user.service.UserCommandService
import com.hoppingmall.user.service.UserQueryService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
@DisplayName("UserController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class UserControllerTest {

    @Mock
    private lateinit var userCommandService: UserCommandService

    @Mock
    private lateinit var userQueryService: UserQueryService

    @Mock
    private lateinit var authService: AuthService

    @InjectMocks
    private lateinit var userController: UserController

    @Test
    fun 회원가입_시_응답을_반환한다() {
        val request = SignUpRequest("test@example.com", "Password1234", "테스트", Role.SELLER)
        val expected = SignUpResponse(1L, "test@example.com", "테스트", Role.SELLER)
        whenever(userCommandService.signUp(request)).thenReturn(expected)

        val response = userController.signUp(request)

        assertThat(response).isEqualTo(ApiResponse.success(expected))
        verify(userCommandService).signUp(request)
    }

    @Test
    fun 로그인_시_응답을_반환한다() {
        val request = SignInRequest("test@example.com", "Password1234")
        val expected = SignInResponse("access-token", "refresh-token")
        whenever(authService.login(request)).thenReturn(expected)

        val response = userController.login(request)

        assertThat(response).isEqualTo(ApiResponse.success(expected))
        verify(authService).login(request)
    }

    @Test
    fun 내_프로필_조회_시_principal의_userId로_조회한다() {
        val principal = UserPrincipal.of(1L, Role.BUYER.name)
        val expected = UserProfileResponse(1L, "buyer@example.com", "구매자", Role.BUYER.name)
        whenever(userQueryService.getUserProfile(1L)).thenReturn(expected)

        val response = userController.getMyProfile(principal)

        assertThat(response).isEqualTo(ApiResponse.success(expected))
        verify(userQueryService).getUserProfile(1L)
    }

    @Test
    fun 프로필_수정_시_principal의_userId로_수정한다() {
        val request = UpdateUserRequest(name = "새이름", password = "NewPassword1234")
        val principal = UserPrincipal.of(2L, Role.SELLER.name)

        val response = userController.updateMyProfile(request, principal)

        assertThat(response).isEqualTo(ApiResponse.success(Unit))
        verify(userCommandService).updateUserProfile(2L, request)
    }

    @Test
    fun 프로필_수정_시_비밀번호가_없어도_수정한다() {
        val request = UpdateUserRequest(name = "이름만변경", password = null)
        val principal = UserPrincipal.of(3L, Role.BUYER.name)

        val response = userController.updateMyProfile(request, principal)

        assertThat(response).isEqualTo(ApiResponse.success(Unit))
        verify(userCommandService).updateUserProfile(3L, request)
    }
}
