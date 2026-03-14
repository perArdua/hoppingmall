package com.hoppingmall.mall.global.common.config

import jakarta.persistence.EntityManager
import org.hibernate.Filter
import org.hibernate.Session
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@DisplayName("HibernateFilterConfigurer")
@DisplayNameGeneration(ReplaceUnderscores::class)
class HibernateFilterConfigurerTest {

    private val entityManager: EntityManager = mock()
    private val session: Session = mock()
    private val filter: Filter = mock()
    
    private val configurer = HibernateFilterConfigurer(entityManager)

    @Nested
    @DisplayName("enableSoftDeleteFilter")
    inner class EnableSoftDeleteFilter {
        @Test
        fun enableSoftDeleteFilter는_Session에서_소프트_삭제_필터를_활성화한다() {
            whenever(entityManager.unwrap(Session::class.java)).thenReturn(session)
            whenever(session.enableFilter("softDeleteFilter")).thenReturn(filter)
            whenever(filter.setParameter("isDeleted", false)).thenReturn(filter)

            configurer.enableSoftDeleteFilter()

            verify(entityManager).unwrap(Session::class.java)
            verify(session).enableFilter("softDeleteFilter")
            verify(filter).setParameter("isDeleted", false)
        }

        @Test
        fun enableSoftDeleteFilter는_PostConstruct에서_자동으로_호출된다() {
            whenever(entityManager.unwrap(Session::class.java)).thenReturn(session)
            whenever(session.enableFilter("softDeleteFilter")).thenReturn(filter)
            whenever(filter.setParameter("isDeleted", false)).thenReturn(filter)

            configurer.enableSoftDeleteFilter()

            verify(session).enableFilter("softDeleteFilter")
        }
    }
}