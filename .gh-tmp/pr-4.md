Closes #300

### 📌 작업 개요
order-service, payment-service에 중복된 Outbox 도메인/서비스를 hoppingmall-outbox 공통 모듈로 추출합니다.

---

### ✅ 주요 변경 사항

- `hoppingmall-outbox.domain`
  - `OutboxEvent`, `OutboxStatus`: Outbox 도메인 모델

- `hoppingmall-outbox.metrics`
  - `OutboxMetrics`: Outbox 메트릭 수집

- `hoppingmall-outbox.repository`
  - `OutboxEventRepository`: Outbox 이벤트 저장소

- `hoppingmall-outbox.service`
  - `OutboxEventService`: 스케줄러 기반 이벤트 발행
  - `OutboxEventWriter`: 이벤트 저장

- `.github/workflows/ci.yml`
  - CI에 hoppingmall-outbox 모듈 추가

---

### 🧪 테스트

- `OutboxMetricsTest`: 메트릭 수집 테스트
- `OutboxEventWriterTest`: 이벤트 저장 테스트

---

### 📎 관련 이슈
- #300
