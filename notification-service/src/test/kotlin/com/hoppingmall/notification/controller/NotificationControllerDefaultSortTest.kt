package com.hoppingmall.notification.controller

import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.notification.dto.response.NotificationResponse
import com.hoppingmall.notification.service.NotificationCommandService
import com.hoppingmall.notification.service.NotificationQueryService
import com.hoppingmall.notification.service.NotificationSseService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.domain.Sort
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
@DisplayName("NotificationController 목록 기본 정렬")
@DisplayNameGeneration(ReplaceUnderscores::class)
class NotificationControllerDefaultSortTest {

    @Mock
    private lateinit var notificationQueryService: NotificationQueryService

    @Mock
    private lateinit var notificationCommandService: NotificationCommandService

    @Mock
    private lateinit var notificationSseService: NotificationSseService

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(
                NotificationController(notificationQueryService, notificationCommandService, notificationSseService)
            )
            .setCustomArgumentResolvers(
                PageableHandlerMethodArgumentResolver(),
                AuthenticationPrincipalArgumentResolver()
            )
            .build()
        val principal = UserPrincipal.of(1L, "BUYER")
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, principal.authorities)
        whenever(notificationQueryService.getNotifications(eq(1L), isNull(), any()))
            .thenReturn(emptySlice())
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun 정렬_없이_조회하면_최신_알림부터_조회한다() {
        mockMvc.get("/api/v1/notifications").andExpect { status { isOk() } }

        val pageable = capturedPageable()
        assertThat(pageable.sort.getOrderFor("createdAt")?.direction).isEqualTo(Sort.Direction.DESC)
        assertThat(pageable.pageSize).isEqualTo(20)
    }

    @Test
    fun 정렬을_보내면_보낸_정렬로_조회한다() {
        mockMvc.get("/api/v1/notifications") { param("sort", "createdAt,asc") }
            .andExpect { status { isOk() } }

        val pageable = capturedPageable()
        assertThat(pageable.sort.getOrderFor("createdAt")?.direction).isEqualTo(Sort.Direction.ASC)
    }

    private fun capturedPageable(): Pageable {
        val captor = argumentCaptor<Pageable>()
        verify(notificationQueryService).getNotifications(eq(1L), isNull(), captor.capture())
        return captor.firstValue
    }

    private fun emptySlice(): Slice<NotificationResponse> =
        SliceImpl(emptyList(), Pageable.ofSize(20), false)
}
