### 🔧 [Outbox 공통 모듈 분리]

#### 📝 어떤 기능을 추가하나요?
order-service, payment-service에 중복된 Outbox 도메인/서비스를 hoppingmall-outbox 공통 모듈로 추출합니다.

#### 👀 자세한 내용
- hoppingmall-outbox 모듈 신규 생성 (OutboxEvent, OutboxStatus, OutboxMetrics, OutboxEventRepository, OutboxEventService, OutboxEventWriter)
- 모듈 테스트 포함 (OutboxMetricsTest, OutboxEventWriterTest)
- CI 워크플로에 outbox 모듈 추가

🔗 관련 브랜치: `refactor/#issue-outbox-module`
