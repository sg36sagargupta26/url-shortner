# URL Shortener with Analytics — Technical Specification

## 1. Overview

A reactive URL shortener with real-time analytics, built with Spring Boot WebFlux, Redis, and PostgreSQL. Deployed on AWS ECS Fargate with ElastiCache Serverless and RDS PostgreSQL. CI/CD via GitHub Actions.

---

## 2. Functional Requirements

### 2.1 URL Shortening
- **Short codes**: Auto-generated random 7-character alphanumeric codes using Base62 encoding
- **Collision handling**: Retry with alternate hash inputs up to 3 attempts; fall back to UUID-based code
- **TTL**: Every link has an expiration. Mandatory field. Max TTL: 365 days
- **Default TTL**: 30 days if none specified
- **Custom TTL**: User sets via API: `ttl` in seconds or human-readable (`1d`, `30d`, `365d`)

### 2.2 Redirects
- **Default**: HTTP 302 (temporary), prevents browser caching of dead links
- **Configurable per link**: `redirect_type` = `302` or `301`
- **Expired links**: Return HTTP 410 Gone with `{"error": "link_expired", "expired_at": "..."}`

### 2.3 Rate Limiting
- **Threshold**: 1 POST request per minute per IP address
- **Bucket**: Redis token bucket per IP
- **Response**: HTTP 429 with `Retry-After` header

### 2.4 Authentication
- **None**: Single-user/internal tool. No login, no API keys, no roles

### 2.5 Analytics

#### Tracked per click:
| Field | Source | Storage |
|-------|--------|---------|
| Timestamp | Server clock | `clicks.clicked_at` (core) |
| IP Address | `X-Forwarded-For` header | `clicks.ip_hash` (core, hashed) |
| User Agent | `User-Agent` header | `clicks.user_agent` (core, raw) |
| Referrer URL | `Referer` header | `clicks.referrer` (core) |
| Country | IP to Geo lookup (MaxMind GeoLite2) | `clicks.country` (core) |
| City | IP to Geo lookup (MaxMind GeoLite2) | `clicks.city` (core) |
| Device Type | User-Agent parser (uap-java) | `clicks.metadata` (JSON) |
| Browser | User-Agent parser (uap-java) | `clicks.metadata` (JSON) |
| OS | User-Agent parser (uap-java) | `clicks.metadata` (JSON) |

#### Aggregations:
- **Real-time**: Redis HyperLogLog for unique visitors + counters per link
- **Dashboards**: Live click counts, country breakdown, device/browser/OS distribution, referrer summary

### 2.6 Data Retention
- **Raw clicks**: 30 days, then pruned by scheduled job
- **Aggregated stats**: Kept forever in `link_stats_daily` table (one row per link per day)

---

## 3. API Endpoints

### 3.1 Create Short URL
```
POST /api/v1/links
Content-Type: application/json

{
  "url": "https://example.com/very-long-path",
  "ttl": "30d",
  "redirect_type": "302"
}

Response 201:
{
  "short_url": "https://short.ly/abc1234",
  "short_code": "abc1234",
  "original_url": "https://example.com/very-long-path",
  "expires_at": "2026-07-16T19:00:00Z",
  "redirect_type": "302",
  "created_at": "2026-06-16T19:00:00Z"
}
```

### 3.2 Redirect
```
GET /{short_code}

Response 302:
Location: https://example.com/very-long-path

Response 410 (expired):
{"error": "link_expired", "expired_at": "2026-06-15T00:00:00Z"}
```

### 3.3 Link Analytics Summary
```
GET /api/v1/links/{short_code}/analytics

Response 200:
{
  "short_code": "abc1234",
  "total_clicks": 15420,
  "unique_visitors": 8923,
  "by_country": [
    {"country": "US", "count": 5000},
    {"country": "IN", "count": 3000}
  ],
  "by_device": [
    {"device": "Mobile", "count": 8000},
    {"device": "Desktop", "count": 6000}
  ],
  "by_browser": [
    {"browser": "Chrome", "count": 7000},
    {"browser": "Safari", "count": 4000}
  ],
  "by_os": [
    {"os": "iOS", "count": 5000},
    {"os": "Android", "count": 4000}
  ],
  "by_referrer": [
    {"referrer": "https://twitter.com", "count": 3000}
  ],
  "daily_breakdown": [
    {"date": "2026-06-16", "clicks": 500, "unique": 300},
    {"date": "2026-06-15", "clicks": 420, "unique": 280}
  ]
}
```

### 3.4 Health Check
```
GET /health
Response 200: {"status": "UP", "redis": "UP", "db": "UP"}
```

---

## 4. Architecture

```
                    ┌──────────────┐
                    │  CloudFront  │  (optional, CDN)
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │  ALB (AWS)   │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │ ECS Fargate  │  Spring Boot WebFlux
                    │   (2 tasks)  │
                    └──┬───────┬───┘
                       │       │
              ┌────────▼─┐  ┌──▼──────────┐
              │  Redis    │  │ PostgreSQL  │
              │ElastiCache│  │    RDS      │
              │Serverless │  │             │
              └──────────┘  └─────────────┘
```

### Data Flow:

**Short URL Creation:**
1. Validate URL format and TTL
2. Check Redis rate limiter for IP
3. Generate unique short code (Base62)
4. Store in PostgreSQL (`links` table)
5. Cache in Redis: `shortlink:{code}` -> URL + TTL + redirect_type
6. Return response

**Redirect:**
1. Hit Redis: `shortlink:{code}`  
   - Hit -> return redirect (302/301)
   - Miss -> query PostgreSQL
     - Found and not expired -> backfill Redis, redirect
     - Found and expired -> return 410
     - Not found -> return 404

**Click Recording (async, non-blocking):**
1. Parse headers (IP, User-Agent, Referer)
2. Publish click event to Redis Stream: `clicks:stream`
3. Consumer (same JVM, separate thread pool):
   - Geo-lookup via MaxMind
   - UA parsing via uap-java
   - Increment Redis counters (real-time)
   - Persist to PostgreSQL
4. Scheduled job: nightly rollup into `link_stats_daily`, purge clicks >30 days

---

## 5. Database Schema (PostgreSQL)

### `links`
| Column | Type | Notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| short_code | VARCHAR(10) UNIQUE NOT NULL | Indexed |
| original_url | TEXT NOT NULL | |
| redirect_type | VARCHAR(3) NOT NULL DEFAULT '302' | '301' or '302' |
| expires_at | TIMESTAMPTZ NOT NULL | |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |
| is_active | BOOLEAN NOT NULL DEFAULT TRUE | |

Indexes: `idx_short_code` (unique), `idx_expires_at`, `idx_created_at`

### `clicks`
| Column | Type | Notes |
|--------|------|-------|
| id | BIGSERIAL PK | |
| link_id | BIGINT NOT NULL FK -> links.id | Partitioned by `clicked_at` |
| clicked_at | TIMESTAMPTZ NOT NULL DEFAULT NOW() | |
| ip_hash | VARCHAR(64) | SHA-256 hashed |
| country | VARCHAR(2) | ISO 3166-1 alpha-2 |
| city | VARCHAR(100) | |
| user_agent | TEXT | Raw UA string |
| referrer | TEXT | |
| metadata | JSONB | device, browser, os |

Indexes: `idx_clicks_link_id_clicked_at`, `idx_clicks_clicked_at`  
Partitioning: By month on `clicked_at` (auto-managed)

### `link_stats_daily`
| Column | Type | Notes |
|--------|------|-------|
| link_id | BIGINT NOT NULL FK -> links.id | |
| date | DATE NOT NULL | |
| total_clicks | BIGINT NOT NULL DEFAULT 0 | |
| unique_visitors | BIGINT NOT NULL DEFAULT 0 | |
| country_breakdown | JSONB | |
| device_breakdown | JSONB | |
| browser_breakdown | JSONB | |
| os_breakdown | JSONB | |
| referrer_breakdown | JSONB | |

PK: (`link_id`, `date`)

---

## 6. Redis Data Model

| Key | Type | TTL | Purpose |
|-----|------|-----|---------|
| `shortlink:{code}` | Hash | Link TTL | `{url, redirect_type, expires_at}` |
| `ratelimit:{ip}` | String | 60s | Token bucket count |
| `clicks:total:{link_id}` | String | infinite | Real-time total clicks |
| `clicks:unique:{link_id}` | HyperLogLog | infinite | Real-time unique visitors |
| `clicks:country:{link_id}` | Hash | infinite | Real-time country counts |
| `clicks:device:{link_id}` | Hash | infinite | Real-time device counts |
| `clicks:browser:{link_id}` | Hash | infinite | Real-time browser counts |
| `clicks:os:{link_id}` | Hash | infinite | Real-time OS counts |
| `clicks:referrer:{link_id}` | Hash | 30d | Real-time referrer counts |
| `clicks:stream` | Stream | 30d | Async click processing |

---

## 7. Project Structure

```
url-shortener/
├── build.gradle.kts
├── settings.gradle.kts
├── docker-compose.yml          # Local dev: Redis + Postgres
├── Dockerfile
├── .github/
│   └── workflows/
│       └── deploy.yml          # CI/CD pipeline
├── terraform/                  # AWS Infrastructure
│   ├── main.tf
│   ├── ecs.tf
│   ├── rds.tf
│   ├── elasticache.tf
│   ├── variables.tf
│   └── outputs.tf
├── src/main/java/com/shortly/
│   ├── ShortlyApplication.java
│   ├── config/
│   │   ├── RedisConfig.java
│   │   ├── DatabaseConfig.java
│   │   ├── WebConfig.java
│   │   └── AppConfig.java
│   ├── controller/
│   │   ├── LinkController.java
│   │   ├── RedirectController.java
│   │   └── HealthController.java
│   ├── dto/
│   │   ├── CreateLinkRequest.java
│   │   ├── CreateLinkResponse.java
│   │   ├── AnalyticsResponse.java
│   │   └── ErrorResponse.java
│   ├── model/
│   │   ├── Link.java
│   │   ├── Click.java
│   │   └── LinkStatsDaily.java
│   ├── repository/
│   │   ├── LinkRepository.java
│   │   ├── ClickRepository.java
│   │   └── LinkStatsDailyRepository.java
│   ├── service/
│   │   ├── ShortCodeGenerator.java
│   │   ├── LinkService.java
│   │   ├── RedirectService.java
│   │   ├── ClickProcessingService.java
│   │   ├── AnalyticsService.java
│   │   ├── GeoLocationService.java
│   │   ├── UserAgentParserService.java
│   │   ├── RateLimiterService.java
│   │   └── ExpiryCleanupJob.java
│   └── util/
│       ├── TtlParser.java
│       └── IpHashUtil.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-prod.yml
│   └── db/migration/           # Flyway migrations
│       ├── V1__create_links.sql
│       ├── V2__create_clicks.sql
│       └── V3__create_link_stats_daily.sql
├── src/test/java/com/shortly/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   └── integration/
└── MaxMind/GeoLite2-City.mmdb   # Not committed; downloaded in CI
```

---

## 8. Technology Versions

| Component | Version |
|-----------|---------|
| Java | 21 |
| Spring Boot | 3.3.x |
| Spring WebFlux | (bundled) |
| Spring Data R2DBC | 1.3.x |
| Redis Client | Lettuce (bundled with Spring) |
| Flyway | 10.x |
| Gradle | 8.x with Kotlin DSL |
| Testcontainers | 1.19.x (integration tests) |
| Terraform | 1.9.x |
| AWS Provider | ~> 5.0 |
| MaxMind GeoLite2 | (latest) |
| uap-java | 1.5.x |

---

## 9. Non-functional Requirements

| Requirement | Target |
|-------------|--------|
| Redirect latency (p95) | < 10ms (Redis hit), < 30ms (DB fallback) |
| Create latency (p95) | < 100ms |
| Availability | 99.9% |
| Throughput | 1,000 redirects/sec per task |
| MaxMind DB refresh | Monthly via GitHub Actions cron |
| Flyway migrations | Auto-run on startup |
