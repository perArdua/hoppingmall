Closes #305

### 📌 작업 개요
payment-service의 로컬 Outbox 구현을 hoppingmall-outbox 공통 모듈로 전환합니다.

---

### ✅ 주요 변경 사항

- `payment-service.config`
  - `OutboxMetrics`: 삭제 (공통 모듈로 대체)
  - `JpaConfig`: entityManagerFactory 패키지 경로 수정

- `payment-service.outbox.domain`
  - `OutboxEvent`: 삭제 (공통 모듈로 대체)

- `payment-service.outbox.repository`
  - `OutboxEventRepository`: 삭제 (공통 모듈로 대체)

- `payment-service.outbox.service`
  - `OutboxEventService`, `OutboxEventWriter`: 삭제 (공통 모듈로 대체)
  - `OutboxEventPublisher`, `OutboxMaintenanceScheduler`, `TransactionalEventPublisher`: import 경로 수정

- `payment-service`
  - `build.gradle.kts`, `settings.gradle.kts`, `Dockerfile`, `PaymentServiceApplication`: hoppingmall-outbox 의존성 추가

---

### 🧪 테스트

- `OutboxMetricsTest`: import 경로 수정
- `OutboxEventPublisherTest`: 신규 추가

---

### 📎 관련 이슈
- #305
