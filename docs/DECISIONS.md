# Architecture Decision Records — URL Shortener with Analytics

This document captures every significant design decision, the alternatives considered, the final choice, and the rationale.

---

## 1. Short Code Format

**Decision**: Auto-generated random 7-character Base62 codes

| Option | Pros | Cons |
|--------|------|------|
| Random Base62 (chosen) | Short, URL-friendly, no collision risk with large space, unpredictable | Cannot be customized by user |
| Custom user aliases | User-friendly, brandable | Collision risk, requires profanity filter, moderation overhead |
| Both | Best UX | 2x complexity, requires alias validation system |
| UUID-based | Guaranteed unique, simple | Too long (36 chars), defeats purpose of URL shortener |
| Sequential integers | Extremely short, simple | Predictable (security risk — users can enumerate all links) |

**Why**: Simplicity for an MVP. 7-char Base62 gives 62^7 = ~3.5 trillion combinations — more than enough. Custom aliases can be added later as a feature enhancement without rewriting core logic.

---

## 2. Link Expiration

**Decision**: Every link expires; user sets TTL. Default 30 days, max 365 days.

| Option | Pros | Cons |
|--------|------|------|
| Permanent only | Simple, no cleanup needed | Links accumulate forever, storage grows unbounded, dead links never reclaimed |
| Mandatory TTL (chosen) | Predictable storage, automatic cleanup, prevents link rot | Slightly more complex, users MUST think about expiration |
| Optional TTL | Flexible | Most users never set it, same as permanent for practical purposes |

**Why**: A URL shortener without expiration becomes a link graveyard. Mandatory TTL with a generous 30-day default keeps the database lean while giving users control.

---

## 3. Redirect HTTP Status Code

**Decision**: 302 (temporary) as default, configurable to 301 per link

| Option | Pros | Cons |
|--------|------|------|
| 301 (permanent) | Browsers cache the redirect, fewer requests to our server | Browser caches dead redirect if link expires — user lands on cached 301 even after link is gone |
| 302 (chosen default) | Always hits our server, works with expiration, we can track every click | Every redirect hits our server (more load — but needed for analytics anyway) |
| 307/308 | Preserves HTTP method | Overkill for a URL shortener |

**Why**: Since every link expires, a browser-cached 301 would point to a dead link after TTL. 302 ensures redirects always pass through our server — which is essential for accurate analytics tracking. Users who explicitly want 301 (e.g., SEO purposes) can set it per-link.

---

## 4. Rate Limiting

**Decision**: 1 POST request per minute per IP address (configurable)

| Option | Pros | Cons |
|--------|------|------|
| No rate limit | Simplest | Open to abuse — anyone can flood with URL creation requests, exhaust DB storage |
| IP-based (chosen) | Simple, no auth needed, prevents basic abuse | Shared IPs (corporate, university) affect all users behind NAT |
| Token-based (API key) | Fair per-user, bypasses NAT issue | Requires user accounts/auth — rejected per requirement #5 |
| Sliding window | More accurate than fixed window | Slightly more complex Redis logic, minimal benefit at 1 req/min |

**Why**: With no authentication, IP-based is the only practical option. 1 req/min is intentionally strict for an MVP — the limit is configurable in application.yml and can be raised later. Redis makes this near-zero overhead.

---

## 5. Authentication

**Decision**: No authentication — single-user/internal tool

| Option | Pros | Cons |
|--------|------|------|
| No auth (chosen) | Simplest, fastest to build | No multi-tenancy, no per-user analytics isolation |
| JWT/OAuth2 | Industry standard, multi-user | Significant complexity (auth server, token management, user DB) |
| API Key (header) | Simple, stateless | Still needs key management, unwarranted for single user |
| Basic Auth | Very simple | Passwords over HTTP without TLS is unsafe, no session management |

**Why**: User explicitly chose "single user without authentication." This keeps the scope minimal for the MVP. Multi-tenancy can be layered on later.

---

## 6. Analytics: What to Track

**Decision**: Country, city, device type, browser, OS, and referrer URL (+ implicit: timestamp, click count)

| Option | Pros | Cons |
|--------|------|------|
| Minimal (click count only) | Trivial to implement | No meaningful insights — "1000 clicks" tells you nothing about your audience |
| Geography only | Useful for marketing targeting | Misses technical insights (mobile vs desktop) |
| Full (chosen) | Complete user picture, rich dashboards | More storage, needs MaxMind + uap-java dependencies |
| Full + heatmaps | Ultimate analytics | Major complexity overkill for MVP, needs client-side JS injection |

**Why**: Geo + device + browser + OS + referrer gives a 360-degree view of link performance. The two external dependencies (MaxMind GeoLite2 and uap-java) are well-maintained, battle-tested, and free.

---

## 7. Analytics Dashboard: Real-Time vs Batch

**Decision**: Real-time dashboards using Redis counters

| Option | Pros | Cons |
|--------|------|------|
| Batch (nightly jobs only) | Simple, consistent with rollup jobs | Analytics lag 24 hours, cannot see "what's happening right now" |
| Real-time (chosen) | Instant feedback, feels alive | Two data sources to merge (Redis for real-time, Postgres for historical) |
| Pure Postgres (live queries) | Single source of truth | Expensive queries on large click tables, won't scale, latency spikes |

**Why**: Real-time analytics aligns with the core value proposition — users want to see click counts update live. Redis HyperLogLog for unique visitors and Hash counters give accurate-enough real-time data with O(1) complexity. Nightly rollups into Postgres handle historical accuracy.

---

## 8. Data Retention

**Decision**: Raw clicks deleted after 30 days, daily aggregates kept forever

| Option | Pros | Cons |
|--------|------|------|
| Keep everything | Full fidelity forever | Clicks table grows unbounded, queries get slower, storage cost balloons |
| 7-day retention | Very low storage cost | Not enough data for meaningful trends or month-over-month analysis |
| 30-day + aggregates (chosen) | Good balance — 30 days of raw data for debugging, trends forever via aggregates | Slightly more complex (needs scheduled cleanup job) |
| 90-day retention | More raw data | 3x storage cost over 30-day, diminishing returns for a URL shortener |

**Why**: 30 days of raw click data lets users debug recent campaigns and see full detail. Beyond that, aggregated daily stats provide trend analysis without the storage cost. The scheduled cleanup job is a simple cron — minimal operational complexity.

---

## 9. Framework: WebFlux vs Spring MVC

**Decision**: Spring Boot WebFlux (reactive)

| Option | Pros | Cons |
|--------|------|------|
| Spring MVC (traditional) | Familiar, huge ecosystem, easier debugging | Thread-per-request model: 200 threads = 200 concurrent connections max. Redirects are I/O-bound — threads idle waiting for Redis/DB |
| WebFlux (chosen) | Non-blocking I/O, handles 10,000+ concurrent connections on few threads, perfect for redirect-heavy workload | Steeper learning curve, some libraries not reactive-compatible, requires R2DBC instead of JPA/Hibernate |
| Vert.x / Quarkus | Even faster startup, smaller footprint | Smaller ecosystem, less community support |

**Why**: The primary workload is redirects — 99% I/O-bound (Redis lookup, then either redirect or DB query). WebFlux handles thousands of concurrent redirects on a handful of event-loop threads, avoiding thread pool exhaustion. Spring Boot integration with Lettuce (Redis) and R2DBC (Postgres) makes the stack fully non-blocking end-to-end.

---

## 10. Redis Usage

**Decision**: Redis handles shortcode cache, rate limiting, AND real-time analytics counters

| Option | Pros | Cons |
|--------|------|------|
| Cache only | Simplest Redis usage | Rate limiting needs a separate solution, analytics counters hit Postgres (slow) |
| Cache + rate limiting | Good separation | Analytics counters on Postgres would cause write amplification on every click |
| Full (chosen) | Single infrastructure for all real-time needs, sub-ms performance for all three | Redis becomes critical path — if Redis is down, all three features degrade |
| Separate Redis instances | Isolation | Costly overkill for MVP — can separate later if needed |

**Why**: Redis is exceptionally good at all three workloads — caching (GET), rate limiting (INCR + EXPIRE), and counters (HINCRBY, PFADD). Consolidating these avoids managing multiple infrastructure pieces. The downside (Redis as single point of failure) is mitigated by ElastiCache Serverless auto-failover.

---

## 11. PostgreSQL: Normalized vs JSON-Heavy

**Decision**: Normalized core fields, JSONB for optional/extensible metadata

| Option | Pros | Cons |
|--------|------|------|
| Fully normalized (separate tables for countries, devices, browsers) | Queryable with SQL JOINs, referential integrity | 5+ extra tables, complex migrations, slower writes |
| Pure JSONB (`clicks` has one `data` JSONB column) | Extremely flexible, no migrations for new fields | No SQL indexing on nested fields, harder to query |
| Hybrid (chosen) | Frequently-queried fields (country, city, link_id, clicked_at) are indexed columns; less-common fields (device details) in JSONB | Slightly more complex schema design upfront |

**Why**: Core analytics queries (clicks by country, clicks per day) need fast SQL WHERE clauses and indexes — these must be columnar. Device/browser/OS breakdowns are secondary and fit well in JSONB. This also makes it easy to add new analytics dimensions later without schema migrations.

---

## 12. Build Tool: Maven vs Gradle

**Decision**: Gradle with Kotlin DSL (build.gradle.kts)

| Option | Pros | Cons |
|--------|------|------|
| Maven (XML) | Most Java devs know it, stable, IDE support excellent | XML is verbose, complex custom logic is painful |
| Gradle Groovy DSL | Concise, fast, flexible | Groovy is a dead-end language for Gradle |
| Gradle Kotlin DSL (chosen) | Type-safe, IDE autocomplete, first-class Gradle citizen, same language as the project (Kotlin/Java ecosystem) | Slightly slower first build than Groovy DSL |

**Why**: Gradle's incremental builds and caching are significantly faster than Maven for multi-module projects. Kotlin DSL gives compile-time safety for build scripts where Groovy would fail at runtime. The entire Spring ecosystem has moved to Gradle.

---

## 13. AWS Compute: ECS Fargate vs Alternatives

**Decision**: ECS Fargate

| Option | Monthly Cost (low traffic) | Scaling | Maintenance |
|--------|---------------------------|---------|-------------|
| EC2 (t3.small) | ~$15/mo | Manual, slow | OS patches, Docker runtime updates |
| EC2 + ASG | ~$20/mo | Automatic (ASG) | OS patches, AMI management |
| ECS on EC2 | ~$20/mo + ECS overhead | Automatic (ECS + ASG) | EC2 patching, capacity planning |
| ECS Fargate (chosen) | ~$25/mo (0.25 vCPU, 0.5GB) | Instant, serverless | None |
| EKS | ~$73/mo (control plane) + compute | Kubernetes-native | Complex operations |
| Elastic Beanstalk | ~$20/mo | Limited | Platform updates managed |

**Why**: Fargate eliminates all server management — no patching, no capacity planning, no AMI updates. For a URL shortener with spiky/unpredictable redirect traffic, the pay-per-task model means near-zero cost during quiet periods. The ~$5-10/mo premium over EC2 is worth the eliminated operational burden for a single-developer project.

---

## 14. PostgreSQL: RDS vs Aurora

**Decision**: RDS PostgreSQL (not Aurora)

| Option | Pros | Cons |
|--------|------|------|
| RDS (chosen) | Simpler, familiar, sufficient for this scale, cheaper at steady low load | Manual scaling when traffic grows |
| Aurora Standard | Higher throughput, auto-scaling storage | 20-30% more expensive, overkill for MVP |
| Aurora Serverless v2 | Auto-scales CPU/RAM, pay-per-capacity | ACU minimum = 0.5 (~$43/mo base cost) vs RDS t3.micro (~$15/mo) |

**Why**: At MVP scale (thousands of clicks/day, not millions), a small RDS instance handles the load easily. Aurora's benefits (auto-scaling, higher throughput) are wasted on this traffic level. RDS t3.micro with 20GB gp3 storage runs ~$15-20/month. Can migrate to Aurora later if traffic warrants it.

---

## 15. Redis: ElastiCache Serverless vs Provisioned

**Decision**: ElastiCache Serverless

| Option | Pros | Cons |
|--------|------|------|
| Provisioned (cache.t3.micro) | ~$12/mo, predictable cost | Manual scaling, need to choose instance size, wasted capacity during quiet periods |
| Serverless (chosen) | Auto-scales, pay per GB + per request, no capacity planning | Slightly higher per-GB cost; variable monthly bill |
| Self-managed on EC2 | Cheapest raw cost | You manage Redis — patching, failover, backups |

**Why**: ElastiCache Serverless eliminates the capacity planning problem — traffic can spike from 0 to 10,000 requests/second without pre-provisioning. At the expected volume, the cost difference vs provisioned is negligible (~$15-25/mo vs ~$12/mo). For a Redis-critical application (cache miss = DB hit = latency spike), auto-scaling is worth the small premium.

---

## 16. CI/CD: GitHub Actions vs AWS CodePipeline

**Decision**: GitHub Actions

| Option | Pros | Cons |
|--------|------|------|
| GitHub Actions (chosen) | Free for public repos, 2000 min/mo for private, huge ecosystem, Gradle caching built-in, ECR/ECS deploy actions exist | Needs AWS OIDC federation for secure credentials |
| AWS CodePipeline + CodeBuild | Native AWS integration, one-click ECS deploy | $1/pipeline/month + per-minute build costs, limited third-party actions |
| Jenkins | Maximum control, unlimited customization | Server to manage, plugin hell, overkill |

**Why**: GitHub Actions is effectively free for this project. The `aws-actions/configure-aws-credentials` action with OIDC federation eliminates long-lived AWS credentials. Pre-built actions for Docker build, ECR push, and ECS deploy make the pipeline ~30 lines of YAML. AWS CodePipeline adds cost without adding value.

---

## 17. Project Structure: Package by Layer vs Feature

**Decision**: Package by layer (`controller/`, `service/`, `repository/`, `model/`, `dto/`, `config/`)

| Option | Pros | Cons |
|--------|------|------|
| Package by layer (chosen) | Familiar to most Spring devs, clear separation of concerns, easy to navigate | Cross-cutting changes touch multiple packages |
| Package by feature (`link/`, `analytics/`, `redirect/`) | Cohesive — all Link code in one place | Each feature package still needs sub-packaging; overkill for ~15 classes |

**Why**: At this project size (~25 Java files), package-by-feature adds unnecessary nesting without real benefit. Package-by-layer keeps the structure flat and navigable. If the project grows beyond 50+ classes, restructure then.

---

## Summary Table

| # | Decision | Chosen | Key Reason |
|---|----------|--------|------------|
| 1 | Short code format | Auto-generated Base62, 7 chars | Simple, huge namespace, MVP-appropriate |
| 2 | Link expiration | Mandatory TTL (default 30d) | Prevents link rot, controls storage |
| 3 | Redirect status | 302 default, configurable | TTL-compatible, enables click tracking |
| 4 | Rate limiting | 1 req/min per IP, Redis | Simple abuse prevention without auth |
| 5 | Authentication | None | Single user, keep scope minimal |
| 6 | Analytics depth | Geography + device + browser + OS + referrer | Rich insights with well-maintained free deps |
| 7 | Dashboard type | Real-time (Redis) | Instant feedback, core value proposition |
| 8 | Data retention | Raw: 30 days, Aggregates: forever | Balances storage cost vs utility |
| 9 | Framework | Spring Boot WebFlux | Non-blocking I/O for redirect-heavy workload |
| 10 | Redis usage | Cache + rate limiting + analytics | Single infra for all real-time needs |
| 11 | PG Schema | Normalized core + JSONB extras | Fast queries on key fields, flexible metadata |
| 12 | Build tool | Gradle Kotlin DSL | Type-safe, fast, Spring ecosystem standard |
| 13 | AWS Compute | ECS Fargate | Zero maintenance, serverless scaling |
| 14 | AWS Postgres | RDS PostgreSQL | Cost-effective for MVP scale |
| 15 | AWS Redis | ElastiCache Serverless | No capacity planning, auto-scaling |
| 16 | CI/CD | GitHub Actions | Free, huge ecosystem, 30-line pipeline |
| 17 | Project structure | Package by layer | Simple, familiar, appropriate for ~25 files |

---

*All decisions made on 2026-06-16. Happy to revisit any of these as requirements evolve.*
