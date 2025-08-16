# HoppingMall

**쇼핑몰 프로젝트**  
Kotlin + Spring Boot + DDD + TDD 기반으로 개발된 쇼핑몰 플랫폼입니다.

[![codecov](https://codecov.io/gh/perArdua/hoppingmall/branch/develop/graph/badge.svg)](https://codecov.io/gh/perArdua/hoppingmall)

---

## 기술 스택

| 분류 | 사용 기술                                             |
|------|---------------------------------------------------|
| 언어 | Kotlin, Java 17                                   |
| 프레임워크 | Spring Boot 3.5, Spring Security, Spring Data JPA |
| 빌드 도구 | Gradle (Kotlin DSL)                               |
| 데이터베이스 | MySQL, H2 (Test용)                                 |
| 메시징 | Kafka, Redis                                      |
| 테스트 | JUnit5, Kotlin Test, Jacoco                       |
| 모니터링 | Spring Boot Actuator, Grafana, Prometheus         |
| 배포 | GitHub Actions, Docker, EC2                       |

---

## 아키텍처 및 DDD 설계

- 도메인 주도 설계 (DDD) 적용
- 바운디드 컨텍스트 기준 패키지 분리
- 애그리거트, 도메인 서비스, 도메인 이벤트 구성

```text
📂 user
📂 product
📂 order
📂 payment
📂 notification
📂 shared-kernel
```

## 전략적 설계 (Strategic Design)

### 바운디드 컨텍스트 (Bounded Context)

| 컨텍스트 이름 | 주요 책임 | API / DB 스키마 |
| --- | --- | --- |
| **User** | 회원가입, 로그인, 권한관리, 판매자 승인 | module-user + User/Seller/Buyer 테이블 |
| **Product** | 상품, 옵션, 이미지 CRUD | module-product + Product 관련 테이블 |
| **Cart** | 장바구니 관리 | module-cart + CartItem |
| **Order** | 주문 생성/승인 | module-order + Order, OrderItem |
| **Payment** | 결제(Mock 처리) | module-payment + Payment |
| **Notification** | 실시간 및 저장 알림 | module-notification + Notification |

---

## 전술적 설계 (Tactical Design)

### 애그리거트 설계 (Aggregates) & 루트 엔티티

- **UserAggregate**
    - 루트 엔티티: `User`
    - 밸류 객체: `Email`, `Password`
    - 관련 엔티티: `Seller`, `Buyer`
- **ProductAggregate**
    - 루트: `Product`
    - 자식: `ProductOption`, `ProductImage`
- **CartAggregate**
    - 루트: `CartItem` (한 사용자 당 여러 항목)
    - 관계: `CartItem`은 `ProductOption` 참조
- **OrderAggregate**
    - 루트: `Order`
    - 자식: `OrderItem`, 연결을 통한 `Payment`
- **PaymentAggregate**
    - 루트: `Payment` (Order 상태에 종속)
- **NotificationAggregate**
    - 루트: `Notification`, 관련 `NotificationType` 밸류 객체

# 기능 명세서

### 사용자(User)

| 기능명 | 설명 | URL | Method | 인증 여부 |  책임 Aggregate |
| --- | --- | --- | --- | --- | --- |
| 회원가입 | 구매자/판매자 계정 생성 | `/api/v1/users/signup` | POST | x | UserAggregate |
| 로그인 | JWT 발급 | `/api/v1/users/login` | POST | x | UserAggregate |
| 내 정보 조회 | 프로필 정보 조회 | `/api/v1/users/me` | GET | o | UserAggregate |
| 내 정보 수정 | 닉네임/비밀번호 변경 등 | `/api/v1/users/me` | PATCH | o | UserAggregate |
| 판매자 승인 신청 | 판매자 등록 정보 제출 | `/api/v1/sellers/apply` | POST | o | UserAggregate (Seller) |
| (Admin) 판매자 승인 처리 | 판매자 승인 or 반려 | `/api/v1/admin/sellers/{id}/approve` | PATCH | o(관리자) | UserAggregate (Admin) |

---

### 상품(Product)

| 기능명 | 설명 | URL | Method | 인증 여부 |  책임 Aggregate |
| --- | --- | --- | --- | --- | --- |
| 상품 등록 | 판매자가 상품 등록 | `/api/v1/products` | POST | o(판매자) | ProductAggregate |
| 상품 수정 | 상품 정보 수정 | `/api/v1/products/{id}` | PUT | o(판매자) | ProductAggregate |
| 상품 삭제 | 상품 삭제 | `/api/v1/products/{id}` | DELETE | o(판매자) | ProductAggregate |
| 상품 목록 조회 | 전체 상품 리스트 | `/api/v1/products` | GET | x | ProductAggregate |
| 상품 상세 조회 | 옵션 포함 상세 정보 | `/api/v1/products/{id}` | GET | x | ProductAggregate |

---

### 장바구니(Cart)

| 기능명 | 설명 | URL | Method | 인증 여부 |  책임 Aggregate |
| --- | --- | --- | --- | --- | --- |
| 장바구니 담기 | 상품 옵션 선택 후 추가 | `/api/v1/cart-items` | POST | o | CartAggregate |
| 장바구니 조회 | 현재 담은 상품 목록 | `/api/v1/cart-items` | GET | o | CartAggregate |
| 수량 수정 | 수량 변경 | `/api/v1/cart-items/{id}` | PATCH | o | CartAggregate |
| 상품 제거 | 장바구니 항목 삭제 | `/api/v1/cart-items/{id}` | DELETE | o | CartAggregate |

---

### 주문(Order)

| 기능명 | 설명 | URL | Method | 인증 여부 |  책임 Aggregate |
| --- | --- | --- | --- | --- | --- |
| 주문 생성 | 장바구니 기반 주문 | `/api/v1/orders` | POST | o | OrderAggregate |
| 주문 상세 조회 | 주문 상세 정보 | `/api/v1/orders/{id}` | GET | o | OrderAggregate |
| 주문 목록 조회 | 내 주문 리스트 | `/api/v1/orders` | GET | o | OrderAggregate |
| 주문 상태 변경 | 판매자가 주문 승인/거절 | `/api/v1/seller/orders/{id}` | PATCH | o(판매자) | OrderAggregate, NotificationAggregate (이벤트 전파) |

---

### 결제(Payment)

| 기능명 | 설명 | URL | Method | 인증 여부 |  책임 Aggregate |
| --- | --- | --- | --- | --- | --- |
| 결제 처리 (Mock) | 결제 시뮬레이션 | `/api/v1/payments` | POST | o | PaymentAggregate |

---

### 알림(Notification)

| 기능명 | 설명 | URL | Method | 인증 여부 |  책임 Aggregate |
| --- | --- | --- | --- | --- | --- |
| 실시간 알림 수신 | WebSocket 연결 후 `/queue/notifications` 구독 | `/ws` | WebSocket | o | NotificationAggregate |
| 알림 목록 조회 | 알림함 조회 | `/api/v1/notifications` | GET | o | NotificationAggregate |
| 알림 읽음 처리 | 알림 읽음 처리 | `/api/v1/notifications/{id}/read` | PATCH | o | NotificationAggregate |

---

### DLQ (Dead Letter Queue) 관리

| 기능명 | 설명 | URL | Method | 인증 여부 |
| --- | --- | --- | --- | --- |
| DLQ 통계 조회 | 실패한 메시지 통계 정보 | `/api/v1/admin/dlq/stats` | GET | o(관리자) |
| DLQ 메시지 일괄 재처리 | 특정 토픽의 실패 메시지 일괄 재처리 | `/api/v1/admin/dlq/retry/{topic}` | POST | o(관리자) |
| DLQ 메시지 개별 재처리 | 특정 메시지 개별 재처리 | `/api/v1/admin/dlq/retry/message/{id}` | POST | o(관리자) |
| 처리된 DLQ 메시지 삭제 | 특정 토픽의 처리 완료된 메시지 삭제 | `/api/v1/admin/dlq/clear/{topic}` | DELETE | o(관리자) |
| 토픽별 DLQ 메시지 조회 | 특정 토픽의 실패 메시지 목록 (페이징) | `/api/v1/admin/dlq/messages/{topic}` | GET | o(관리자) |
| 상태별 DLQ 메시지 조회 | 특정 상태의 실패 메시지 목록 (페이징) | `/api/v1/admin/dlq/messages?status={status}` | GET | o(관리자) |

#### DLQ 메시지 상태
- `PENDING`: 대기 중 (재처리 가능)
- `RETRYING`: 재시도 중
- `PROCESSED`: 처리 완료
- `FAILED`: 최종 실패