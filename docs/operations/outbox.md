# 아웃박스 운영 메모

## 멈춘(parked) 행 다시 넣기

메시지 쪽 실패(직렬화 실패, 레코드 크기 초과, 토픽 권한 없음 등)로 재시도 3회를 다 쓴 행은 `status = 'FAILED'`, `processed = true`로 멈추고 발행 대상에서 빠진다. 브로커 쪽 실패는 재시도 횟수를 쓰지 않으므로 여기에 쌓이지 않는다.

- 지표: `outbox.parked` (멈춘 행 수)
- 로그: `Outbox event parked after max retries ...` (ERROR)
- 정리 작업이 `processed = true` 행을 7일 뒤 지우므로 그 전에 처리한다.

원인을 고친 뒤 아래 중 하나로 다시 넣는다. 자동으로 다시 넣는 경로는 없다.

```sql
UPDATE outbox_events SET status = 'PENDING', retry_count = 0, processed = false, processed_at = NULL, updated_at = NOW(6) WHERE id IN (...) AND processed = true AND status = 'FAILED';
```

코드에서는 `OutboxEventService.requeueParked(ids)`를 부른다. 다음 발행 주기에 나간다.
