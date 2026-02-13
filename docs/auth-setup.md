# 인증 및 실행 가이드 문서

## 1. 개요 및 목적

이 문서는 `atdd-camping-tests` 프로젝트의 인수 테스트를 실행하기 위한 인증 설정 및 전반적인 실행 환경 구성에 대한 가이드라인을 제공합니다. 특히 `Admin` 서비스와 `Payments` 서비스의 인증 메커니즘을 이해하고, 테스트에 필요한 자격 증명을 관리하는 방법을 설명합니다.

## 2. 인증 메커니즘 및 테스트 흐름

현재 시스템은 `Admin` 서비스와 `Payments` 서비스에서 인증을 요구합니다. `Kiosk` 및 `Reservation` 서비스의 공개된 엔드포인트는 일반적으로 인증이 필요 없습니다. 인수 테스트 시에는 이 인증 과정을 모방하여 토큰을 획득하고, 보호된 엔드포인트에 접근할 때 해당 토큰을 사용해야 합니다.

### 2.1. 인증 토큰 획득 및 사용 (Admin 서비스 예시)

`Admin` 서비스에 대한 인증은 `POST /auth/login` 엔드포인트를 통해 이루어집니다. 이 요청의 성공적인 응답으로 `Set-Cookie` 헤더를 통해 `AUTH_TOKEN` 형태의 인증 토큰이 발행됩니다. 획득된 토큰은 이후 `Admin` 서비스의 보호된 엔드포인트에 요청을 보낼 때 `Authorization` 헤더에 `Bearer {AUTH_TOKEN}` 형태로 포함되어야 합니다.

**개념적인 로그인 및 토큰 사용 흐름:**

1.  **로그인 요청:**
    `POST /auth/login` 엔드포인트에 유효한 사용자 이름과 비밀번호로 요청을 보냅니다.

    ```bash
    # Admin 서비스 로그인 예시 (사용자: admin, 비밀번호: password)
    # 응답 헤더의 Set-Cookie에서 AUTH_TOKEN 값을 추출합니다.
    curl -i -X POST "http://localhost:8082/auth/login" \
         -H "Content-Type: application/json" \
         -d '{ "username": "admin", "password": "password" }'
    ```
    성공적인 응답 시, `Set-Cookie` 헤더에 `AUTH_TOKEN={획득된_인증_토큰_값}; Path=/; ...` 와 같은 형태로 토큰이 반환됩니다.

2.  **토큰 저장:**
    응답에서 `AUTH_TOKEN` 값을 추출하여 변수(예: `String authToken`)에 저장합니다.

3.  **보호된 리소스 접근:**
    이후 `Admin` 서비스의 `GET /admin/products`와 같은 보호된 엔드포인트에 요청을 보낼 때, 획득된 `AUTH_TOKEN`을 `Authorization: Bearer {AUTH_TOKEN}` 헤더에 포함하여 요청합니다.

    ```bash
    # 획득된 토큰을 사용하여 보호된 리소스에 접근하는 예시
    AUTH_TOKEN="eyJ..." # 실제 획득된 토큰 값으로 대체
    curl -i -X GET "http://localhost:8082/admin/products" \
         -H "Authorization: Bearer ${AUTH_TOKEN}"
    ```

### 2.2. 테스트 시 인증 토큰 관리

인수 테스트에서는 위와 같은 인증 흐름을 자동화하여 재현해야 합니다.

*   **테스트 컨텍스트 (`ScenarioContext`)를 통한 토큰 공유:**
    테스트 프레임워크(Cucumber) 내에서는 `com.camping.tests.context.ScenarioContext`와 같은 공유 가능한 컨텍스트 객체를 사용하여 로그인 Step에서 획득한 `AUTH_TOKEN`을 저장하고, 이후 보호된 엔드포인트에 접근하는 다른 Step 정의에서 이 토큰을 조회하여 사용할 수 있습니다.

    ```java
    // 예시: ScenarioContext에 토큰 저장
    @Autowired
    private ScenarioContext scenarioContext;

    @When("관리자 사용자가 로그인하면")
    public void adminLogsIn() {
        // ... 로그인 요청 수행 ...
        String authToken = response.header("Set-Cookie").split("AUTH_TOKEN=")[1].split(";")[0];
        scenarioContext.setAuthToken(authToken); // ScenarioContext에 토큰 저장
    }

    // 예시: ScenarioContext에서 토큰 조회 후 클라이언트에 주입
    @And("관리자가 상품 목록을 조회한다")
    public void adminViewsProducts() {
        adminClient.setAuthToken(scenarioContext.getAuthToken()); // 클라이언트에 토큰 주입
        adminClient.getProducts();
    }
    ```

*   **API 클라이언트 Setter를 통한 주입:**
    `com.camping.tests.clients.ApiClient` 또는 각 서비스 클라이언트 (`AdminClient`, `PaymentClient`)에 `setAuthToken(String token)`과 같은 메서드를 추가하여 획득한 토큰을 주입받도록 합니다. 클라이언트는 이 토큰을 내부 `RequestSpecification`에 설정하여 모든 후속 요청에 자동으로 `Authorization` 헤더를 포함시킬 수 있습니다. 이는 Step 정의 내에서의 코드 중복을 줄이고 테스트 코드의 가독성 및 유지보수성을 높이는 데 기여합니다.

    ```java
    // 예시: AdminClient에 토큰 설정 로직 추가 (AdminClient.java 파일 내)
    public class AdminClient {
        // ... 기존 코드 ...

        public void setAuthToken(String token) {
            this.api.addHeader("Authorization", "Bearer " + token);
        }

        // ...
    }
    ```

## 3. 테스트 실행 환경 구성

### 3.1. 테스트를 위한 사전 준비

1.  **Docker 설치:** Docker와 Docker Compose가 시스템에 설치되어 있어야 합니다.
2.  **JDK 설치:** 테스트 실행을 위해 Java Development Kit (JDK) 17 이상이 설치되어 있어야 합니다.
3.  **서비스 실행:** 아래 `3.4 Docker Compose를 이용한 서비스 실행` 섹션의 지침에 따라 모든 필수 서비스가 실행 중인지 확인합니다.

### 3.2. 환경 변수 설정 및 우선순위

테스트 설정(예: 서비스 URL)은 여러 소스에서 로드될 수 있으며, 명확한 우선순위를 가집니다. 이는 `TestConfig.java`의 구현에 정의되어 있습니다.

**설정 소스 및 우선순위:**

1.  **환경 변수 (Environment Variables) - 우선순위 가장 높음**
    *   `export` 명령어나 셸 프로필을 통해 시스템에 설정된 변수입니다.
    *   키는 대소문자를 구분하지 않으며, `_`나 `-`는 `.`으로 정규화됩니다. (예: `ADMIN_BASE_URL` -> `admin.base.url`)

2.  **Java 시스템 프로퍼티 (Java System Properties) - 우선순위 중간**
    *   Gradle이나 Java 실행 시 `-D` 플래그를 통해 전달되는 변수입니다.

3.  **`config.properties` 파일 - 우선순위 가장 낮음**
    *   `src/test/resources/config.properties` 파일에 정의된 기본값입니다.

**설정값 재정의(Override) 예시:**

`admin.base.url` 값을 기본값(`http://localhost:8082`)에서 다른 값으로 변경하고 싶을 때, 다음 방법들을 사용할 수 있습니다.

*   **환경 변수로 재정의 (가장 높은 우선순위):**
    ```bash
    # 터미널에서 환경 변수를 설정하고 테스트 실행
    export ADMIN_BASE_URL="http://test-server:8082"
    ./gradlew test
    ```

*   **시스템 프로퍼티로 재정의:**
    ```bash
    # Gradle 실행 시 시스템 프로퍼티로 전달
    ./gradlew test -Dadmin.base.url="http://test-server:8082"
    ```

*   **`config.properties` 파일 수정 (가장 낮은 우선순위):**
    파일 자체를 수정하여 기본값을 변경합니다.
    ```properties
    # src/test/resources/config.properties
    admin.base.url=http://test-server:8082
    ```

이러한 우선순위 규칙을 이해하면 로컬 테스트, CI/CD 환경 등 다양한 환경에 맞게 유연하게 설정을 변경할 수 있습니다.

### 3.3. Docker 서비스 상세 정보

테스트 환경은 여러 Docker 컨테이너로 구성됩니다. 각 서비스의 이름과 포트 정보는 다음과 같습니다.

| 서비스명 (`docker-compose.yml`) | 컨테이너명 | 외부 포트 (Host) | 내부 포트 (Container) | 설명 |
| :--- | :--- | :--- | :--- | :--- |
| `kiosk` | `atdd-kiosk` | `8081` | `8080` | 키오스크 UI 및 BFF |
| `admin` | `atdd-admin` | `8082` | `8080` | 관리자 서비스 |
| `reservation` | `atdd-reservation`| `8083` | `8080` | 예약 서비스 |
| `payments-mock`| `atdd-payments-mock`| `8084` | `8080` | 결제 서비스 모킹 (WireMock) |
| `db` | `atdd-db` | `3306` | `3306` | MySQL 데이터베이스 |

### 3.4. Docker Compose를 이용한 서비스 실행

프로젝트는 `infra/docker-compose.yml` 및 `infra/docker-compose-infra.yml` 파일을 사용하여 테스트 환경을 구성할 수 있습니다. `gradle/tasks.gradle.kts`에 정의된 Gradle 태스크를 사용하면 더 편리하게 서비스를 관리할 수 있습니다.

**서비스 실행:**

```bash
# 모든 인프라 및 애플리케이션 서비스 실행
./gradlew composeUp
```

**서비스 중지:**

```bash
# 모든 서비스 중지 및 볼륨 삭제
./gradlew composeDown
```

**서비스 상태 확인:**
```bash
# 현재 실행 중인 애플리케이션 서비스 목록 확인
./gradlew ServicePs

# 또는 docker-compose 명령어를 직접 사용
docker-compose -f infra/docker-compose.yml ps
```

## 4. 테스트 실행 가이드

### 4.1. 기본 테스트 실행

가장 간단한 방법은 Gradle 래퍼(`gradlew`)를 사용하여 `test` 태스크를 실행하는 것입니다. 이 명령은 `RunCucumberTest.java`를 실행하여 모든 피처 파일을 테스트합니다.

```bash
# 모든 인수 테스트 실행
./gradlew test
```

### 4.2. 태그를 이용한 선택적 테스트 실행

Gherkin 시나리오에 부여된 태그를 기준으로 특정 테스트만 선택적으로 실행할 수 있습니다. Gradle의 시스템 프로퍼티(`-D`)를 통해 Cucumber 필터 태그를 전달하는 방식을 사용합니다.

```bash
# @smoke 태그가 붙은 시나리오만 실행
./gradlew test -Dcucumber.filter.tags="@smoke"

# @e2e 태그가 있고 @smoke 태그는 없는 시나리오 실행
./gradlew test -Dcucumber.filter.tags="@e2e and not @smoke"

# @kiosk 또는 @admin 태그가 붙은 시나리오 실행
./gradlew test -Dcucumber.filter.tags="@kiosk or @admin"
```
이 방식은 CI/CD 파이프라인에서 특정 종류의 테스트(예: Smoke Test)만 빠르게 실행하거나, 로컬 개발 중에 특정 기능과 관련된 테스트만 확인할 때 매우 유용합니다.

## 5. CI/CD 파이프라인 연동 예시

이 인수 테스트는 CI/CD 파이프라인에 통합하여 코드 변경 시 자동으로 시스템 전체의 안정성을 검증하는 데 사용될 수 있습니다. 아래는 GitHub Actions를 사용한 예시 워크플로우입니다.

### 5.1. GitHub Actions 워크플로우 예시

`.github/workflows/acceptance-tests.yml`

```yaml
name: E2E Acceptance Tests

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  run-acceptance-tests:
    runs-on: ubuntu-latest
    
    steps:
      - name: Checkout repository
        uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Grant execute permission for gradlew
        run: chmod +x ./gradlew

      - name: Start Docker services
        run: ./gradlew composeUp
        env:
          # 필요한 경우 CI 환경용 환경 변수 설정
          # 예: ADMIN_USERNAME: ${{ secrets.CI_ADMIN_USERNAME }}
          #     ADMIN_PASSWORD: ${{ secrets.CI_ADMIN_PASSWORD }}

      - name: Wait for services to be healthy
        run: ./gradlew waitForServices

      - name: Run acceptance tests (Smoke Test)
        run: ./gradlew test -Dcucumber.filter.tags="@smoke"

      - name: Run acceptance tests (E2E)
        if: success() # Smoke Test 성공 시에만 실행
        run: ./gradlew test -Dcucumber.filter.tags="@e2e and not @smoke"

      - name: Stop Docker services
        if: always() # 테스트 성공 여부와 관계없이 항상 실행
        run: ./gradlew composeDown
```

### 5.2. Jenkins 파이프라인 예시 (Declarative)

```groovy
pipeline {
    agent any

    environment {
        // 필요한 경우 CI 환경용 환경 변수 설정
        // ADMIN_USERNAME = credentials('jenkins-admin-username')
        // ADMIN_PASSWORD = credentials('jenkins-admin-password')
    }

    stages {
        stage('Checkout') {
            steps {
                git 'https://github.com/your-repo/atdd-camping-tests.git'
            }
        }
        
        stage('Setup') {
            steps {
                sh 'chmod +x ./gradlew'
            }
        }

        stage('Start Services') {
            steps {
                sh './gradlew composeUp'
            }
        }
        
        stage('Wait for Services') {
            steps {
                sh './gradlew waitForServices'
            }
        }

        stage('Run Tests') {
            steps {
                sh './gradlew test'
            }
        }
    }

    post {
        always {
            stage('Stop Services') {
                steps {
                    sh './gradlew composeDown'
                }
            }
        }
    }
}
```
위 예시들은 테스트 환경을 구성하고, Gradle 태스크를 실행하여 전체 테스트 과정을 자동화하는 방법을 보여줍니다. `if: always()` 나 `post` 블록을 사용하여 테스트가 실패하더라도 항상 환경이 정리되도록 하는 것이 중요합니다.

## 6. 트러블슈팅

**Q: 테스트 실행 시 `Connection refused` 오류가 발생합니다.**
**A:** 테스트 대상 서비스들이 실행 중이지 않을 가능성이 높습니다. `./gradlew composeUp` 명령을 실행하고, `docker ps`를 통해 모든 컨테이너가 정상적으로 실행 중인지 확인하세요.

**Q: Docker Compose 실행 시 포트 충돌(Port conflict) 오류가 발생합니다.**
**A:** `docker-compose.yml`에 정의된 포트(예: 8081, 8082 등)를 다른 프로세스가 이미 사용하고 있습니다. 해당 프로세스를 종료하거나, `docker-compose.yml`의 포트 매핑을 변경해야 합니다. (예: `"8091:8080"`)

**Q: `Admin` 또는 `Payments` 관련 테스트에서 401 Unauthorized 오류가 계속 발생합니다.**
**A:** 인증 토큰이 요청에 올바르게 포함되지 않았을 가능성이 큽니다.
1. 로그인 Step이 정상적으로 수행되어 토큰이 `ScenarioContext`에 저장되었는지 확인하세요.
2. `AdminClient` 또는 `PaymentClient`를 호출하는 Step에서 `Authorization` 헤더를 제대로 추가하고 있는지 디버깅하세요. 본 문서의 `2.2. 테스트 시 인증 토큰 관리` 항목을 참고하여 클라이언트의 토큰 주입 로직을 확인하고 리팩토링하는 것을 권장합니다.

**Q: Gradle 빌드 또는 테스트가 이상하게 실패합니다.**
**A:** Gradle 캐시나 이전 빌드 아티팩트 문제일 수 있습니다. `./gradlew clean build --refresh-dependencies` 명령으로 캐시를 정리하고 의존성을 다시 다운로드해보세요.

**Q: `atdd-camping-*` 서브모듈의 코드를 변경했는데 테스트에 반영되지 않습니다.**
**A:** `docker-compose.yml`은 각 서브모듈 디렉토리를 `build context`로 사용합니다. 코드를 변경한 후에는 이미지를 다시 빌드해야 합니다. `./gradlew composeUp`은 `--build` 옵션을 포함하므로 자동으로 이미지를 다시 빌드하지만, 문제가 지속되면 `docker-compose -f infra/docker-compose.yml build --no-cache`와 같이 캐시 없이 강제로 이미지를 재빌드해볼 수 있습니다.