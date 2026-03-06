# server distri

## 아키텍처

```
Client → Gateway(8080) → Auth(8081) / User(8082) / Product(8083) / Order(8084)
                              ↓              ↓              ↓              ↓
                           H2 + Redis     H2 + Redis    H2 + Redis +   H2 + Redis +
                           (JWT token)    (user cache)   Kafka + Cache   Kafka
```

## 시작하기

### 1. 인프라 실행

```bash
docker-compose up -d
```

### 2. 서비스 실행

```bash
# 터미널 5개 또는 백그라운드로
./gradlew :auth:bootRun      # 8081
./gradlew :user:bootRun      # 8082
./gradlew :product:bootRun   # 8083
./gradlew :order:bootRun     # 8084
./gradlew :gateway:bootRun   # 8080
```

### 3. 프론트엔드

```bash
open frontend/index.html
```

기본 계정: `khope@test.com` / `1234`

## 로컬 링크

| 서비스 | URL |
|--------|-----|
| Gateway | http://localhost:8080 |
| Auth | http://localhost:8081 |
| User | http://localhost:8082 |
| Product | http://localhost:8083 |
| Order | http://localhost:8084 |
| Frontend | `frontend/index.html` |

### 모니터링

| 서비스 | URL | 계정 |
|--------|-----|------|
| Grafana | http://localhost:3000 | admin / admin |
| Prometheus | http://localhost:9090 | - |
| Loki | http://localhost:3100 | - |

### Grafana 데이터소스 설정

1. http://localhost:3000 접속 (admin/admin)
2. Connections → Data sources → Add data source
3. **Prometheus** 추가: URL = `http://prometheus:9090`
4. **Loki** 추가: URL = `http://loki:3100`

### H2 Console

| 서비스 | URL | JDBC URL |
|--------|-----|----------|
| Auth | http://localhost:8081/h2-console | jdbc:h2:mem:authdb |
| User | http://localhost:8082/h2-console | jdbc:h2:mem:userdb |
| Product | http://localhost:8083/h2-console | jdbc:h2:mem:productdb |
| Order | http://localhost:8084/h2-console | jdbc:h2:mem:orderdb |

### Actuator 엔드포인트

각 서비스별:
- Health: `http://localhost:{port}/actuator/health`
- Metrics: `http://localhost:{port}/actuator/metrics`
- Prometheus: `http://localhost:{port}/actuator/prometheus`

## 주요 기능

- **JWT 인증** — Gateway에서 토큰 검증 후 X-User-Id 헤더 주입
- **Tiered Cache** — Caffeine L1 + Redis L2 (모드: caffeine/redis/tiered)
- **Saga Pattern** — 주문 생성 시 재고 차감 분산 트랜잭션 + 보상
- **DLT** — Kafka 메시지 처리 실패 시 Dead Letter Topic으로 이동
- **Kafka JSON** — 모든 Kafka 메시지 JSON 포맷

## 결제 플로우

```
1. 상품 둘러보기     GET  /api/products
2. 장바구니 담기     POST /api/cart/items
3. 결제하기         POST /api/orders
4. 주문 확인        GET  /api/orders
```

## 기술 스택

- Kotlin 1.9 + Spring Boot 3.2.5 + JDK 17
- Spring Cloud Gateway (WebFlux)
- H2 (in-memory DB) + Spring Data JPA
- Redis + Caffeine (tiered cache)
- Apache Kafka (event-driven)
- Prometheus + Grafana + Loki (monitoring)
