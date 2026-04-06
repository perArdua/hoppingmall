### 🔧 [결제 취소 Avro 스키마]

#### 📝 어떤 기능을 추가하나요?
결제 취소 Saga에 필요한 Avro 이벤트 스키마를 정의하고, AvroEventConverter에 변환 로직을 추가합니다.

#### 👀 자세한 내용
- PaymentCancellationRequestedEvent.avsc 추가
- PaymentCancellationCompletedEvent.avsc 추가
- PaymentCancellationFailedEvent.avsc 추가
- AvroEventConverter에 신규 이벤트 변환 로직 추가

🔗 관련 브랜치: `feat/#issue-cancellation-avro`
