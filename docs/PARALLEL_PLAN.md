# Parallel Agent Execution Plan

## Strategy

We have 21 tasks organized into 6 phases. Each phase can run multiple agents in parallel since the tasks within a phase have no dependencies on each other. Phases run sequentially — each phase waits for the previous one to complete.

The key insight: **within each phase, tasks are independent** and can be executed simultaneously by separate coding agents.

---

## Agent Allocation per Phase

### Phase 0: Foundation — 3 agents in parallel

```
Agent A -> Task 0.1: Project scaffolding and build.gradle.kts
Agent B -> Task 0.2: Docker Compose
Agent C -> Task 0.3: Flyway SQL migrations
```

**Shared contract before starting**: Agreed-upon classpath, package names, database table names, Redis key patterns, port numbers.

---

### Phase 1: Core Domain — 3 agents in parallel

```
Agent A -> Task 1.1: Models and R2DBC Repositories
Agent B -> Task 1.2: Redis Config + CacheService
Agent C -> Task 1.3: ShortCodeGenerator
```

**Contract**: Repository method signatures (pre-defined), RedisCacheService interface (pre-defined), ShortCodeGenerator input/output contract.

---

### Phase 2: Service Layer — 4 agents in parallel

```
Agent A -> Task 2.1: LinkService (depends on 1.1, 1.2, 1.3)
Agent B -> Task 2.2: RateLimiterService (depends on 1.2)
Agent C -> Task 2.3: GeoLocationService (independent, external lib)
Agent D -> Task 2.4: UserAgentParserService (independent, external lib)
```

**Contract**: 
- `LinkService` exposing `createLink(CreateLinkRequest): Mono<CreateLinkResponse>` and `resolveLink(shortCode): Mono<Link>`
- `RateLimiterService.allowRequest(ip): Mono<Boolean>`
- `GeoLocationService.lookup(ip): GeoResult`
- `UserAgentParserService.parse(ua): UAProfile`

---

### Phase 3: API Layer — 4 agents in parallel

```
Agent A -> Task 3.1: DTOs
Agent B -> Task 3.2: LinkController + RedirectController
Agent C -> Task 3.3: HealthController
Agent D -> Task 3.4: GlobalErrorHandler
```

**Contract**: DTOs defined first (or by Agent A) then shared. Controller paths and response shapes from the spec.

---

### Phase 4: Analytics Engine — 3 agents in parallel

```
Agent A -> Task 4.1: ClickProcessingService
Agent B -> Task 4.2: AnalyticsService
Agent C -> Task 4.3: ExpiryCleanupJob
```

**Contract**: ClickProcessingService interface for recording clicks, AnalyticsService interface for querying analytics, Redis key naming convention from spec.

---

### Phase 5: Infrastructure — 3 agents in parallel

```
Agent A -> Task 5.1: Dockerfile
Agent B -> Task 5.2: Terraform (all AWS resources)
Agent C -> Task 5.3: GitHub Actions workflow
```

**Contract**: ECR repo name, ECS task definition family, AWS region, Docker image tag format.

---

### Phase 6: Testing — 3 agents in parallel

```
Agent A -> Task 6.1: Unit tests
Agent B -> Task 6.2: Integration tests
Agent C -> Task 6.3: Controller tests
```

**Contract**: Test naming convention, Testcontainers setup for integration tests.

---

## Execution Timeline (approximate)

```
Phase 0: ++++++++..........   (~10 min)  Foundation
Phase 1: ....++++++++......   (~10 min)  Domain models & infra
Phase 2: ..........++++++++   (~15 min)  Service layer
Phase 3: ................++   (~10 min)  API layer
Phase 4: ................++   (~10 min)  Analytics
Phase 5: ................++   (~10 min)  Infrastructure
Phase 6: ................++   (~10 min)  Testing
Total:                         ~75 min sequential
                               ~25 min with parallel agents (~3x speedup)
```

---

## How to Execute with Me

Since we're in a single-agent context right now, here are the options:

### Option 1: Sequential but organized (I handle it)
I execute all 21 tasks one-by-one in dependency order. Files compile after each phase.

### Option 2: Batch parallel within phases
For each phase, I create all files simultaneously since tasks within a phase share no files.

### Option 3: Multiple agent sessions (maximum parallelism)
Split the tasks across separate pi agent instances. Each gets a slice of the work with pre-defined contracts. They all write to the same project directory.

---

## Recommended: Batch parallel within phases

**Phase 0** — Create all 3 tasks simultaneously (build.gradle.kts, docker-compose.yml, 3 SQL files) since no dependencies between them.

**Phase 1** — All 3 tasks in parallel (models, Redis config, code generator) — different files, same package.

**Phase 2** — All 4 services in parallel. Each depends on interfaces from Phase 1 but files are independent.

**Phase 3** — All 4 API tasks in parallel.

**Phase 4** — All 3 analytics tasks in parallel.

**Phase 5** — All 3 infra tasks in parallel.

**Phase 6** — All 3 test suites in parallel.

---

## What I Need From You

The three spec docs are now ready:
- `docs/SPEC.md` — Full technical specification
- `docs/TASKS.md` — 21-task breakdown with dependencies
- `docs/PARALLEL_PLAN.md` — This document

**Should I start building?** Choose:

- **A)** Full send — Build everything phase by phase (all 21 tasks). ~30-40 min.
- **B)** Start with Phase 0 (Gradle, Docker Compose, Flyway) — verify first.
- **C)** Pick specific tasks to prioritize.
- **D)** Modify the spec or breakdown before we start.
