#!/bin/bash
set -e

CASES=(
  "reader-offset"
  "reader-zero-offset"
  "writer-jpa"
  "writer-jdbc-batch"
  "insert-identity"
  "insert-jdbc-rewrite"
  "sequential"
  "partitioning"
)

echo "=== Building application ==="
./gradlew bootJar --no-daemon -q

echo "=== Building Docker image ==="
docker compose build app

echo "=== Starting MySQL ==="
docker compose up -d mysql
echo "Waiting for MySQL to be healthy..."
until docker compose exec mysql mysqladmin ping -h localhost --silent 2>/dev/null; do
  sleep 2
done
echo "MySQL is ready."

echo "=== Starting monitoring stack ==="
docker compose up -d prometheus grafana

for case in "${CASES[@]}"; do
  echo ""
  echo "══════════════════════════════════════════"
  echo "  Running: $case"
  echo "══════════════════════════════════════════"
  BENCHMARK_CASE=$case docker compose run --rm app
  echo ""
done

echo ""
echo "=== All benchmarks complete ==="
echo "Grafana dashboard: http://localhost:3000"
echo ""
echo "To stop infrastructure:"
echo "  docker compose down"
