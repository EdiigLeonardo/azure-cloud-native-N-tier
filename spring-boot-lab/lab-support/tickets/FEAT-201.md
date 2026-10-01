# FEAT-201 · Aggregation latency metric (internal)
**From:** Olivier (Support lead)

> Half of our performance tickets start with "it's slow" and no numbers. Please expose a Micrometer
> **Timer** named `riskcube.aggregation`, tagged with `dimension` (DESK, BOOK, ...), so customers can graph
> p95/p99 in Grafana (`/actuator/prometheus` -> `riskcube_aggregation_seconds_*`) and alert on it.

**Test:** `Feat201AggregationMetricsTest`
