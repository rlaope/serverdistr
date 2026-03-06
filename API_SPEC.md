# API Specification

## Architecture Overview

```
Client → Gateway(8080) → Auth(8081) / User(8082) / Product(8083) / Order(8084)
                              ↓              ↓              ↓              ↓
                           H2 + Redis     H2 + Redis    H2 + Redis +   H2 + Redis +
                           (JWT token)    (user cache)   Kafka + Cache   Kafka
```

### Payment Flow
```
1. 상품 둘러보기     GET  /api/products
2. 장바구니 담기     POST /api/cart/items
3. 결제하기         POST /api/orders         (Cart → Order 전환, 재고 검증, Kafka 재고 차감)
4. 결제항목 확인     GET  /api/users/me/orders (User → Order 서비스간 REST 통신)
```

### Service Communication
| From | To | Method | Purpose |
|------|----|--------|---------|
| Gateway | All | HTTP Proxy | JWT 검증 후 라우팅 |
| Order | Product | REST (WebClient) | 결제 시 상품 가격/재고 검증 |
| Order | Product | REST (WebClient) | 결제 후 장바구니 비우기 |
| Order | Product | Kafka (`stock-decrease`) | 결제 완료 후 재고 차감 |
| User | Order | REST (WebClient) | 유저 결제항목 목록 조회 |

---

## Auth Service (port 8081)

### POST /api/auth/signup
회원가입

**Request**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response** `201 Created`
```json
{
  "accessToken": "eyJhbG...",
  "refreshToken": "eyJhbG...",
  "tokenType": "Bearer"
}
```

### POST /api/auth/login
로그인

**Request**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

**Response** `200 OK`
```json
{
  "accessToken": "eyJhbG...",
  "refreshToken": "eyJhbG...",
  "tokenType": "Bearer"
}
```

### POST /api/auth/refresh
토큰 갱신

**Request**
```json
{
  "refreshToken": "eyJhbG..."
}
```

**Response** `200 OK`
```json
{
  "accessToken": "eyJhbG...",
  "refreshToken": "eyJhbG...",
  "tokenType": "Bearer"
}
```

### GET /api/auth/validate
토큰 검증 (내부 호출용)

**Headers**: `Authorization: Bearer {token}`

**Response** `200 OK`
```json
{
  "userId": 1,
  "email": "user@example.com",
  "role": "USER"
}
```

---

## User Service (port 8082)

> 인증 필요: Gateway가 JWT 검증 후 `X-User-Id`, `X-User-Role` 헤더 주입

### GET /api/users/{id}
유저 조회

**Response** `200 OK`
```json
{
  "id": 1,
  "nickname": "user",
  "profileImageUrl": null,
  "createdAt": "2026-03-06T12:00:00"
}
```

### GET /api/users/me
내 정보 조회

**Headers**: `X-User-Id: {userId}` (Gateway 자동 주입)

**Response** `200 OK` (위와 동일)

### GET /api/users/me/orders
내 결제항목 조회 (User → Order 서비스간 통신)

**Headers**: `X-User-Id: {userId}`

**Query Params**: `page` (default: 0), `size` (default: 20)

**Response** `200 OK`
```json
{
  "content": [
    {
      "id": 1,
      "totalPrice": 35000,
      "status": "PAID",
      "createdAt": "2026-03-06T12:30:00",
      "items": [
        {
          "productId": 1,
          "productName": "Kotlin Book",
          "price": 35000,
          "quantity": 1,
          "subtotal": 35000
        }
      ]
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

### PUT /api/users/{id}
유저 수정

**Request**
```json
{
  "nickname": "new_nickname",
  "profileImageUrl": "https://example.com/photo.jpg"
}
```

**Response** `200 OK`

### DELETE /api/users/{id}
유저 삭제

**Response** `204 No Content`

---

## Product Service (port 8083)

### GET /api/products
상품 목록 (페이징)

**Query Params**: `page` (default: 0), `size` (default: 20), `sort`

**Response** `200 OK`
```json
{
  "content": [
    {
      "id": 1,
      "name": "Kotlin Book",
      "description": "Kotlin in Action",
      "price": 35000,
      "stock": 100,
      "status": "ACTIVE",
      "createdAt": "2026-03-06T12:00:00"
    }
  ],
  "totalElements": 1,
  "totalPages": 1,
  "number": 0,
  "size": 20
}
```

### GET /api/products/{id}
상품 상세 (Caffeine L1 + Redis L2 캐시)

**Response** `200 OK`
```json
{
  "id": 1,
  "name": "Kotlin Book",
  "description": "Kotlin in Action",
  "price": 35000,
  "stock": 100,
  "status": "ACTIVE",
  "createdAt": "2026-03-06T12:00:00"
}
```

### POST /api/products
상품 등록 → Kafka `product-created` 이벤트

**Request**
```json
{
  "name": "Kotlin Book",
  "description": "Kotlin in Action",
  "price": 35000,
  "stock": 100
}
```

**Response** `201 Created`

### PUT /api/products/{id}
상품 수정 → Kafka `product-updated` 이벤트, 캐시 evict

**Request**
```json
{
  "name": "Kotlin Book v2",
  "description": "Updated",
  "price": 38000,
  "stock": 50
}
```

**Response** `200 OK`

### DELETE /api/products/{id}
상품 삭제 (soft delete → INACTIVE), 캐시 evict

**Response** `204 No Content`

---

## Cart (Product Service, port 8083)

> Redis Hash 기반 장바구니 (key: `cart:{userId}`)

### GET /api/cart
장바구니 조회

**Headers**: `X-User-Id: {userId}`

**Response** `200 OK`
```json
{
  "userId": 1,
  "items": [
    {
      "productId": 1,
      "productName": "Kotlin Book",
      "price": 35000,
      "quantity": 2,
      "subtotal": 70000
    }
  ],
  "totalPrice": 70000
}
```

### POST /api/cart/items
장바구니 담기

**Headers**: `X-User-Id: {userId}`

**Request**
```json
{
  "productId": 1,
  "quantity": 2
}
```

**Response** `200 OK`

### PUT /api/cart/items/{productId}
수량 변경

**Headers**: `X-User-Id: {userId}`

**Request**
```json
{
  "quantity": 3
}
```

**Response** `200 OK`

### DELETE /api/cart/items/{productId}
장바구니에서 제거

**Headers**: `X-User-Id: {userId}`

**Response** `204 No Content`

### DELETE /api/cart
장바구니 비우기

**Headers**: `X-User-Id: {userId}`

**Response** `204 No Content`

---

## Order Service (port 8084)

### POST /api/orders
결제하기 (장바구니 → 주문 생성)

**Headers**: `X-User-Id: {userId}`

**Flow**:
1. Product Service에서 장바구니 조회
2. 각 상품 가격/재고 검증
3. 주문 생성 (DB 저장)
4. Kafka `stock-decrease` 이벤트 발행 (재고 차감)
5. 장바구니 비우기

**Response** `201 Created`
```json
{
  "id": 1,
  "userId": 1,
  "totalPrice": 70000,
  "status": "PAID",
  "createdAt": "2026-03-06T12:30:00",
  "items": [
    {
      "productId": 1,
      "productName": "Kotlin Book",
      "price": 35000,
      "quantity": 2,
      "subtotal": 70000
    }
  ]
}
```

### GET /api/orders
내 주문 목록

**Headers**: `X-User-Id: {userId}`

**Query Params**: `page`, `size`

**Response** `200 OK` (paginated)

### GET /api/orders/{id}
주문 상세

**Headers**: `X-User-Id: {userId}`

**Response** `200 OK`

---

## Gateway Routing (port 8080)

| Route | Target | Auth |
|-------|--------|------|
| `/api/auth/**` | `http://localhost:8081` | No |
| `/api/users/**` | `http://localhost:8082` | JWT required |
| `/api/cart/**` | `http://localhost:8083` | JWT required |
| `/api/products/**` | `http://localhost:8083` | JWT required |
| `/api/orders/**` | `http://localhost:8084` | JWT required |

---

## Infrastructure

```bash
# Start infra
docker-compose up -d   # Redis (6379) + Kafka (9092)

# Start services
./gradlew :auth:bootRun      # 8081
./gradlew :user:bootRun      # 8082
./gradlew :product:bootRun   # 8083
./gradlew :order:bootRun     # 8084
./gradlew :gateway:bootRun   # 8080
```

## Cache Strategy

| Service | Cache Name | Mode | L1 (Caffeine) | L2 (Redis) |
|---------|-----------|------|----------------|------------|
| Product | products | tiered | 500개, 5min | 30min |
| User | users | tiered | 300개, 5min | 15min |
| Order | orders | redis | - | 10min |

설정은 각 서비스의 `application.yml`에서 `khope.cache.caches.{name}.mode`로 변경:
- `caffeine`: 로컬 캐시만
- `redis`: Redis만
- `tiered`: L1(Caffeine) + L2(Redis)
