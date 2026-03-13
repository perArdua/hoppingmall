package com.hoppingmall.mall.settlement.service

import com.hoppingmall.mall.order.domain.repository.OrderItemRepository
import com.hoppingmall.mall.refund.domain.repository.RefundRepository
import com.hoppingmall.mall.refund.enum.RefundStatus
import com.hoppingmall.mall.settlement.domain.Settlement
import com.hoppingmall.mall.settlement.domain.SettlementItem
import com.hoppingmall.mall.settlement.domain.repository.SettlementItemRepository
import com.hoppingmall.mall.settlement.domain.repository.SettlementRepository
import com.hoppingmall.mall.settlement.dto.request.CreateSettlementRequest
import com.hoppingmall.mall.settlement.dto.response.SettlementResponse
import com.hoppingmall.mall.settlement.exception.SettlementAlreadyExistsException
import com.hoppingmall.mall.settlement.exception.SettlementInvalidPeriodException
import com.hoppingmall.mall.settlement.exception.SettlementNoSalesDataException
import com.hoppingmall.mall.settlement.exception.SettlementNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
@Transactional
class SettlementCommandServiceImpl(
    private val settlementRepository: SettlementRepository,
    private val settlementItemRepository: SettlementItemRepository,
    private val orderItemRepository: OrderItemRepository,
    private val refundRepository: RefundRepository
) : SettlementCommandService {

    override fun createSettlement(request: CreateSettlementRequest): SettlementResponse {
        if (request.periodEnd.isBefore(request.periodStart)) {
            throw SettlementInvalidPeriodException()
        }

        if (settlementRepository.existsBySellerIdAndPeriodStartAndPeriodEnd(
                request.sellerId, request.periodStart, request.periodEnd
            )
        ) {
            throw SettlementAlreadyExistsException()
        }

        val startDateTime = request.periodStart.atStartOfDay()
        val endDateTime = request.periodEnd.plusDays(1).atStartOfDay()

        val orderItems = orderItemRepository.findDeliveredItemsBySellerAndPeriod(
            request.sellerId, startDateTime, endDateTime
        )

        if (orderItems.isEmpty()) {
            throw SettlementNoSalesDataException()
        }

        val totalSalesAmount = orderItems.sumOf { it.totalPrice }

        val refunds = refundRepository.findBySellerIdAndStatusAndCompletedAtBetween(
            request.sellerId, RefundStatus.COMPLETED, startDateTime, endDateTime
        )
        val totalRefundAmount = refunds.sumOf { it.refundAmount }

        val commissionAmount = totalSalesAmount.multiply(request.commissionRate)
            .setScale(2, RoundingMode.HALF_UP)
        val settlementAmount = totalSalesAmount.subtract(totalRefundAmount).subtract(commissionAmount)

        val settlement = settlementRepository.save(
            Settlement.create(
                sellerId = request.sellerId,
                periodStart = request.periodStart,
                periodEnd = request.periodEnd,
                totalSalesAmount = totalSalesAmount,
                totalRefundAmount = totalRefundAmount,
                commissionRate = request.commissionRate,
                commissionAmount = commissionAmount,
                settlementAmount = settlementAmount
            )
        )

        val settlementItems = orderItems.map { orderItem ->
            SettlementItem.create(
                settlementId = settlement.id!!,
                orderId = orderItem.orderId,
                orderItemId = orderItem.id!!,
                productName = orderItem.productName,
                quantity = orderItem.quantity,
                salesAmount = orderItem.totalPrice
            )
        }
        settlementItemRepository.saveAll(settlementItems)

        return SettlementResponse.from(settlement)
    }

    override fun confirmSettlement(settlementId: Long): SettlementResponse {
        val settlement = settlementRepository.findById(settlementId)
            .orElseThrow { SettlementNotFoundException() }
        settlement.confirm()
        return SettlementResponse.from(settlement)
    }

    override fun paySettlement(settlementId: Long): SettlementResponse {
        val settlement = settlementRepository.findById(settlementId)
            .orElseThrow { SettlementNotFoundException() }
        settlement.pay()
        return SettlementResponse.from(settlement)
    }
}
