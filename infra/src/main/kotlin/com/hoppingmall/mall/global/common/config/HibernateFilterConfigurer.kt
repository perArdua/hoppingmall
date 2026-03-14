package com.hoppingmall.mall.global.common.config

import jakarta.annotation.PostConstruct
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.hibernate.Session
import org.springframework.stereotype.Component

@Component
class HibernateFilterConfigurer(

    @PersistenceContext
    private val entityManager: EntityManager
) {
    @PostConstruct
    fun enableSoftDeleteFilter() {
        val session = entityManager.unwrap(Session::class.java)
        session.enableFilter("softDeleteFilter")
            .setParameter("isDeleted", false)
    }

}