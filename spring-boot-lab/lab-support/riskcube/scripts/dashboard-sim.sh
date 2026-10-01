#!/usr/bin/env bash
# TCK-105: simulates the customer's dashboards (auto-refresh with a cache-busting "_=" parameter)
# Usage: ./scripts/dashboard-sim.sh 3000
N=${1:-3000}
for i in $(seq 1 "$N"); do
  curl -s -o /dev/null -u analyst:analyst "http://localhost:8080/api/v1/aggregates?groupBy=DESK&_=$(date +%s%N)"
  if (( i % 250 == 0 )); then
    heap=$(curl -s -u admin:admin "http://localhost:8080/actuator/metrics/jvm.memory.used?tag=area:heap" | grep -o '"value":[0-9.E]*' | head -1 | cut -d: -f2)
    echo "refreshes=$i heap_used_bytes=$heap"
  fi
done
