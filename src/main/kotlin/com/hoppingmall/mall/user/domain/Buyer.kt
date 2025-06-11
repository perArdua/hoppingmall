package com.hoppingmall.mall.user.domain

import com.hoppingmall.mall.global.common.entity.BaseEntity
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Filter


@Filter(name = "softDeleteFilter", condition = "deleted_at IS NULL")
@Entity
@Table(name = "buyers")
class Buyer private constructor(

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User

) : BaseEntity() {

    companion object {
        fun create(user: User): Buyer {
            return Buyer(user)
        }
    }
}
