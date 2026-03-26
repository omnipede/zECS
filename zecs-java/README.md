# zECS Java - Enterprise Caching System

z/OS Enterprise Caching System (zECS) 의 Java 구현체입니다. 원래의 COBOL/CICS 기반 zECS 를 Spring Boot 로 포팅하여 모던한 자바 환경에서 실행할 수 있습니다.

## 📋 개요

zECS 는 분산 키/밸류 캐싱 서비스로, 다음과 같은 기능을 제공합니다:

- **RESTful API**: HTTP GET, POST, PUT, DELETE 지원
- **키/밸류 구조**: 키는 최대 255 바이트, 밸류는 최대 3.2MB
- **TTL 기반 만료**: 5 분 ~ 24 시간 생존 시간 설정
- **Basic Authentication**: 사용자별 권한 관리 (SELECT, UPDATE, DELETE)
- **자동 만료 처리**: 백그라운드에서 만료된 키 자동 삭제
- **데이터센터 복제**: Active/Active, Active/Standby 모드 지원

## 🚀 빠른 시작

### Docker Compose 로 시작하기 (권장)

```bash
cd docker
docker-compose up -d
```

이 명령으로 Redis 와 zECS 애플리케이션이 함께 시작됩니다.

- **zECS API**: http://localhost:50100
- **Redis Commander**(선택): http://localhost:8081

### 수동으로 실행하기

1. **Redis 서버 실행**
   ```bash
   docker run -d -p 6379:6379 --name redis redis:7-alpine
   ```

2. **애플리케이션 빌드 및 실행**
   ```bash
   mvn clean package
   java -jar target/zecs-java-1.0.0.jar
   ```

## 📖 API 사용법

### 기본 URL 구조

```
/resources/ecs/{org}/{app}/{key}
```

- `{org}`: 조직 ID (예: devops)
- `{app}`: 애플리케이션 ID (예: sessionData)
- `{key}`: 캐시 키 (1-255 바이트)

### curl 예제

#### 값 조회 (GET)

```bash
curl -X GET "http://localhost:50100/resources/ecs/devops/sessionData/my-key"
```

#### 값 저장 (POST)

```bash
curl -X POST "http://localhost:50100/resources/ecs/devops/sessionData/my-key" \
  -H "Content-Type: text/plain" \
  -d "my-value"
```

#### 값 수정 (PUT)

```bash
curl -X PUT "http://localhost:50100/resources/ecs/devops/sessionData/my-key" \
  -H "Content-Type: application/json" \
  -d '{"name":"John","age":30}'
```

#### 값 삭제 (DELETE)

```bash
curl -X DELETE "http://localhost:50100/resources/ecs/devops/sessionData/my-key"
```

#### 전체 캐시 초기화

```bash
curl -X DELETE "http://localhost:50100/resources/ecs/devops/sessionData/any-key?clear=*"
```

#### TTL 설정

```bash
curl -X POST "http://localhost:50100/resources/ecs/devops/sessionData/temp-key?ttl=300" \
  -d "temporary-value"
```

### JavaScript 예제

```javascript
const svc = new XMLHttpRequest();
const url = "http://localhost:50100/resources/ecs/devops/sessionData/";
const key_name = "mlb_dodgers";
const ecs_value = JSON.stringify({
  name: "Los Angeles Dodgers",
  players: 26,
  salaries: 248606156,
  won_world_series: true
});

// 저장 (POST)
svc.open("POST", url + key_name, false);
svc.setRequestHeader("Content-type", "application/json");
svc.send(ecs_value);
console.log("POST Status:", svc.status);

// 조회 (GET)
svc.open("GET", url + key_name, false);
svc.send(null);
console.log("GET Response:", svc.responseText);

// 삭제 (DELETE)
svc.open("DELETE", url + key_name, false);
svc.send(null);
console.log("DELETE Status:", svc.status);
```

## 🔐 보안

### 기본 인증 (Basic Authentication)

zECS 는 HTTP Basic Authentication 을 지원합니다.

```bash
curl -X GET "http://localhost:50100/resources/ecs/devops/sessionData/my-key" \
  -u USERID1:password1
```

### 사용자 권한

| 사용자 | 권한 | 설명 |
|--------|------|------|
| USERID1 | SELECT, UPDATE, DELETE | 전체 권한 |
| USERID2 | SELECT, UPDATE | 읽기/쓰기 전용 |
| USERID3 | SELECT | 읽기 전용 |

권한은 `application.yml` 에서 수정할 수 있습니다.

## ⚙️ 설정

### 주요 설정 항목 (`application.yml`)

```yaml
server:
  port: 50100  # HTTP 포트

spring:
  data:
    redis:
      host: localhost
      port: 6379

zecs:
  cache:
    default-ttl: 1800      # 기본 TTL (초)
    min-ttl: 300           # 최소 TTL (5 분)
    max-ttl: 86400         # 최대 TTL (24 시간)
    max-key-length: 255    # 최대 키 길이
    max-value-size: 3200000  # 최대 밸류 크기 (3.2MB)
  
  security:
    enabled: true
    authenticate: BASIC
  
  replication:
    mode: A1  # A1(Standalone), AA(Active/Active), AS(Active/Standby)
    enabled: false
  
  expiration:
    scan-interval: 1500  # 만료 스캔 주기 (ms)
    batch-size: 500      # 배치 삭제 크기
```

## 📊 HTTP 상태 코드

| 코드 | 의미 | 설명 |
|------|------|------|
| 200 | OK | 성공 |
| 201 | Created | 키 생성 성공 |
| 204 | No Content | 키를 찾을 수 없음 |
| 400 | Bad Request | 잘못된 요청 (키 형식, 길이 등) |
| 401 | Unauthorized | 인증 실패 |
| 409 | Conflict | 중복 키 (POST) |
| 507 | Insufficient Storage | 서버 오류 |

## 🏗️ 아키텍처

### 기술 스택

- **프레임워크**: Spring Boot 3.2
- **데이터 스토어**: Redis 7
- **보안**: Spring Security (Basic Auth)
- **빌드**: Maven
- **Java**: 17+

### 프로젝트 구조

```
zecs-java/
├── src/main/java/com/enterprise/zecs/
│   ├── config/          # 설정 클래스
│   ├── controller/      # REST API 컨트롤러
│   ├── service/         # 비즈니스 로직
│   ├── repository/      # 데이터 접근
│   ├── model/           # 도메인 모델
│   ├── security/        # 보안 처리
│   └── exception/       # 예외 처리
├── src/main/resources/
│   └── application.yml  # 설정 파일
├── src/test/            # 테스트 코드
└── docker/              # Docker 설정
```

## 🔄 복제 모드

### A1 - Standalone

단일 사이트 운영. 복제 없음.

### AA - Active/Active

양방향 복제. 두 데이터센터 모두 읽기/쓰기 가능.

### AS - Active/Standby

단방향 복제. 주 데이터센터에서 대기 데이터센터로 복제.

설정 예:

```yaml
zecs:
  replication:
    mode: AA
    enabled: true
    partner:
      host: sysplex01-ecs.mycompany.com
      port: 50102
      scheme: http
```

## 🧪 테스트

```bash
# 단위 테스트
mvn test

# Docker 환경에서 통합 테스트
docker-compose -f docker/docker-compose.yml up --build
```

## 📝 원본 zECS 와의 비교

| 기능 | zECS (COBOL/CICS) | zECS Java |
|------|-------------------|-----------|
| 플랫폼 | z/OS, CICS/TS | Java 17+, Spring Boot |
| 데이터 스토어 | VSAM/RLS | Redis |
| 웹 서버 | CICS Web Support | Spring Web MVC |
| 보안 | RACF | Spring Security |
| 만료 처리 | CICS 백그라운드 태스크 | @Scheduled |
| 복제 | CICS WEB API | RestTemplate |
| 설정 | CICS RDO, CSD | application.yml |

## 🚀 배포

### Docker

```bash
docker build -t zecs-java:1.0.0 -f docker/Dockerfile .
docker run -d -p 50100:50100 --env SPRING_DATA_REDIS_HOST=redis zecs-java:1.0.0
```

### Kubernetes

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: zecs-java
spec:
  replicas: 3
  selector:
    matchLabels:
      app: zecs-java
  template:
    spec:
      containers:
      - name: zecs-java
        image: zecs-java:1.0.0
        ports:
        - containerPort: 50100
        env:
        - name: SPRING_DATA_REDIS_HOST
          value: "redis-master"
```

## 📄 라이선스

원본 zECS 의 라이선스를 따릅니다.

## 👥 기여

원본 zECS 기여자:
- Randy Frerking, Walmart Technology
- Rich Jackson, Walmart Technology
- Michael Karagines, Walmart Technology
- Trey Vanderpool, Walmart Technology

Java 포팅: Enterprise Caching System Team

## 📞 문의

이식된 Java 버전에 대한 문의는 프로젝트 관리자에게 연락하십시오.
