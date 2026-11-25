# 재고 모드(inventory.mode) 변경 절차

product-service는 인스턴스마다 `inventory.mode`(`single` 또는 `bucket`)와 `inventory.bucket.count`(기본 8)로 재고를 어디서 차감할지 정한다.

- `single`: `inventories.stock_quantity` 한 행에서 차감한다.
- `bucket`: 상품마다 `inventory_buckets` 행 N개(`bucket.count`)에 나눠 담고 그중 한 칸에서 차감한다. `inventories.stock_quantity`는 재고 생성·수정(`initStock`, `updateStock`) 때만 맞춰지고 예약·증감·복원 때는 바뀌지 않는다.

두 모드가 같은 DB에 동시에 붙으면 같은 재고가 두 벌로 팔린다(재고 1,000에 1,491개 판매, #1177).

**모드가 섞인 채로 롤링·카나리 배포로 모드를 바꾸는 것은 허용하지 않는다. 모드를 바꿀 때는 모든 product-service 인스턴스를 먼저 내린다.**

## 기동 검사

DB의 `inventory_runtime_mode` 표(행 1개, `id = 1`)에 이 DB를 쓰는 재고 모드가 기록된다. 인스턴스는 기동 중(빈 생성이 끝난 직후, 웹·gRPC·Kafka·스케줄러가 시작되기 전) 이 행을 확인하고, 다르면 기동하지 않는다.

| DB 상태 | 이 인스턴스 | 결과 |
| --- | --- | --- |
| 행 없음, `inventory_buckets` 비어 있음 | single | `single` 기록 후 기동 |
| 행 없음, `inventory_buckets` 행 있음 | single | 기록하지 않고 기동 실패 |
| 행 없음, `inventory_buckets` 행 있음 | bucket | `bucket`과 버킷 수 기록 후 기동 |
| 행 없음, `inventory_buckets` 비어 있음, `inventories` 재고 없음 | bucket | `bucket`과 버킷 수 기록 후 기동 |
| 행 없음, `inventory_buckets` 비어 있음, `inventories`에 재고 있음 | bucket | 기록하지 않고 기동 실패 |
| 행 있음, 모드(bucket이면 버킷 수까지) 같음 | 아무거나 | 기동 |
| 행 있음, 모드나 버킷 수가 다름 | 아무거나 | 기동 실패 |

여러 인스턴스가 행이 없는 DB에서 동시에 떠도 기본 키 때문에 한 인스턴스만 기록하고, 나머지는 기록된 값을 다시 읽어 비교한다.

이 검사가 들어가기 전부터 쓰던 DB에는 행이 없다. 지금 모드 그대로 띄우면 위 표대로 첫 인스턴스가 행을 기록한다. 지금 쓰는 모드를 모르겠으면 띄우기 전에 `inventory_buckets` 행 유무를 보고, 띄울 설정과 다르면 아래 절차를 먼저 밟는다.

현재 모드 확인:

```sql
SELECT mode, bucket_count, updated_at FROM inventory_runtime_mode WHERE id = 1;
```

## single → bucket

1. product-service 인스턴스를 모두 내린다(k8s: `replicas: 0`, Argo Rollout은 카나리 진행 없이 0으로 줄인다).
2. 남아 있는 버킷 행을 지운다. 예전 bucket 시절의 버킷이 남아 있으면 채우기가 그 상품을 건너뛰어 옛 재고로 팔게 된다.

   ```sql
   DELETE FROM inventory_buckets;
   ```

3. 모드 행을 bucket으로 기록한다. 버킷 수는 띄울 인스턴스의 `inventory.bucket.count`와 같게 한다.

   ```sql
   INSERT INTO inventory_runtime_mode (id, mode, bucket_count, updated_at)
   VALUES (1, 'bucket', 8, NOW(6))
   ON DUPLICATE KEY UPDATE mode = 'bucket', bucket_count = 8, updated_at = NOW(6);
   ```

4. 모든 인스턴스를 `inventory.mode=bucket`, 같은 `inventory.bucket.count`로 띄운다. 기동이 끝나면(`ApplicationReadyEvent`) 버킷 채우기가 `inventories.stock_quantity`를 버킷으로 나눠 담는다. 채우기가 놓친 상품은 첫 예약·복원 때 같은 방식으로 버킷이 만들어진다. 바로 채우려면 `POST /internal/api/v1/inventory/buckets/backfill`을 부른다.

single 때 만든 RESERVED 예약은 bucket 모드에서 만료·취소될 때 버킷으로 돌아간다.

## bucket → single

1. product-service 인스턴스를 모두 내린다.
2. 상품마다 버킷 합계를 `inventories`로 옮기고, 버킷 행을 지우고, 모드 행을 single로 바꾼다. 한 트랜잭션으로 실행한다.

   ```sql
   START TRANSACTION;

   UPDATE inventories i
   JOIN (
     SELECT product_id, SUM(stock_quantity) AS total
     FROM inventory_buckets
     GROUP BY product_id
   ) b ON b.product_id = i.product_id
   SET i.stock_quantity = b.total, i.updated_at = NOW(6);

   DELETE FROM inventory_buckets;

   UPDATE inventory_runtime_mode SET mode = 'single', bucket_count = NULL, updated_at = NOW(6) WHERE id = 1;

   COMMIT;
   ```

   버킷이 없는 상품은 `inventories` 값을 그대로 쓴다. 버킷 행을 남겨 두면 single 인스턴스는 기록이 없는 DB에서 기동하지 않고, 나중에 다시 bucket으로 바꿀 때 옛 버킷이 쓰인다.

3. 모든 인스턴스를 `inventory.mode=single`로 띄운다.

bucket 때 만든 RESERVED 예약의 수량은 이미 버킷 합계에서 빠져 있으므로, single 모드에서 만료·취소되면 `inventories`로 돌아간다.

## 버킷 수 변경 (bucket → bucket)

버킷 수만 바꿀 때도 인스턴스를 모두 내린 뒤 bucket → single의 2단계 SQL에서 마지막 UPDATE만 아래로 바꿔 실행하고, 새 버킷 수로 모든 인스턴스를 띄운다. 기동 뒤 채우기가 `inventories`의 합계를 새 버킷 수로 다시 나눈다.

```sql
UPDATE inventory_runtime_mode SET mode = 'bucket', bucket_count = 16, updated_at = NOW(6) WHERE id = 1;
```

## 설정 위치

- docker: `product-service/src/main/resources/application-docker.yml` (`inventory.mode: bucket`, 버킷 수 기본 8)
- k8s: `product-service/src/main/resources/application-k8s.yml` (`inventory.mode: bucket`, 버킷 수 기본 8)
- 프로필 없이 띄우면 `single`
