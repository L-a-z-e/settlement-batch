# Settlement Batch Benchmark

Spring Batch 기반 정산 배치의 성능 전략을 **Reader / Writer / Insert / 병렬처리** 4가지 축으로 실측 비교하는 벤치마크 프로젝트.

각 축마다 기본 방식과 최적화 방식을 1:1 대응시켜, 동일 데이터셋(100만~2000만 건)에서 처리 시간과 Throughput을 직접 측정한다.

## Tech Stack

| 구분 | 기술 |
|------|------|
| Application | Spring Boot 3.5.13, Spring Batch 5, Java 17 |
| Database | MySQL 8.4 |
| Monitoring | Micrometer + Prometheus + Grafana |
| Infra | Docker Compose |
| Build | Gradle |

## 벤치마크 케이스

8개 케이스를 Spring Profile로 분리하여 독립 실행한다.

### Reader 비교 — OFFSET vs Zero-Offset

| Case | Profile | 전략 | 핵심 |
|------|---------|------|------|
| 1 | `reader-offset` | `JpaPagingItemReader` (OFFSET/LIMIT) | 페이지가 뒤로 갈수록 느려짐 |
| 2 | `reader-zero-offset` | `WHERE id > lastId` (Keyset Pagination) | 항상 일정한 성능 |

### Writer 비교 — JPA vs JDBC Batch

| Case | Profile | 전략 | 핵심 |
|------|---------|------|------|
| 3 | `writer-jpa` | JPA merge (Dirty Checking) | 영속성 컨텍스트 스냅샷 비교 + 개별 UPDATE |
| 4 | `writer-jdbc-batch` | `jdbcTemplate.batchUpdate()` | 벌크 UPDATE/INSERT 직접 제어 |

### Insert 비교 — IDENTITY vs JDBC Batch

| Case | Profile | 전략 | 핵심 |
|------|---------|------|------|
| 5 | `insert-identity` | `JPA saveAll()` (IDENTITY 전략) | persist() 시 즉시 INSERT → Batch 비활성화 |
| 6 | `insert-jdbc-rewrite` | JDBC Batch + `rewriteBatchedStatements` | Multi-Value INSERT로 재작성 |

### 병렬처리 비교 — Sequential vs Partitioning

| Case | Profile | 전략 | 핵심 |
|------|---------|------|------|
| 7 | `sequential` | 싱글 스레드 | 기준선 |
| 8 | `partitioning` | 4파티션 병렬 (ID 범위 분할) | 파티션별 독립 Reader/Writer, lock 없음 |

## 실측 결과

> 환경: Docker Compose (MySQL 8.4, Java 17, Chunk Size 1000)

### 100만 건

| Case | 대상 | Duration | Throughput | 개선율 |
|------|------|----------|------------|--------|
| 1 | Reader OFFSET | 782.8s | 1,278/s | — |
| 2 | Reader Zero-Offset | 3.7s | 271,444/s | **212x** |
| 3 | Writer JPA | 105.3s | 9,497/s | — |
| 4 | Writer JDBC Batch | 39.8s | 25,101/s | **2.6x** |
| 5 | Insert IDENTITY | 9.4s | 10,638/s | — |
| 6 | Insert JDBC Batch | 1.6s | 64,433/s | **6x** |
| 7 | Sequential | 40.3s | 24,794/s | — |
| 8 | Partitioning (4) | 15.6s | 63,988/s | **2.6x** |

### 2000만 건

| Case | 대상 | 결과 |
|------|------|------|
| 1 vs 2 | Reader | OFFSET **중단 불가** (Chunk #200에서 16.5s/chunk) vs Zero-Offset **58.2s** |
| 3 vs 4 | Writer | JPA 34분 vs JDBC Batch 13분 (**2.6x**) |
| 5 vs 6 | Insert | IDENTITY vs JDBC Batch (**6.7x**) |
| 7 vs 8 | Parallel | Sequential 41분 vs Partitioning 21분 (**1.9x**) |

### Chunk 타이밍 추이 (Reader)

OFFSET 방식은 데이터가 커질수록 성능이 악화되지만, Zero-Offset은 일정하다.

```
OFFSET:      Chunk #1 → 207ms  |  Chunk #1000 → 828ms  (4배 악화)
Zero-Offset: Chunk #1 →  45ms  |  Chunk #1000 →   3ms  (일정)
```

## 핵심 발견

### 1. 배치 성능의 80%는 Reader가 결정한다

OFFSET → Zero-Offset 전환만으로 100만 건 **212배**, 2000만 건은 **완료 불가 → 58초**. 다른 최적화를 하기 전에 Reader부터 점검해야 한다.

### 2. JPA Dirty Checking은 배치에서 병목이다

영속성 컨텍스트의 스냅샷 비교 + 개별 UPDATE SQL 생성은 OLTP에선 편리하지만, 대량 처리에선 `jdbcTemplate.batchUpdate()`가 2.6배 빠르다.

### 3. IDENTITY 전략은 Batch Insert를 비활성화한다

Hibernate는 IDENTITY 사용 시 새 Key를 미리 알 수 없어 JDBC Batch를 투명하게 비활성화한다. `rewriteBatchedStatements=true` 옵션과 JDBC 직접 사용으로 6배 개선.

### 4. Partitioning의 실측은 이론보다 낮다

4파티션이면 이론적 4배지만, 실측은 2.6배(100만 건) / 1.9배(2000만 건). Docker CPU 제약과 커넥션 풀 경합이 원인이다. 실무에서는 `hikari.maximum-pool-size = 파티션 수 + α` 설정이 필수.

## 프로젝트 구조

```
settlement-batch/
├── src/main/java/com/settlementbatch/
│   ├── domain/          # Order, Settlement 엔티티
│   ├── reader/          # Case 1-2: Reader 전략
│   ├── writer/          # Case 3-4: Writer 전략
│   ├── insert/          # Case 5-6: Insert 전략
│   ├── parallel/        # Case 7-8: 병렬처리 전략
│   ├── common/          # 벤치마크 공통 (타이밍, 로깅, 실행)
│   └── init/            # 테스트 데이터 초기화
├── monitoring/
│   ├── prometheus/      # Prometheus 설정
│   └── grafana/         # Grafana 데이터소스/대시보드
├── docker-compose.yml
├── Dockerfile
├── run-all.sh           # 전체 벤치마크 실행
└── run-remaining.sh     # 나머지 케이스 이어서 실행
```

## 도메인 모델

```
Order (주문)                    Settlement (정산)
┌─────────────────┐            ┌─────────────────────┐
│ id (PK)         │───────────▶│ id (PK)             │
│ sellerId        │            │ orderId (UK)        │
│ amount          │            │ sellerId            │
│ status          │            │ amount              │
│ orderDate       │            │ fee (amount * 3%)   │
│ createdAt       │            │ netAmount           │
└─────────────────┘            │ settledAt           │
                               └─────────────────────┘
 status: CONFIRMED → SETTLED
```

## 실행 방법

### 전체 벤치마크 실행

```bash
./run-all.sh
```

8개 케이스를 순차 실행한다. MySQL 헬스체크 후 자동 시작.

### 개별 케이스 실행

```bash
# Docker
BENCHMARK_CASE=reader-zero-offset docker compose run --rm app

# 로컬 (MySQL 필요)
./gradlew bootRun --args='--spring.profiles.active=reader-zero-offset'
```

### 모니터링

- **Grafana**: http://localhost:3000 (admin / admin)
- **Prometheus**: http://localhost:9090

## 주요 설정

```yaml
# application.yml
spring.datasource.url: jdbc:mysql://localhost:3307/benchmark?rewriteBatchedStatements=true
spring.jpa.properties.hibernate:
  jdbc.batch_size: 100
  order_inserts: true
  order_updates: true

benchmark:
  data-count: 20000000       # 테스트 데이터 (기본 2000만 건)
  insert-data-count: 100000  # Insert 벤치마크용 (10만 건)
  chunk-size: 1000           # Chunk 크기

spring.datasource.hikari:
  maximum-pool-size: 10
  minimum-idle: 5
```
