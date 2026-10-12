#!/usr/bin/env bash
# Smoke-tests a built image against a fresh PostgreSQL (#11): starts both containers on a private network, waits for
# the image's own HEALTHCHECK to report healthy, then makes one real API call and checks the seed data came back,
# which proves Liquibase ran. Used by CI before an image is published and runnable locally:
#
#   .github/scripts/smoke-test.sh <image reference> [host port, default 18080]
#
# Container logs are printed on failure; the containers and network are always removed.
set -euo pipefail

IMAGE="${1:?image reference required}"
PORT="${2:-18080}"
NETWORK="store-smoke"
DB="store-smoke-db"
APP="store-smoke-app"

cleanup() {
    docker rm -f "$APP" "$DB" > /dev/null 2>&1 || true
    docker network rm "$NETWORK" > /dev/null 2>&1 || true
}
fail() {
    echo "::error::$1"
    echo "--- $APP logs ---"
    docker logs "$APP" 2>&1 | tail -n 100 || true
    exit 1
}
trap cleanup EXIT
cleanup

docker network create "$NETWORK" > /dev/null
docker run -d --name "$DB" --network "$NETWORK" \
    -e POSTGRES_USER=admin -e POSTGRES_PASSWORD=admin -e POSTGRES_DB=store \
    postgres:16.2 postgres -c wal_level=logical > /dev/null

# The image's HEALTHCHECK interval is tuned for production; poll faster here so the check finishes in seconds. The
# start period must cover the whole startup, migrations included, on a slow runner: a failed probe inside it does not
# count towards "unhealthy", while a passing one flips to "healthy" at once. The loop below is the real timeout.
docker run -d --name "$APP" --network "$NETWORK" -p "$PORT:8080" \
    --health-interval 2s --health-start-period 120s --health-retries 3 \
    -e SPRING_DATASOURCE_URL="jdbc:postgresql://$DB:5432/store" \
    -e SPRING_DATASOURCE_USERNAME=admin -e SPRING_DATASOURCE_PASSWORD=admin \
    "$IMAGE" > /dev/null

echo "Waiting for $IMAGE to report healthy..."
for _ in $(seq 1 60); do
    status="$(docker inspect --format '{{.State.Health.Status}}' "$APP" 2> /dev/null || echo missing)"
    case "$status" in
        healthy) break ;;
        unhealthy) fail "container reported unhealthy" ;;
    esac
    if [ "$(docker inspect --format '{{.State.Running}}' "$APP")" != "true" ]; then
        fail "container exited before becoming healthy"
    fi
    sleep 2
done
[ "$status" = "healthy" ] || fail "container did not become healthy in time (last status: $status)"

health="$(curl -fsS "http://localhost:$PORT/actuator/health")" || fail "health request failed"
echo "Health: $health"
echo "$health" | grep -q '"status":"UP"' || fail "health did not report UP"

order="$(curl -fsS "http://localhost:$PORT/order/1")" || fail "GET /order/1 failed"
echo "GET /order/1: ${order:0:200}"
echo "$order" | grep -q 'Handcrafted Soft Chair' || fail "seed order 1 not returned; did the migrations run?"

echo "Smoke test passed for $IMAGE"
