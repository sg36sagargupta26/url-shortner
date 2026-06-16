#!/usr/bin/env bash
set -euo pipefail

# ──────────────────────────────────────────────────────────────────
# Shortly URL Shortener — Local Deployment Script (Colima)
# ──────────────────────────────────────────────────────────────────

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

log_info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

APP_PORT=8080
PG_CONTAINER="shortly-postgres"
REDIS_CONTAINER="shortly-redis"
PG_DB="shortly"
PG_USER="sagargupta"
PG_PASSWORD="password"
BASE_URL="http://localhost:${APP_PORT}"

# ── Step 1: Check prerequisites ──
log_info "Checking prerequisites..."

if ! command -v java &>/dev/null; then
    log_error "Java is not installed. Install OpenJDK 25+."
    exit 1
fi
JAVA_VERSION=$(java -version 2>&1 | head -1 | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)
log_ok "Java ${JAVA_VERSION}"

if ! command -v colima &>/dev/null; then
    log_info "Installing Colima..."
    brew install colima
fi
log_ok "Colima $(colima version 2>/dev/null || echo 'installed')"

if ! command -v docker &>/dev/null; then
    log_info "Installing Docker CLI..."
    brew install docker
fi
log_ok "Docker CLI $(docker version --format '{{.Client.Version}}' 2>/dev/null || echo 'installed')"

if ! command -v curl &>/dev/null; then
    log_error "curl is required but not installed."
    exit 1
fi

# ── Step 2: Start Colima if not running ──
log_info "Checking Colima status..."
if colima status 2>&1 | grep -q "colima is running"; then
    log_ok "Colima is running"
else
    log_info "Starting Colima (Apple Virtualization Framework)..."
    colima start --vm-type vz --cpu 2 --memory 4
    log_ok "Colima started"
fi

# ── Step 3: Fix Docker credentials store ──
if grep -q '"credsStore"' ~/.docker/config.json 2>/dev/null; then
    log_warn "Fixing Docker config (removing credsStore)..."
    cat > ~/.docker/config.json << 'DOCKER_EOF'
{"auths": {}, "currentContext": "colima"}
DOCKER_EOF
    log_ok "Docker config fixed"
fi

# ── Step 4: Start PostgreSQL ──
log_info "Checking PostgreSQL container..."
if docker ps --format '{{.Names}}' | grep -q "^${PG_CONTAINER}$"; then
    log_ok "PostgreSQL container is running"
else
    if docker ps -a --format '{{.Names}}' | grep -q "^${PG_CONTAINER}$"; then
        log_info "Starting existing PostgreSQL container..."
        docker start "${PG_CONTAINER}"
    else
        log_info "Pulling PostgreSQL 16 image..."
        docker pull postgres:16-alpine
        log_info "Creating PostgreSQL container..."
        docker run -d \
            --name "${PG_CONTAINER}" \
            -e POSTGRES_DB="${PG_DB}" \
            -e POSTGRES_USER="${PG_USER}" \
            -e POSTGRES_PASSWORD="${PG_PASSWORD}" \
            -p 5432:5432 \
            postgres:16-alpine
    fi

    # Wait for PostgreSQL to be ready
    log_info "Waiting for PostgreSQL to be ready..."
    for i in $(seq 1 30); do
        if docker exec "${PG_CONTAINER}" pg_isready -U "${PG_USER}" -d "${PG_DB}" &>/dev/null; then
            log_ok "PostgreSQL is ready"
            break
        fi
        if [ "$i" -eq 30 ]; then
            log_error "PostgreSQL failed to start within 30 seconds"
            exit 1
        fi
        sleep 1
    done
fi

# ── Step 5: Start Redis ──
log_info "Checking Redis container..."
if docker ps --format '{{.Names}}' | grep -q "^${REDIS_CONTAINER}$"; then
    log_ok "Redis container is running"
else
    if docker ps -a --format '{{.Names}}' | grep -q "^${REDIS_CONTAINER}$"; then
        log_info "Starting existing Redis container..."
        docker start "${REDIS_CONTAINER}"
    else
        log_info "Pulling Redis 7 image..."
        docker pull redis:7-alpine
        log_info "Creating Redis container..."
        docker run -d \
            --name "${REDIS_CONTAINER}" \
            -p 6379:6379 \
            redis:7-alpine
    fi

    # Wait for Redis
    log_info "Waiting for Redis to be ready..."
    for i in $(seq 1 15); do
        if docker exec "${REDIS_CONTAINER}" redis-cli ping &>/dev/null; then
            log_ok "Redis is ready"
            break
        fi
        if [ "$i" -eq 15 ]; then
            log_error "Redis failed to start within 15 seconds"
            exit 1
        fi
        sleep 1
    done
fi

# ── Step 6: Build the project ──
log_info "Building project..."
if ./gradlew build -x test --no-daemon --console=plain 2>&1 | tail -3; then
    log_ok "Build successful"
else
    log_error "Build failed"
    exit 1
fi

# ── Step 7: Start the application ──
log_info "Starting Shortly on port ${APP_PORT}..."
./gradlew bootRun --no-daemon --console=plain &
APP_PID=$!
trap "kill ${APP_PID} 2>/dev/null; log_info 'Application stopped'" EXIT

# Wait for the app to be ready
log_info "Waiting for application to be ready..."
for i in $(seq 1 60); do
    if curl -s "${BASE_URL}/health" > /dev/null 2>&1; then
        log_ok "Application is ready (PID: ${APP_PID})"
        break
    fi
    if [ "$i" -eq 60 ]; then
        log_error "Application failed to start within 60 seconds"
        exit 1
    fi
    sleep 1
done

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Shortly URL Shortener is running!"
echo "  Health:  ${BASE_URL}/health"
echo "  API:     ${BASE_URL}/api/v1/links"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# ── Step 8: Validation tests ──
log_info "Running validation tests..."
FAILURES=0

# 8.1 Health check
echo ""
log_info "Test 1: Health check"
HEALTH=$(curl -s "${BASE_URL}/health")
if echo "$HEALTH" | grep -q '"status":"UP"'; then
    log_ok "Health check passed: $(echo "$HEALTH" | tr -d '\n')"
else
    log_error "Health check failed: $HEALTH"
    FAILURES=$((FAILURES + 1))
fi

# 8.2 Create short link
echo ""
log_info "Test 2: Create short link"
CREATE_RESPONSE=$(curl -s -X POST "${BASE_URL}/api/v1/links" \
    -H "Content-Type: application/json" \
    -d '{"url":"https://example.com/hello-world","ttl":"7d","redirectType":"302"}')

SHORT_CODE=$(echo "$CREATE_RESPONSE" | grep -oE '"shortCode":"([^"]+)"' | cut -d'"' -f4)
SHORT_URL=$(echo "$CREATE_RESPONSE" | grep -oE '"shortUrl":"([^"]+)"' | cut -d'"' -f4)
ORIGINAL_URL=$(echo "$CREATE_RESPONSE" | grep -oE '"originalUrl":"([^"]+)"' | cut -d'"' -f4)

if [ -n "$SHORT_CODE" ] && [ "${#SHORT_CODE}" -eq 7 ]; then
    log_ok "Short link created:"
    log_ok "  shortCode   = ${SHORT_CODE}"
    log_ok "  shortUrl    = ${SHORT_URL}"
    log_ok "  originalUrl = ${ORIGINAL_URL}"
else
    log_error "Failed to create short link"
    log_error "Response: ${CREATE_RESPONSE}"
    FAILURES=$((FAILURES + 1))
fi

# 8.3 Redirect
echo ""
log_info "Test 3: Redirect (follow short link)"
REDIRECT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/${SHORT_CODE}")
REDIRECT_LOCATION=$(curl -s -o /dev/null -w "%{redirect_url}" "${BASE_URL}/${SHORT_CODE}")

if [ "$REDIRECT_STATUS" = "302" ] && [ "$REDIRECT_LOCATION" = "https://example.com/hello-world" ]; then
    log_ok "Redirect works: ${REDIRECT_STATUS} → ${REDIRECT_LOCATION}"
else
    log_error "Redirect failed: status=${REDIRECT_STATUS}, location=${REDIRECT_LOCATION}"
    FAILURES=$((FAILURES + 1))
fi

# 8.4 Analytics
echo ""
log_info "Test 4: Analytics"
ANALYTICS=$(curl -s "${BASE_URL}/api/v1/links/${SHORT_CODE}/analytics")
if echo "$ANALYTICS" | grep -q '"totalClicks"'; then
    TOTAL_CLICKS=$(echo "$ANALYTICS" | grep -oE '"totalClicks":[0-9]+' | cut -d: -f2)
    log_ok "Analytics available: totalClicks=${TOTAL_CLICKS}"
else
    log_error "Analytics failed: ${ANALYTICS}"
    FAILURES=$((FAILURES + 1))
fi

# 8.5 Rate limiting
echo ""
log_info "Test 5: Rate limiting (2 rapid requests → second should be 429)"
R1=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/api/v1/links" \
    -H "Content-Type: application/json" \
    -d '{"url":"https://example.com/rate-test"}')
R2=$(curl -s -o /dev/null -w "%{http_code}" -X POST "${BASE_URL}/api/v1/links" \
    -H "Content-Type: application/json" \
    -d '{"url":"https://example.com/rate-test-2"}')

if [ "$R1" = "201" ] && [ "$R2" = "429" ]; then
    log_ok "Rate limiting works: request 1=${R1}, request 2=${R2}"
else
    log_warn "Rate limiting may not be active: request 1=${R1}, request 2=${R2} (expected 201 then 429)"
fi

# 8.6 404 for unknown short code
echo ""
log_info "Test 6: 404 for unknown short code"
NOTFOUND=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/nonexistent123")
if [ "$NOTFOUND" = "404" ]; then
    log_ok "Unknown code returns 404"
else
    log_error "Expected 404, got ${NOTFOUND}"
    FAILURES=$((FAILURES + 1))
fi

# 8.7 Validation: missing URL
echo ""
log_info "Test 7: Reject request with missing URL"
VALIDATION=$(curl -s -w "\n%{http_code}" -X POST "${BASE_URL}/api/v1/links" \
    -H "Content-Type: application/json" \
    -d '{"ttl":"30d"}')
VAL_CODE=$(echo "$VALIDATION" | tail -1)
if [ "$VAL_CODE" = "400" ]; then
    log_ok "Missing URL returns 400"
else
    log_error "Expected 400, got ${VAL_CODE}"
    FAILURES=$((FAILURES + 1))
fi

# ── Results ──
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if [ "$FAILURES" -eq 0 ]; then
    echo -e "  ${GREEN}✓ All validation tests passed!${NC}"
    echo ""
    echo "  Short link: ${SHORT_CODE}"
    echo "  Try it:     curl -v ${BASE_URL}/${SHORT_CODE}"
else
    echo -e "  ${RED}✗ ${FAILURES} test(s) failed${NC}"
fi
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Keep running until Ctrl+C
echo ""
log_info "Press Ctrl+C to stop the application"
wait "${APP_PID}"
