package com.hoppingmall.user.domain

import com.hoppingmall.common.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
    name = "buyers",
    indexes = [Index(name = "idx_buyers_user_id", columnList = "user_id")]
)
class Buyer private constructor(

    @Column(name = "user_id", nullable = false)
    val userId: Long

) : BaseEntity() {

    companion object {
        fun create(user: User): Buyer = Buyer(user.id!!)
    }
}
