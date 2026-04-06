Closes #294

### 📌 작업 개요
결제 취소 Saga에 필요한 Avro 이벤트 스키마 3종을 정의하고, AvroEventConverter에 변환 로직을 추가합니다.

---

### ✅ 주요 변경 사항

- `hoppingmall-common.avro`
  - `PaymentCancellationRequestedEvent.avsc`: 결제 취소 요청 이벤트 스키마
  - `PaymentCancellationCompletedEvent.avsc`: 결제 취소 완료 이벤트 스키마
  - `PaymentCancellationFailedEvent.avsc`: 결제 취소 실패 이벤트 스키마

- `hoppingmall-common.event`
  - `AvroEventConverter`: 신규 이벤트 변환 로직 추가

---

### 📎 관련 이슈
- #294
