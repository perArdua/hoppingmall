package com.hoppingmall.user.domain

import com.hoppingmall.user.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.Filter

@Filter(name = "softDeleteFilter", condition = "deleted_at IS NULL")
@Entity
@Table(name = "buyers")
class Buyer private constructor(

    @Column(name = "user_id", nullable = false)
    val userId: Long

) : BaseEntity() {

    companion object {
        fun create(user: User): Buyer {
            return Buyer(user.id!!)
        }
    }
}
