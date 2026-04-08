# HoppingMall

구매자와 판매자를 연결하는 온라인 쇼핑몰입니다. 상품 등록부터 주문, 결제, 포인트 적립, 쿠폰, 환불, 배송 추적, 정산까지 커머스 운영에 필요한 전체 흐름을 지원합니다.

[![codecov](https://codecov.io/gh/perArdua/hoppingmall/branch/develop/graph/badge.svg)](https://codecov.io/gh/perArdua/hoppingmall)

## 서비스 구조

<img width="1241" height="775" alt="system_architecture" src="https://github.com/user-attachments/assets/81ae35c0-40fd-42eb-80ed-87d39415ebbd" />


## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Kotlin, Java 21 |
| Framework | Spring Boot 3.5, Spring Cloud Gateway |
| DB | MySQL 8.0, H2 (테스트) |
| Messaging | Kafka (EOS, Transactional Outbox) |
| Cache | Redis Cluster (Redisson), Caffeine |
| Communication | gRPC, REST (Resilience4j Circuit Breaker) |
| Auth | JWT (Stateless), Role 기반 (BUYER/SELLER/ADMIN) |
| Infra | Docker Compose, Kind (K8s), Istio, ArgoCD, Argo Rollouts |
| Monitoring | Prometheus, Grafana, Loki, Zipkin |
| CI/CD | GitHub Actions, Canary 배포 (20% → 50% → 100%) |
| Test | JUnit5, Mockito, EmbeddedKafka, Jacoco (80%+) |

## 이벤트 흐름

주문부터 알림까지의 전체 이벤트 흐름입니다.

```mermaid
sequenceDiagram
    participant C as Client
    participant O as Order Service
    participant P as Payment Service
    participant K as Kafka
    participant N as Notification Service

    C->>O: 주문 생성 (POST /orders)
    O->>O: 재고 예약 (batchReserveStock)
    O-->>C: 주문 응답 (CREATED)

    C->>P: 결제 요청 (POST /payments)
    P->>P: 결제 처리 + Outbox 저장
    P->>K: payment-completed (Outbox Scheduler)

    K->>O: payment-completed
    O->>O: 주문 상태 → PAID
    O->>O: 재고 예약 확정 (confirmReservations)

    K->>P: point-earn-request
    P->>P: 포인트 적립 + Outbox 저장
    P->>K: notification

    K->>N: 알림 저장 + 전송
```

## 결제 보상 흐름

결제 실패 시 보상 트랜잭션 처리 흐름입니다.

```mermaid
flowchart LR
    A[결제 실패/취소] --> B[payment-compensation 발행]
    B --> C[Order Service]
    C --> D[주문 취소]
    C --> E[재고 복구]
    C --> F[예약 취소]

    G[환불 완료] --> H[refund-completion 발행]
    H --> I[Payment Service]
    I --> J[결제 상태 REFUNDED]
    I --> K[포인트 반환]
    I --> L[쿠폰 복구]
    I --> M[재고 복구]
    I --> N[주문 취소]
```

## 도메인

```
user-service        회원, 인증, 멤버십 (등급별 적립률 1~7%)
product-service     상품, 카테고리, 재고, 리뷰, 위시리스트, 통계
order-service       주문, 장바구니, 환불, 배송
payment-service     결제, 포인트, 쿠폰, DLQ
notification-service 알림 (Kafka Consumer)
settlement-service  정산 (판매자 수익 집계)
api-gateway         라우팅, JWT 검증, Rate Limiting
```

## Canary 배포

Argo Rollouts + Istio 기반 Canary 배포를 지원합니다.

```mermaid
graph LR
    A[새 이미지 Push] --> B[ArgoCD Sync]
    B --> C[Canary Pod 생성]
    C --> D[20% 트래픽]
    D -->|promote| E[50% 트래픽]
    E -->|promote| F[100% 트래픽]
    D -->|abort| G[롤백]
    E -->|abort| G
```

```bash
# 로컬 환경 셋업
./scripts/setup.sh

# 이미지 빌드 + 배포
./scripts/build-push.sh
./scripts/deploy-all.sh

# Canary 데모
./scripts/canary-demo.sh api-gateway
```

## 실행 방법

```bash
# 인프라 (Kafka, Redis, MySQL)
docker-compose up -d

# 개별 서비스 실행
cd user-service && ./gradlew bootRun

# 테스트
./gradlew test

# 커버리지 검증 (80% 이상)
./gradlew jacocoTestVerification
```

## 주요 패턴

- **Transactional Outbox** : 이벤트 발행 보장 (5초 주기 스케줄러)
- **Consumer 멱등성** : eventId 기반 중복 처리 방지 + DataIntegrityViolation catch
- **Saga (2-Phase Step Tracking)** : 분산 트랜잭션 보상 + crash recovery
- **DLQ** : DB 기반 Dead Letter Queue (자동 재시도, 지수 백오프)
- **CQRS** : 상품 도메인 Command/Query 서비스 분리
- **Pessimistic Locking** : 포인트 잔액, 재고 조작 시 비관적 락
- **Circuit Breaker + Retry** : Resilience4j 기반 서비스 간 장애 격리
