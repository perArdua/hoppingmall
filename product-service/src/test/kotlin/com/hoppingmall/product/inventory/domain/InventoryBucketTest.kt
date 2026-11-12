package com.hoppingmall.product.inventory.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test

@DisplayName("InventoryBucket 단위 테스트")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class InventoryBucketTest {

    @Test
    fun 재고를_K개로_나누고_나머지는_0번_버킷에_둔다() {
        val buckets = InventoryBucket.split(7L, 19, 4)

        assertThat(buckets.map { it.bucketNo }).containsExactly(0, 1, 2, 3)
        assertThat(buckets.map { it.stockQuantity }).containsExactly(7, 4, 4, 4)
        assertThat(buckets).allMatch { it.productId == 7L }
        assertThat(buckets.sumOf { it.stockQuantity }).isEqualTo(19)
    }

    @Test
    fun 재고가_버킷_수보다_적으면_0번_버킷만_남는다() {
        assertThat(InventoryBucket.split(7L, 3, 8).map { it.stockQuantity }).containsExactly(3, 0, 0, 0, 0, 0, 0, 0)
    }

    @Test
    fun 복합_키는_상품과_버킷_번호로_같음을_판단한다() {
        assertThat(InventoryBucketId(1L, 2)).isEqualTo(InventoryBucketId(1L, 2))
        assertThat(InventoryBucketId(1L, 2)).isNotEqualTo(InventoryBucketId(1L, 3))
        assertThat(InventoryBucketId()).isEqualTo(InventoryBucketId(0L, 0))
    }
}
