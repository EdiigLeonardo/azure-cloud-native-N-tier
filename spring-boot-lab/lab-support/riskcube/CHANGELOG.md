# RiskCube changelog

## 2.4.0 (2026-09-22)
- Faster aggregation engine: aggregation is now parallelised for large portfolios (#412)
- New query result cache: dashboard refreshes no longer recompute the cube (#398)
- Market data client migrated to Spring `RestClient` (#420)
- Configuration cleanup: market data settings moved under `riskcube.market-data.*` (#421)
- Security: simplified authorization rules (#425)

## 2.3.2 (2026-07-14)
- Fix: desk summary rounding for JPY books (#377)
- Market data provider URL configurable through the `MD_URL` environment variable

## 2.3.0 (2026-05-02)
- New `COUNTERPARTY` dimension
- Exposure endpoint `/api/v1/exposure/{currency}` (converted to EUR with live FX rates)
