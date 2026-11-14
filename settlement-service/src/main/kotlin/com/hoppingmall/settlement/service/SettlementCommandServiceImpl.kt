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
import com.hoppingmall.settlement.exception.SettlementItemAlreadySettledException
import com.hoppingmall.settlement.exception.SettlementNoSalesDataException
import com.hoppingmall.settlement.exception.SettlementNotFoundException
import com.hoppingmall.settlement.port.OrderItemQueryPort
import com.hoppingmall.settlement.port.RefundQueryPort
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class SettlementCommandServiceImpl(
    private val settlementRepository: SettlementRepository,
    private val settlementItemRepository: SettlementItemRepository,
    private val orderItemQueryPort: OrderItemQueryPort,
    private val refundQueryPort: RefundQueryPort,
    transactionManager: PlatformTransactionManager,
    private val settlementSummarySynchronizer: SettlementSummarySynchronizer? = null,
    private val settlementSellerLocker: SettlementSellerLocker? = null
) : SettlementCommandService {

    private val transactionTemplate = TransactionTemplate(transactionManager)

    override fun createSettlement(request: CreateSettlementRequest): SettlementResponse {
        try {
            return doCreateSettlement(request)
        } catch (e: DataIntegrityViolationException) {
            if (e.mostSpecificCause.message?.contains(SettlementItem.ORDER_ITEM_UNIQUE_CONSTRAINT, ignoreCase = true) == true) {
                throw SettlementItemAlreadySettledException()
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
            settlementSummarySynchronizer?.sync(settlement)

            SettlementResponse.from(settlement)
        }!!
    }

    private fun startOfSettlementDay(date: LocalDate): LocalDateTime =
        date.atStartOfDay()

    @Transactional
    override fun confirmSettlement(settlementId: Long): SettlementResponse {
        val settlement = settlementRepository.findByIdOrNull(settlementId) ?: throw SettlementNotFoundException() 
        settlement.confirm()
        settlementSummarySynchronizer?.sync(settlement)
        return SettlementResponse.from(settlement)
    }

    @Transactional
    override fun paySettlement(settlementId: Long): SettlementResponse {
        val settlement = settlementRepository.findByIdOrNull(settlementId) ?: throw SettlementNotFoundException() 
        settlement.pay()
        settlementSummarySynchronizer?.sync(settlement)
        return SettlementResponse.from(settlement)
    }
}
