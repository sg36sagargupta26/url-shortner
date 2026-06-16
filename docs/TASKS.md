# URL Shortener with Analytics — Task Breakdown

## Dependency Graph

```
Phase 0: Foundation
  +-- Task 0.1: Project scaffolding and Gradle build
  +-- Task 0.2: Docker Compose (Redis + Postgres for local dev)
  +-- Task 0.3: Flyway migrations (all 3 SQL scripts)

Phase 1: Core Domain (parallel)
  +-- Task 1.1: Models and R2DBC repositories
  +-- Task 1.2: Redis config + cache service
  +-- Task 1.3: Short code generator (Base62)

Phase 2: Services Layer (parallel with Phase 1 completion)
  +-- Task 2.1: LinkService (create, lookup, TTL handling)
  +-- Task 2.2: RateLimiterService (Redis token bucket)
  +-- Task 2.3: GeoLocationService (MaxMind integration)
  +-- Task 2.4: UserAgentParserService (uap-java)

Phase 3: API Layer (parallel)
  +-- Task 3.1: DTOs and validation
  +-- Task 3.2: LinkController + RedirectController
  +-- Task 3.3: HealthController
  +-- Task 3.4: Error handling (GlobalErrorHandler)

Phase 4: Analytics Engine (parallel)
  +-- Task 4.1: ClickProcessingService (stream consumer)
  +-- Task 4.2: AnalyticsService (Redis counters + DB queries)
  +-- Task 4.3: ExpiryCleanupJob (scheduled pruning + rollups)

Phase 5: Infrastructure (parallel)
  +-- Task 5.1: Dockerfile (multi-stage build)
  +-- Task 5.2: Terraform (ECS, RDS, ElastiCache, ALB)
  +-- Task 5.3: GitHub Actions deploy workflow

Phase 6: Testing
  +-- Task 6.1: Unit tests (all services)
  +-- Task 6.2: Integration tests (Testcontainers)
  +-- Task 6.3: Controller tests (WebTestClient)
```

---

## Detailed Tasks

### Task 0.1: Project Scaffolding and Gradle Build
**Priority**: P0 | **Depends on**: Nothing
**Description**: Initialize Spring Boot project with Gradle Kotlin DSL, all dependencies.
**Files**: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`
**Dependencies**: spring-boot-starter-webflux, spring-boot-starter-data-r2dbc, r2dbc-postgresql, spring-boot-starter-data-redis-reactive, flyway-core, flyway-database-postgresql, lombok, uap-java, maxmind-geoip2, testcontainers
**Acceptance**: `./gradlew build` succeeds

### Task 0.2: Docker Compose
**Priority**: P0 | **Depends on**: Nothing
**Description**: `docker-compose.yml` with Redis 7 and PostgreSQL 16 for local development.
**Files**: `docker-compose.yml`
**Acceptance**: `docker compose up -d` starts both services; health checks pass

### Task 0.3: Flyway Migrations
**Priority**: P0 | **Depends on**: Nothing (schema from spec)
**Description**: Three migration scripts: create `links`, `clicks` (partitioned), `link_stats_daily`.
**Files**: `V1__create_links.sql`, `V2__create_clicks.sql`, `V3__create_link_stats_daily.sql`
**Acceptance**: Flyway runs on startup, tables exist with correct schema

### Task 1.1: Models and R2DBC Repositories
**Priority**: P0 | **Depends on**: 0.1, 0.3
**Description**: Models with Lombok and Spring Data R2DBC repositories.
**Files**: `Link.java`, `Click.java`, `LinkStatsDaily.java`, `LinkRepository.java`, `ClickRepository.java`, `LinkStatsDailyRepository.java`
**Acceptance**: CRUD operations work via repository tests

### Task 1.2: Redis Configuration and Cache Service
**Priority**: P0 | **Depends on**: 0.1
**Description**: Redis config using Lettuce, `ReactiveRedisTemplate`. Includes `RedisCacheService` with methods for caching links, incrementing counters, HyperLogLog operations.
**Files**: `RedisConfig.java`, `RedisCacheService.java`
**Acceptance**: All Redis ops work with Testcontainers

### Task 1.3: Short Code Generator
**Priority**: P0 | **Depends on**: Nothing (pure logic)
**Description**: Base62 encoding from random source + counter. Collision detection with retry.
**Files**: `ShortCodeGenerator.java`
**Acceptance**: Generates 7-char unique codes in <1ms

### Task 2.1: LinkService
**Priority**: P1 | **Depends on**: 1.1, 1.2, 1.3
**Description**: Orchestrates URL creation: validate, rate limit, generate code, save to DB, cache in Redis. Redis-first lookup pattern.
**Files**: `LinkService.java`, `TtlParser.java`
**Acceptance**: Create 100 links in test, all codes unique, all cached

### Task 2.2: RateLimiterService
**Priority**: P1 | **Depends on**: 1.2
**Description**: Token bucket using Redis INCR + EXPIRE. Check on POST /api/v1/links.
**Files**: `RateLimiterService.java`
**Acceptance**: 2nd request within window returns false; 1st returns true

### Task 2.3: GeoLocationService
**Priority**: P1 | **Depends on**: Nothing (external dep)
**Description**: Wraps MaxMind GeoLite2 database reader. Returns country + city for an IP.
**Files**: `GeoLocationService.java`
**Acceptance**: Known IP -> correct country/city

### Task 2.4: UserAgentParserService
**Priority**: P1 | **Depends on**: Nothing (external dep)
**Description**: Wraps uap-java. Parses UA into device, browser, OS. LRU cached.
**Files**: `UserAgentParserService.java`
**Acceptance**: Common UAs parse correctly

### Task 3.1: DTOs and Validation
**Priority**: P1 | **Depends on**: Nothing (pure data classes)
**Description**: Request/response DTOs with Bean Validation constraints.
**Files**: `CreateLinkRequest.java`, `CreateLinkResponse.java`, `AnalyticsResponse.java`, `ErrorResponse.java`
**Acceptance**: Invalid URLs rejected, TTL parsed correctly

### Task 3.2: LinkController + RedirectController
**Priority**: P1 | **Depends on**: 2.1, 2.2, 3.1
**Description**: Reactive controllers. POST /api/v1/links and GET /{short_code}.
**Files**: `LinkController.java`, `RedirectController.java`
**Acceptance**: Integration tests pass

### Task 3.3: HealthController
**Priority**: P2 | **Depends on**: 1.1, 1.2
**Description**: /health endpoint checking Redis PING + DB connection.
**Files**: `HealthController.java`
**Acceptance**: Returns UP status when healthy, DOWN when not

### Task 3.4: Global Error Handling
**Priority**: P2 | **Depends on**: 3.2
**Description**: @RestControllerAdvice for consistent error responses.
**Files**: `GlobalErrorHandler.java`
**Acceptance**: All error scenarios return correct HTTP codes + JSON

### Task 4.1: ClickProcessingService
**Priority**: P2 | **Depends on**: 2.1, 2.3, 2.4, 1.1
**Description**: Publishes click events to Redis Stream. Separate consumer parses geo/UA, persists to DB, updates counters.
**Files**: `ClickProcessingService.java`
**Acceptance**: Click recorded without blocking redirect response

### Task 4.2: AnalyticsService
**Priority**: P2 | **Depends on**: 1.1, 1.2, 4.1
**Description**: Merges real-time Redis counters + historical Postgres data for analytics endpoint.
**Files**: `AnalyticsService.java`
**Acceptance**: Analytics endpoint returns real-time + historical data

### Task 4.3: ExpiryCleanupJob
**Priority**: P2 | **Depends on**: 1.1, 1.2
**Description**: Scheduled job (cron 2am). Purges clicks >30 days, rolls up Redis counters to link_stats_daily, invalidates expired caches.
**Files**: `ExpiryCleanupJob.java`
**Acceptance**: After 30 days of test data, old clicks purged, rollups exist

### Task 5.1: Dockerfile
**Priority**: P1 | **Depends on**: 0.1
**Description**: Multi-stage build: Gradle build -> JRE 21 slim runtime. Exposes 8080.
**Files**: `Dockerfile`
**Acceptance**: docker build succeeds and runs

### Task 5.2: Terraform Infrastructure
**Priority**: P1 | **Depends on**: Nothing (declarative)
**Description**: ECS Fargate, task definition, service, ALB, RDS PostgreSQL, ElastiCache Serverless, security groups, IAM roles.
**Files**: `terraform/main.tf`, `terraform/ecs.tf`, `terraform/rds.tf`, `terraform/elasticache.tf`, `terraform/variables.tf`, `terraform/outputs.tf`
**Acceptance**: terraform plan produces no errors

### Task 5.3: GitHub Actions Workflow
**Priority**: P1 | **Depends on**: 5.1
**Description**: On push to main: build, test, Docker build, push to ECR, deploy to ECS. Monthly MaxMind DB update cron.
**Files**: `.github/workflows/deploy.yml`
**Acceptance**: Push triggers build, ECR image pushed, ECS service updated

### Tasks 6.1-6.3: Testing
**Priority**: P2 | **Depends on**: All Phase 1-4 tasks
**Description**: Unit tests (mocked services), integration tests (Testcontainers Redis+Postgres), controller tests (WebTestClient).
**Files**: `src/test/java/com/shortly/**/*Test.java`
**Acceptance**: ./gradlew test passes
