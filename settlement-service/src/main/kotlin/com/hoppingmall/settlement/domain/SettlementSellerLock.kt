package com.hoppingmall.settlement.domain

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "settlement_seller_locks")
class SettlementSellerLock(
    @Id
    val sellerId: Long
)
