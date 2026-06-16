# Local Deployment Guide — URL Shortener

Deploy the Shortly URL Shortener locally on macOS using **Colima** (Apple Virtualization Framework) as the container runtime.

---

## Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| Java | 25+ | `java --version` |
| Homebrew | — | `brew --version` |
| Git | — | `git --version` |

---

## Step 1: Install Colima and Docker CLI

```bash
# Install both (Docker CLI talks to Colima's daemon)
brew install colima docker

# Start Colima with Apple Virtualization Framework
# (vz has better networking than QEMU on macOS)
colima start --vm-type vz --cpu 2 --memory 4

# Verify
docker version
```

> **Why Colima?**  
> Docker Desktop is commercial (requires license for organizations). Colima is free, open-source, and uses macOS-native virtualization (`vz`) for better performance. Testcontainers works with it seamlessly.

---

## Step 2: Fix Docker Config (one-time)

Colima doesn't use Docker Desktop's credential helpers. Remove the `credsStore` line:

```bash
cat > ~/.docker/config.json << 'EOF'
{"auths": {}, "currentContext": "colima"}
EOF
```

---

## Step 3: Configure Testcontainers for Colima

```bash
cat > ~/.testcontainers.properties << 'EOF'
docker.host=unix:///Users/sagargupta/.colima/default/docker.sock
ryuk.disabled=true
EOF
```

> Replace `/Users/sagargupta` with your home directory path.

---

## Step 4: Clone and Build

```bash
# Clone the project
git clone https://github.com/sg36sagargupta26/url-shortner.git
cd url-shortner

# Build without tests (tests need containers running)
./gradlew build -x test --no-daemon
```

Expected output: `BUILD SUCCESSFUL`

---

## Step 5: Start PostgreSQL and Redis

```bash
# Pull images (first time only)
docker pull postgres:16-alpine
docker pull redis:7-alpine

# Start PostgreSQL
docker run -d \
  --name shortly-postgres \
  -e POSTGRES_DB=shortly \
  -e POSTGRES_USER=sagargupta \
  -e POSTGRES_PASSWORD=password \
  -p 5432:5432 \
  postgres:16-alpine

# Start Redis
docker run -d \
  --name shortly-redis \
  -p 6379:6379 \
  redis:7-alpine

# Wait for PostgreSQL to be ready
until docker exec shortly-postgres pg_isready -U sagargupta -d shortly; do
  echo "Waiting for PostgreSQL..."
  sleep 1
done

echo "Both containers are ready"
```

---

## Step 6: Run the Application

```bash
./gradlew bootRun --no-daemon
```

Expected output:
```
Started ShortlyApplication in X.XXX seconds
```

The app starts on **http://localhost:8080**.

---

## Step 7: Verify the Deployment

Open a new terminal window (leave the app running) and test each endpoint:

### 7.1 Health Check
```bash
curl -s http://localhost:8080/health | python3 -m json.tool
```

Expected:
```json
{
    "status": "UP",
    "redis": "UP",
    "db": "UP"
}
```

### 7.2 Create a Short Link
```bash
curl -s -X POST http://localhost:8080/api/v1/links \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com/hello-world","ttl":"7d","redirectType":"302"}' \
  | python3 -m json.tool
```

Expected:
```json
{
    "shortCode": "aB3xK9m",
    "shortUrl": "https://short.ly/aB3xK9m",
    "originalUrl": "https://example.com/hello-world",
    "expiresAt": "2026-06-23T...",
    "redirectType": "302",
    "createdAt": "2026-06-16T..."
}
```

Save the `shortCode` value for the next step.

### 7.3 Redirect (follow the short link)
```bash
curl -s -o /dev/null -w "Status: %{http_code}\nLocation: %{redirect_url}\n" \
  http://localhost:8080/aB3xK9m
```

Expected:
```
Status: 302
Location: https://example.com/hello-world
```

### 7.4 Analytics
```bash
curl -s http://localhost:8080/api/v1/links/aB3xK9m/analytics | python3 -m json.tool
```

### 7.5 Rate Limiting (run twice to trigger 429)
```bash
curl -s -w "\nHTTP %{http_code}" -X POST http://localhost:8080/api/v1/links \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com"}'
# Second request within 60 seconds → 429
```

---

## Step 8: Run the Test Suite

```bash
TESTCONTAINERS_RYUK_DISABLED=true ./gradlew test --no-daemon
```

Expected: `BUILD SUCCESSFUL` — 28 tests, 0 failures.

---

## Cleanup

```bash
# Stop the application (Ctrl+C in its terminal)

# Stop and remove containers
docker rm -f shortly-postgres shortly-redis

# Stop Colima (frees memory)
colima stop
```

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `docker: command not found` | Run `brew install docker` |
| `Cannot connect to Docker daemon` | Run `colima start --vm-type vz` |
| `TLS handshake timeout` pulling images | Colima is using QEMU mode. Delete with `colima delete`, then start with `--vm-type vz` |
| `port 5432 already in use` | You have a local PostgreSQL. Use `-p 5433:5432` instead and update `application.yml` |
| `port 6379 already in use` | You have a local Redis. Use `-p 6380:6379` instead and update `application.yml` |
| Tests fail with `ClassFormatException` | Spring Boot < 3.5 doesn't support Java 25. Ensure you're on `main` branch (uses 3.5.15) |
| `NoUniqueBeanDefinitionException` | Old `RedisConfig` had duplicate factory bean. Pull latest `main` (fixed in 8b6f3f8) |
| Colima VM slow or hangs | Increase resources: `colima start --vm-type vz --cpu 4 --memory 8` |

---

## Architecture (Local)

```
┌──────────────────────────────────────────┐
│                  macOS                    │
│  ┌────────────────────────────────────┐  │
│  │          Colima VM (vz)             │  │
│  │  ┌──────────┐  ┌───────────────┐   │  │
│  │  │PostgreSQL│  │    Redis 7    │   │  │
│  │  │  16      │  │               │   │  │
│  │  └────┬─────┘  └──────┬────────┘   │  │
│  │       │               │            │  │
│  │       └───┬───────────┘            │  │
│  │          │ :5432  :6379            │  │
│  └──────────┼─────────────────────────┘  │
│             │ port mapping                │
│  ┌──────────┼──────────────────────────┐  │
│  │  Spring Boot WebFlux :8080          │  │
│  │  (runs natively on macOS, JVM)      │  │
│  └─────────────────────────────────────┘  │
└──────────────────────────────────────────┘
```

The app runs directly on macOS (not in a container). Only PostgreSQL and Redis run inside Colima. This makes debugging and hot-reloading easy during development.
