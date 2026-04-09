package com.hoppingmall.user.domain

import com.hoppingmall.common.BaseEntity
import jakarta.persistence.*

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
