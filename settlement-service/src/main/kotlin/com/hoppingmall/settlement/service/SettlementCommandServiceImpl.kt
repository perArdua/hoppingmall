package com.hoppingmall.settlement.service

import com.hoppingmall.settlement.domain.Settlement
import com.hoppingmall.settlement.domain.SettlementCarryOver
import com.hoppingmall.settlement.domain.SettlementItem
import com.hoppingmall.settlement.domain.SettlementRefund
import com.hoppingmall.settlement.domain.repository.SettlementCarryOverRepository
import com.hoppingmall.settlement.domain.repository.SettlementItemRepository
import com.hoppingmall.settlement.domain.repository.SettlementRefundRepository
import com.hoppingmall.settlement.domain.repository.SettlementRepository
import com.hoppingmall.settlement.dto.request.CreateSettlementRequest
import com.hoppingmall.settlement.dto.response.SettlementResponse
import com.hoppingmall.settlement.exception.SettlementAlreadyExistsException
import com.hoppingmall.settlement.exception.SettlementInvalidPeriodException
import com.hoppingmall.settlement.exception.SettlementItemAlreadySettledException
import com.hoppingmall.settlement.exception.SettlementNoSalesDataException
import com.hoppingmall.settlement.exception.SettlementNotFoundException
import com.hoppingmall.settlement.exception.SettlementRefundAlreadyDeductedException
import com.hoppingmall.settlement.port.OrderItemQueryPort
import com.hoppingmall.settlement.port.RefundInfo
import com.hoppingmall.settlement.port.RefundQueryPort
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class SettlementCommandServiceImpl(
    private val settlementRepository: SettlementRepository,
    private val settlementItemRepository: SettlementItemRepository,
    private val orderItemQueryPort: OrderItemQueryPort,
    private val refundQueryPort: RefundQueryPort,
    transactionManager: PlatformTransactionManager,
    private val settlementRefundRepository: SettlementRefundRepository,
    private val settlementCarryOverRepository: SettlementCarryOverRepository,
    private val settlementSummarySynchronizer: SettlementSummarySynchronizer? = null,
    private val settlementSellerLocker: SettlementSellerLocker? = null
) : SettlementCommandService {

    private val transactionTemplate = TransactionTemplate(transactionManager)

    override fun createSettlement(request: CreateSettlementRequest): SettlementResponse {
        try {
            return doCreateSettlement(request)
        } catch (e: DataIntegrityViolationException) {
            val cause = e.mostSpecificCause.message
            if (cause?.contains(SettlementItem.ORDER_ITEM_UNIQUE_CONSTRAINT, ignoreCase = true) == true) {
                throw SettlementItemAlreadySettledException()
            }
            if (cause?.contains(SettlementRefund.REFUND_UNIQUE_CONSTRAINT, ignoreCase = true) == true) {
                throw SettlementRefundAlreadyDeductedException()
            }
            throw e
        }
    }

    private fun doCreateSettlement(request: CreateSettlementRequest): SettlementResponse {
        if (request.periodEnd.isBefore(request.periodStart)) {
            throw SettlementInvalidPeriodException()
        }

        val startDateTime = startOfSettlementDay(request.periodStart)
        val endDateTime = startOfSettlementDay(request.periodEnd.plusDays(1))

        val orderItems = orderItemQueryPort.findDeliveredItemsBySellerAndPeriod(
            request.sellerId, startDateTime, endDateTime
        )
        val refunds = refundQueryPort.findCompletedBySellerAndPeriod(
            request.sellerId, startDateTime, endDateTime
        ).distinctBy { it.id }

        if (orderItems.isEmpty() && refunds.isEmpty()) {
            throw SettlementNoSalesDataException()
        }

        return transactionTemplate.execute {
            settlementSellerLocker?.lock(request.sellerId)
            if (settlementRepository.existsBySellerIdAndPeriodStartAndPeriodEnd(
                    request.sellerId, request.periodStart, request.periodEnd
                ) ||
                settlementRepository.existsBySellerIdAndPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(
                    request.sellerId, request.periodEnd, request.periodStart
                )
            ) {
                throw SettlementAlreadyExistsException()
            }

            val newRefunds = excludeDeductedRefunds(refunds)
            if (orderItems.isEmpty() && newRefunds.isEmpty()) {
                throw SettlementNoSalesDataException()
            }
            val pendingCarryOvers = settlementCarryOverRepository
                .findBySellerIdAndAppliedSettlementIdIsNullOrderByIdAsc(request.sellerId)

            val amounts = SettlementAmounts.calculate(
                totalSalesAmount = orderItems.sumOf { it.totalPrice },
                totalRefundAmount = newRefunds.sumOf { it.refundAmount },
                commissionRate = request.commissionRate,
                carriedInAmount = pendingCarryOvers.sumOf { it.amount }
            )

            val settlement = settlementRepository.save(
                Settlement.create(
                    sellerId = request.sellerId,
                    periodStart = request.periodStart,
                    periodEnd = request.periodEnd,
                    totalSalesAmount = amounts.totalSalesAmount,
                    totalRefundAmount = amounts.totalRefundAmount,
                    commissionRate = request.commissionRate,
                    commissionAmount = amounts.commissionAmount,
                    settlementAmount = amounts.settlementAmount,
                    carriedInAmount = amounts.carriedInAmount,
                    carryOverAmount = amounts.carryOverAmount
                )
            )
            val settlementId = settlement.id!!

            val settlementItems = orderItems.map { orderItem ->
                SettlementItem.create(
                    settlementId = settlementId,
                    orderId = orderItem.orderId,
                    orderItemId = orderItem.id,
                    productName = orderItem.productName,
                    quantity = orderItem.quantity,
                    salesAmount = orderItem.totalPrice
                )
            }
            settlementItemRepository.saveAll(settlementItems)
            settlementRefundRepository.saveAll(
                newRefunds.map { SettlementRefund.create(settlementId, it.id, it.refundAmount) }
            )
            pendingCarryOvers.forEach { it.applyTo(settlementId) }
            if (amounts.carryOverAmount.signum() > 0) {
                settlementCarryOverRepository.save(
                    SettlementCarryOver.create(request.sellerId, settlementId, amounts.carryOverAmount)
                )
            }
            settlementSummarySynchronizer?.sync(settlement)

            SettlementResponse.from(settlement)
        }!!
    }

    private fun excludeDeductedRefunds(refunds: List<RefundInfo>): List<RefundInfo> {
        if (refunds.isEmpty()) return refunds
        val deducted = settlementRefundRepository.findRefundIdsIn(refunds.map { it.id }).toSet()
        return refunds.filterNot { it.id in deducted }
    }

    private fun startOfSettlementDay(date: LocalDate): LocalDateTime =
        date.atStartOfDay()

    @Transactional
    override fun confirmSettlement(settlementId: Long): SettlementResponse {
        val settlement = settlementRepository.findWithLockById(settlementId) ?: throw SettlementNotFoundException()
        settlement.confirm()
        settlementSummarySynchronizer?.sync(settlement)
        return SettlementResponse.from(settlement)
    }

    @Transactional
    override fun paySettlement(settlementId: Long): SettlementResponse {
        val settlement = settlementRepository.findWithLockById(settlementId) ?: throw SettlementNotFoundException()
        settlement.pay()
        settlementSummarySynchronizer?.sync(settlement)
        return SettlementResponse.from(settlement)
    }
}
