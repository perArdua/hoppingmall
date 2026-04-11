package com.hoppingmall.settlement.service

import org.springframework.data.repository.findByIdOrNull
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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.math.RoundingMode

@Service
class SettlementCommandServiceImpl(
    private val settlementRepository: SettlementRepository,
    private val settlementItemRepository: SettlementItemRepository,
    private val orderItemQueryPort: OrderItemQueryPort,
    private val refundQueryPort: RefundQueryPort,
    transactionManager: PlatformTransactionManager
) : SettlementCommandService {

    private val transactionTemplate = TransactionTemplate(transactionManager)

    override fun createSettlement(request: CreateSettlementRequest): SettlementResponse {
        if (request.periodEnd.isBefore(request.periodStart)) {
            throw SettlementInvalidPeriodException()
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

        return transactionTemplate.execute {
            if (settlementRepository.existsBySellerIdAndPeriodStartAndPeriodEnd(
                    request.sellerId, request.periodStart, request.periodEnd
                )
            ) {
                throw SettlementAlreadyExistsException()
            }

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

            SettlementResponse.from(settlement)
        }!!
    }

    @Transactional
    override fun confirmSettlement(settlementId: Long): SettlementResponse {
        val settlement = settlementRepository.findByIdOrNull(settlementId) ?: throw SettlementNotFoundException() 
        settlement.confirm()
        return SettlementResponse.from(settlement)
    }

    @Transactional
    override fun paySettlement(settlementId: Long): SettlementResponse {
        val settlement = settlementRepository.findByIdOrNull(settlementId) ?: throw SettlementNotFoundException() 
        settlement.pay()
        return SettlementResponse.from(settlement)
    }
}
