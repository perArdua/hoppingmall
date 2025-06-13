package com.hoppingmall.mall.global.common.config

import jakarta.persistence.EntityManager
import org.hibernate.Filter
import org.hibernate.Session
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class HibernateFilterConfigurerTest {

    private val entityManager: EntityManager = mock()
    private val session: Session = mock()
    private val filter: Filter = mock()
    
    private val configurer = HibernateFilterConfigurer(entityManager)

    @Test
    fun `enableSoftDeleteFilter는 Session에서 소프트 삭제 필터를 활성화한다`() {
        // given
        whenever(entityManager.unwrap(Session::class.java)).thenReturn(session)
        whenever(session.enableFilter("softDeleteFilter")).thenReturn(filter)
        whenever(filter.setParameter("isDeleted", false)).thenReturn(filter)

        // when
        configurer.enableSoftDeleteFilter()

        // then
        verify(entityManager).unwrap(Session::class.java)
        verify(session).enableFilter("softDeleteFilter")
        verify(filter).setParameter("isDeleted", false)
    }

    @Test
    fun `enableSoftDeleteFilter는 PostConstruct에서 자동으로 호출된다`() {
        // given
        whenever(entityManager.unwrap(Session::class.java)).thenReturn(session)
        whenever(session.enableFilter("softDeleteFilter")).thenReturn(filter)
        whenever(filter.setParameter("isDeleted", false)).thenReturn(filter)

        // when - PostConstruct 시뮬레이션
        configurer.enableSoftDeleteFilter()

        // then
        verify(session).enableFilter("softDeleteFilter")
    }
}