# 인수 테스트 작성 가이드

- 이 문서는 `atdd-camping-tests` 프로젝트의 시스템 레벨 인수 테스트 작성 및 관리를 위한 종합 가이드입니다. 
- `kiosk`, `reservation`, `admin`, `payments` 서비스 간의 복합적인 상호작용을 검증하고, AI를 활용하여 효율적인 테스트 시나리오를 구축하는 데 중점을 둡니다.

## 1. 시스템 개요

`atdd-camping-tests`는 캠핑장 예약/관리 시스템의 인수 테스트를 목표로 합니다. 주요 서비스 간의 상호작용 흐름은 다음과 같습니다.

```mermaid
flowchart LR

A[Kiosk] --> C[Reservation]
C --> D[Admin]
A --> E["`Payments 
  (WireMock)  
  `"]
```

## 2. Endpoint 요약

각 서비스의 주요 API 엔드포인트는 다음과 같습니다.

### 2-1. Kiosk

| Method | Endpoint             | Description         | Authentication Required |
|---|----------------------|---------------------|-------------------------|
| GET | `/`                    | 초기 페이지 로드 및 상품 목록 표시 | ❌                     |
| GET | `/health`              | 헬스 체크               | ❌                       |
| POST | `/api/payments`        | 결제 생성               | ❌                     |
| POST | `/api/payments/confirm` | 결제 승인               | ❌                     |
| GET | `/api/products`        | 상품 목록 조회            | ❌                     |

### 2-2. Reservation

| Method | Endpoint | Description | Authentication Required |
|---|---|---------------------|-------------------------|
| POST | `/api/reservations` | 신규 예약 생성 | ❌ |
| GET | `/api/reservations/{id}` | 예약 상세 정보 조회 | ❌ |
| GET | `/api/reservations` | 예약 조회 (날짜별 또는 고객 이름별) | ❌ |
| GET | `/api/reservations/my` | 본인 예약 조회 | ❌ |
| GET | `/api/reservations/calendar` | 특정 사이트의 월별 예약 가능 여부 조회 | ❌ |
| PUT | `/api/reservations/{id}` | 예약 정보 수정 (예약 확인 코드 사용) | ❌ |
| DELETE | `/api/reservations/{id}` | 예약 취소 (예약 확인 코드 사용) | ❌ |
| GET | `/api/sites` | 캠핑장 사이트 목록 조회 | ❌ |
| GET | `/api/sites/{siteId}` | 사이트 상세 정보 조회 | ❌ |
| GET | `/api/sites/{siteNumber}/availability` | 특정 날짜에 해당 사이트가 예약 가능 여부 확인 | ❌ |
| GET | `/api/sites/available` | 특정 날짜에 예약 가능한 사이트 목록 조회 | ❌ |
| GET | `/api/sites/search` | 특정 기간 동안 예약 가능한 사이트 검색 | ❌ |

### 2-3. Admin

| Method | Endpoint | Description | Authentication Required |
|---|---|---------------------|-------------------------|
| POST | /auth/login | 로그인 | ❌ |
| GET | /admin/products | 상품 목록 조회 | ✅ |
| POST | /admin/products | 상품 등록 | ✅ |
| GET | /admin/reservations | 예약 목록 조회 | ✅ |
| PATCH | /admin/reservations/{reservationId}/status | 예약 상태 변경 | ✅ |

### 2-4. Payments (WireMock)

`Payments` 서비스는 `WireMock`을 사용하여 모킹되며, 실제 결제 시스템 없이 다양한 결제 시나리오를 테스트할 수 있습니다.

| Method | Endpoint | Description | Authentication Required |
|---|---|---------------------|-------------------------|
| POST | `/v1/payments` | 결제 생성 및 콜백 URL 등록 | ✅ |
| POST | `/v1/payments/confirm` | 결제 승인 | ✅ |
| POST | `/v1/payments/{paymentKey}/cancel` | 결제 취소 | ✅ |


## 3. 사용자 여정 (Happy Path & Sad Path)

인수 테스트는 사용자의 주요 여정을 `Happy Path` (정상 흐름)와 `Sad Path` (예외/오류 흐름)로 나누어 검증합니다.

### 3.1. Happy Path 예시

*   **정상 예약 성공:**
    1.  `Kiosk`에서 캠핑장 사이트 선택.
    2.  `Payments (WireMock)`에 결제 생성 요청.
    3.  `Payments (WireMock)`에서 결제 승인.
    4.  `Reservation` 서비스에 예약 요청 (CONFIRMED 상태 처리).
    5.  `Admin` 서비스에서 예약 상태 `PAID` 확인.
    6.  캠핑장 사이트 재고 감소.

### 3.2. Sad Path 예시

*   **결제 실패 보상:**
    1.  `Kiosk`에서 캠핑장 사이트 선택 후 결제 시도.
    2.  `Payments (WireMock)`에서 4xx/5xx 오류 또는 타임아웃 발생.
    3.  `Reservation` 서비스에서 예약 미확정/실패 처리.
    4.  `Admin` 서비스에 반영.
    5.  이전에 감소했던 캠핑장 사이트 재고 복원.

*   **취소/환불:**
    1.  `Reservation` 서비스에서 예약 후 취소 요청.
    2.  `Payments (WireMock)`를 통한 환불 처리.
    3.  `Admin` 서비스에 예약 상태 'CANCELLED' 또는 'REFUNDED' 반영.
    4.  캠핑장 사이트 재고 복원.

## 4. 인증 규칙 (Admin 서비스)

Admin 서비스는 JWT (JSON Web Token) Bearer Token 방식을 사용하여 인증을 처리합니다.

### 4.1. Admin 서비스 인증 토큰 획득 및 사용

Admin 서비스의 보호된 API에 접근하려면 다음 절차를 따르십시오.

1.  **토큰 획득:**
    *   `POST /auth/login` 엔드포인트로 `admin.username` 및 `admin.password` 환경 변수에 설정된 사용자 이름과 비밀번호를 포함한 JSON 요청을 보냅니다.
    *   성공적인 응답에서 `AUTH_TOKEN` 쿠키 값을 추출합니다.
    *   **요청 예시 (HTTP POST):**
        ```http
        POST /auth/login HTTP/1.1
        Content-Type: application/json

        {
            "username": "adminUser",
            "password": "adminPassword"
        }
        ```
    *   **응답 예시 (성공 시, 쿠키에 AUTH_TOKEN이 있을 경우):**
        ```http
        HTTP/1.1 200 OK
        Set-Cookie: AUTH_TOKEN=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...; Path=/; HttpOnly
        Content-Type: application/json

        {}
        ```

2.  **토큰 사용:**
    *   획득한 JWT 토큰을 사용하여 보호된 리소스에 접근합니다. 모든 후속 요청의 `Authorization` 헤더에 `Bearer` 접두사와 함께 토큰을 포함하십시오.
    *   **헤더 형식:** `Authorization: Bearer <획득된_JWT_토큰_값>`
    *   **요청 예시 (HTTP GET, 토큰 사용):**
        ```http
        GET /admin/products HTTP/1.1
        Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
        ```

## 5. 시드 데이터 규칙 (Seed Data Rules)

안정적이고 재현 가능한 테스트를 위해 아래 데이터 관리 규칙을 준수하십시오.

### 5.1. 생성 (Creation)

*   **동적 생성:** 테스트 실행 시 필요한 모든 데이터는 **런타임에 동적으로 생성**합니다. 고정된 시드 데이터 사용을 지양합니다.
*   **고유성 보장:** `test_user_{UUID}`, `site_{timestamp}`와 같은 네이밍 패턴을 사용하여 데이터 충돌을 방지하십시오.

### 5.2. 공유 (Sharing)

*   **독립성 유지:** 데이터 공유는 최소화하며, 각 테스트는 자체적인 데이터 세트를 사용합니다.
*   **상태 전달:** Step 간 데이터 공유가 필수적인 경우에만 `ScenarioContext`를 활용합니다.

### 5.3. 격리 (Isolation)

*   **시나리오 격리:** 각 시나리오는 상호 간섭 없이 독립적으로 실행되어야 합니다.
*   **환경 초기화:** `@Before` 및 `@After` 훅을 통해 **WireMock 리셋 및 DB 클리닝** 메커니즘을 반드시 구현합니다.

### 5.4. 목표 (Goal)

*   수만 번의 재실행에도 Side-effect가 없는 **멱등성(Idempotency)이 확보된 테스트 환경**을 구축합니다.


## 6. 시나리오 예시
### 6.1. Anti-Pattern: 기술 중심 테스트
```gherkin
시나리오: 불필요한 HTTP 상태 코드만 검증
  When 사용자가 POST /api/reservations에 요청을 보낸다 # URL 하드코딩
  Then 응답 코드가 200 OK이다 # 비즈니스 검증 누락
```
**문제점 및 개선 방향:**

*   **기술 노출:** 하드코딩된 URL이나 HTTP 메서드가 시나리오에 드러나면 안 됩니다. (추상화된 비즈니스 용어 사용 권장)
*   **검증 부족:** 단순 200 OK는 서비스 간의 데이터 정합성(재고 감소 등)을 보장하지 않습니다.

### 6.2. Best Practice: 도메인/결과 중심 테스트
```gherkin
시나리오: 캠핑장 예약 및 결제 성공
  Given "사이트A"의 재고가 5개 있는 상태이다
  When 사용자가 "사이트A"를 선택하여 결제를 완료한다
  Then 예약 상태가 'CONFIRMED'로 변경된다
  And "사이트A"의 재고가 4개로 감소한다
  And 관리자 페이지에서 결제 상태가 'PAID'로 조회된다
  And 결제 서버(WireMock)에 승인 요청 기록이 남는다
```
**핵심 포인트:**

*   **비즈니스 의도:** 사용자의 행동과 그에 따른 서비스 전반의 결과(Side-effect)를 검증합니다.
*   **관찰 가능성:** Admin 조회, WireMock 기록 등 여러 서비스에 걸친 최종 상태를 확인합니다.


## 7. 주요 프로젝트 폴더

AI는 코드 생성 시 다음 패키지 구조를 준수해야 합니다.

*   `steps/`: Cucumber Step Definition 클래스 위치 (비즈니스 로직 구현)
*   `clients/`: 각 서비스(Kiosk, Reservation 등) 호출을 위한 API 클라이언트
*   `config/`: `TestConfig.java` 등 환경 설정 클래스
*   `resources/features/`: Gherkin 시나리오 파일 위치
    *   `e2e/`: 사용자 중심의 인수 테스트 시나리오
    *   `smoke/`: 시스템 가동 여부 확인을 위한 최소 기능 테스트


## 8. 검토 게이트 및 품질 기준 체크리스트
향후 AI를 통한 테스트 확장 시, 안정성 확보를 위해 다음 태깅 및 검토 프로세스를 준수해야 합니다.

1.  **시나리오 제출:** AI가 생성한 시나리오는 초기 `@ai-candidate` 태그를 가집니다.
2.  **체크리스트 검토:** 아래 체크리스트를 사용하여 시나리오의 품질을 검토합니다.
3.  **태그 변경:** 체크리스트를 통과한 시나리오는 `@ai-candidate` 태그를 제거하고, `e2e`나 `smoke`와 같은 적절한 테스트 태그를 부여합니다. 필요 시, 시나리오의 특성에 맞는 추가 태그를 생성하고 활용할 수 있습니다.

**품질 기준 체크리스트:**

*   [ ] **의도-검증 일치:** `When` 절의 행동이 `Then` 절에서 비즈니스 기대를 정확히 검증하는가?
*   [ ] **환경 독립성:** 호스트, 포트, 인증 토큰 등이 시나리오에 하드코딩되어 있지 않은가? 베이스 URL, 시크릿 등은 외부 설정으로 관리되는가?
*   [ ] **데이터 격리:** 시나리오가 독립적으로 실행되며, 재실행 시에도 동일한 결과를 보장하는가? 테스트 데이터가 고유하게 생성되고 정리되는가?
*   [ ] **관찰 가능성:** 테스트 결과가 예약 상태, 관리자 조회, WireMock 호출 기록 등 시스템의 여러 지점에서 명확하게 관찰 및 검증되는가?
*   [ ] **가독성 및 이해도:** 시나리오가 비즈니스 언어로 명확하고 간결하게 작성되어 모든 이해관계자가 쉽게 이해할 수 있는가?
*   [ ] **완전성:** 정상, 경계, 예외 시나리오를 적절히 포괄하고 있는가?


## 9. 터미널 실행 명령어

`atdd-camping-tests` 프로젝트는 Gradle을 사용하여 테스트를 실행합니다.

### 9.1. 기본 테스트 실행

```bash
# 모든 인수 테스트 실행 (특별한 태그 필터링 없이)
./gradlew :atdd-tests:test
```

### 9.2. 태그를 이용한 선택적 테스트 실행

*   **`@ai-candidate`:** AI가 새로 생성한 시나리오에 기본적으로 부여되는 태그입니다. 이 태그가 붙은 시나리오는 아직 검토 및 승인 대기 중임을 의미합니다.
    *   **실행:** `./gradlew :atdd-tests:test -Dcucumber.filter.tags="@ai-candidate"`
*   **`@e2e`:** 검토를 통과하고 시스템 레벨 인수 테스트로 승인된 시나리오에 부여되는 태그입니다.
    *   **실행:** `./gradlew :atdd-tests:test -Dcucumber.filter.tags="@e2e"`
*   **`@smoke`:** 가장 핵심적인 기능에 대한 빠른 검증을 위한 시나리오에 부여합니다.
*   **`@payment-failure`:** 결제 실패와 관련된 시나리오에 부여합니다.

**다중 태그 조건:**

```bash
# 여러 태그를 조합하여 테스트 실행 (AND 조건)
./gradlew :atdd-tests:test -Dcucumber.filter.tags="@e2e and @smoke"

# 여러 태그를 조합하여 테스트 실행 (OR 조건)
./gradlew :atdd-tests:test -Dcucumber.filter.tags="@e2e or @smoke"

# 특정 태그를 제외하고 테스트 실행
./gradlew :atdd-tests:test -Dcucumber.filter.tags="not @deprecated"
```

### 9.3. 로그 디버깅 방법

(이 섹션은 추가 정보가 제공될 예정입니다. 일반적으로 테스트 실패 시 Gradle 빌드 로그 또는 각 서비스의 애플리케이션 로그를 통해 문제를 진단합니다. 서비스별 로그 파일 위치나 디버깅 설정에 대한 자세한 정보가 필요합니다.)

## 10. WireMock 설정

### 10.1. 개요

- 실제 외부 결제 서비스 대신 `WireMock`을 사용하여 `payments` 서비스를 모의(Mocking)합니다. 
- 이를 통해 외부 의존성 없이 다양한 결제 시나리오를 안정적으로 테스트할 수 있도록 합니다.

```mermaid
flowchart LR

    A[Cucumber Test] --http://localhost:8081/--> C[Kiosk]
    subgraph Docker Network : atdd-net
    C --http://atdd-payments-mock:8080--> D["`Payments
        (WireMock)  
    `"]
    end
  
```
<br/>

### 10.2 도커 컴포즈 구성

`infra/docker-compose.yml` 파일에서 `payments-mock` 서비스는 다음과 같이 정의됩니다.

```yaml
payments-mock:
  image: wiremock/wiremock:latest
  container_name: atdd-payments-mock
  networks:
    - atdd-net
  ports:
    - "8084:8080"
  volumes:
    - ./wiremock/mappings:/home/wiremock/mappings
  healthcheck:
    test: [ "CMD", "curl", "-f", "http://localhost:8080/__admin/mappings" ]
    interval: 10s
    timeout: 5s
    retries: 10
```

`kiosk` 서비스에서 `payments-mock` 서비스로 요청을 보내기 위해 환경 변수를 사용합니다.

```yaml
# infra/docker-compose.yml 
kiosk:
  # ...
  environment:
    # ...
    - KIOSK_PAYMENT_BASE_URL=http://atdd-payments-mock:8080 # payments-mock 컨테이너의 내부 네트워크 주소
```

<br/>

### 10.3. WireMock 스텁 및 템플릿 가이드

`WireMock`은 테스트 시 외부 서비스 (Payments)의 응답을 모의(Mocking)하여 안정적인 테스트 환경을 제공합니다. 응답 맵핑은 JSON 파일 또는 코드 내에서 정의합니다.

#### 10.3.1. 코드 기반 동적 스텁 (Java)

테스트 코드 내 `stubFor`를 사용하여 시나리오별 동적 응답을 정의합니다. (예: `MockPaymentSteps.java`)

```java
// 결제 생성(payments) API 응답
stubFor(post(urlEqualTo(PAYMENTS_ENDPOINT))
        .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(objectMapper.writeValueAsString(new CreateResponse(DEFAULT_PAYMENT_KEY, DEFAULT_ORDER_ID, "CREATED")))));

// 결제 승인(payments/confirm) API 응답
stubFor(post(urlEqualTo(PAYMENTS_CONFIRM_ENDPOINT))
        .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(objectMapper.writeValueAsString(new ConfirmResponse(DEFAULT_PAYMENT_KEY, DEFAULT_ORDER_ID, "CARD", "2026-02-02T10:00:00Z", 10000, "APPROVED", new ConfirmResponse.Receipt("http://receipt.url/123"))))));
```

#### 10.3.2. JSON 응답 템플릿 (Handlebars)

WireMock의 Handlebars 템플릿을 활용하여 동적 응답을 생성합니다.

**사용 단계:**

1.  **템플릿 파일 생성:** `infra/wiremock/__files/dynamic-payment-response.json`
    *   요청 본문, 헤더 등을 참조하여 동적 데이터 정의.
    *   **주요 템플릿 함수:** `jsonPath` (요청에서 값 추출), `now` (동적 시간), `randomValue` (랜덤 문자열).
    ```json
    {
      "paymentKey": "{{jsonPath request.body '$.paymentKey'}}",
      "orderId": "{{jsonPath request.body '$.orderId'}}",
      "status": "APPROVED",
      "approvedAt": "{{now offset='1 hours' format='yyyy-MM-dd HH:mm:ss'}}",
      "amount": {{jsonPath request.body '$.amount'}},
      "receipt": {
        "url": "http://receipt.url/{{randomValue type='ALPHANUMERIC' length=10}}"
      }
    }
    ```
2.  **맵핑 파일 적용:** `infra/wiremock/mappings/dynamic-payment.json`
    *   `bodyFileName`으로 템플릿 파일 지정.
    *   `transformers: ["response-template"]` 추가.
    ```json
    {
      "request": {
        "method": "POST",
        "url": "/v1/payments/confirm"
      },
      "response": {
        "status": 200,
        "headers": {
          "Content-Type": "application/json"
        },
        "bodyFileName": "dynamic-payment-response.json",
        "transformers": ["response-template"]
      }
    }
    ```

#### 10.3.3. WireMock 사용 규칙

1.  **스텁 구분:**
    *   **정적 스텁 (JSON 파일):** 고정 응답 (e.g. `/health`)에 사용.
    *   **동적 스텁 (코드):** 요청 파라미터/로직에 따른 가변 응답에 사용 (테스트 격리 및 유연성).

2.  **검증:**
    `verify()`로 특정 요청 발생 여부 및 파라미터 검증.
    ```java
    // 결제 생성 요청 검증 예시
    verify(postRequestedFor(urlEqualTo("/v1/payments"))
            .withRequestBody(matchingJsonPath("$.orderId", equalTo(DEFAULT_ORDER_ID)))
            .withHeader("Content-Type", equalTo("application/json")));
    ```

3.  **Reset:**
    테스트 독립성을 위해 `@Before`와 `@After`에서 `WireMock.reset()`으로 스텁 및 요청 기록 초기화.
    ```java
    // MockPaymentSteps.java 예시
    @Before public void setupWireMock() { WireMock.configureFor(host, port); WireMock.reset(); }
    @After public void teardownWireMock() { WireMock.reset(); }
    ```
    (`WireMock.reset()` 후 필요한 스텁은 다시 정의해야 함. `resetMappings()` / `resetRequests()`로 세분화 가능.)


### 10.4. WireMock Admin API (제어 및 검증)

WireMock Admin API를 사용하여 테스트 중 WireMock 상태를 직접 제어하고, 실제 발생한 요청을 검증합니다.

*   **1. 상태 초기화:**
    *   **설명:** 모든 스텁 및 요청 기록 초기화.
    *   **메서드/URL:** `POST /__admin/reset` 또는 `POST /__admin/requests/reset`
*   **2. 요청 기록 조회/검증:**
    *   **설명:** WireMock에 들어온 모든 요청 기록 조회 및 특정 요청 검증.
    *   **메서드/URL:** `GET /__admin/requests`
    *   **참고:** `http://localhost:8084/__admin/webapp/` 관리 UI에서도 확인 가능.
*   **3. 디버깅 정보 조회:**
    *   **설명:** 매칭 실패 등 디버깅 정보 조회.
    *   **메서드/URL:** `GET /__admin/requests/unmatched` (매칭되지 않은 요청)
    *   **참고:** `http://localhost:8084/__admin/webapp/` 관리 UI 활용.