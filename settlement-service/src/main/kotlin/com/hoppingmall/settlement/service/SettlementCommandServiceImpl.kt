package com.hoppingmall.settlement.service

import com.hoppingmall.settlement.domain.Settlement
import com.hoppingmall.settlement.domain.SettlementItem
import com.hoppingmall.settlement.domain.repository.SettlementItemRepository
import com.hoppingmall.settlement.domain.repository.SettlementRepository
import com.hoppingmall.settlement.dto.request.CreateSettlementRequest
import com.hoppingmall.settlement.dto.response.SettlementResponse
import com.hoppingmall.settlement.exception.SettlementAlreadyExistsException
import com.hoppingmall.settlement.exception.SettlementInvalidPeriodException
import com.hoppingmall.settlement.exception.SettlementNoSalesDataException
import com.hoppingmall.settlement.exception.SettlementNotFoundException
import com.hoppingmall.settlement.port.OrderItemQueryPort
import com.hoppingmall.settlement.port.RefundQueryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode

@Service
@Transactional
class SettlementCommandServiceImpl(
    private val settlementRepository: SettlementRepository,
    private val settlementItemRepository: SettlementItemRepository,
    private val orderItemQueryPort: OrderItemQueryPort,
    private val refundQueryPort: RefundQueryPort
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

        val orderItems = orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(
            request.sellerId, startDateTime, endDateTime
        )

        if (orderItems.isEmpty()) {
            throw SettlementNoSalesDataException()
        }

        val totalSalesAmount = orderItems.sumOf { it.totalPrice }

        val refunds = refundQueryPort.findCompletedBySellerAndPeriod(
            request.sellerId, startDateTime, endDateTime
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
                orderItemId = orderItem.id,
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
