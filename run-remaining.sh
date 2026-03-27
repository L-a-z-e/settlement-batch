#!/bin/bash
set -e

CONTAINER_ID="a9d1d764d415"

echo "=== Waiting for Case 1 container ($CONTAINER_ID) to finish ==="
docker wait $CONTAINER_ID 2>/dev/null || echo "Container already stopped."
echo "Case 1 done. Starting remaining cases..."

CASES=(
  "reader-zero-offset"
  "writer-jpa"
  "writer-jdbc-batch"
  "insert-identity"
  "insert-jdbc-rewrite"
  "sequential"
  "partitioning"
)

for case in "${CASES[@]}"; do
  echo ""
  echo "══════════════════════════════════════════"
  echo "  Running: $case ($(date))"
  echo "══════════════════════════════════════════"
  BENCHMARK_CASE=$case docker compose run --rm app 2>&1
  echo ""
done

echo ""
echo "=== All remaining benchmarks complete ($(date)) ==="
