### 🔧 [주문-결제 취소 Saga 구현]

#### 📝 어떤 기능을 추가하나요?
주문 취소 시 결제 취소를 Saga 패턴으로 처리하는 기능을 구현합니다.

#### 👀 자세한 내용
- Order 도메인에 취소 상태 전이 추가 (CANCELLATION_REQUESTED, CANCELLED 등)
- OrderCommandServiceImpl에 취소 요청 로직 추가
- OrderSagaConsumer, OrderCancellationResultConsumer로 Saga 이벤트 처리
- PaymentCommandPort/HttpPaymentCommandAdapter로 결제 서비스 호출
- Shipping 도메인에 취소 가능 상태 검증 추가
- PaymentCancellationRequestedHandler 전략 패턴 추가
- InternalPaymentController 취소 엔드포인트 추가

🔗 관련 브랜치: `feat/#issue-order-cancel-saga`
