### 🔧 [BaseSecurityConfig 공통 추출]

#### 📝 어떤 기능을 추가하나요?
각 서비스에 중복된 SecurityConfig 설정을 hoppingmall-common의 BaseSecurityConfig로 추출하여 공통화합니다.

#### 👀 자세한 내용
- hoppingmall-common에 BaseSecurityConfig 추상 클래스 생성
- 6개 서비스(notification, order, payment, product, settlement, user)의 SecurityConfig가 BaseSecurityConfig를 상속
- 공통 보안 설정(CSRF, 세션, 헤더 등) 중복 제거

🔗 관련 브랜치: `refactor/#issue-base-security-config`
