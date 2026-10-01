# Support queue: Monday 29 Sep 2026, 09:00

You are the support engineer on duty. These tickets arrived over the weekend.
**Before touching any code: do the triage exercise (GUIA.md, Passo 1).**

| Ticket | Customer | Subject | Customer says |
|---|---|---|---|
| TCK-101 | Northbridge Bank | Counterparty view shows "Something went wrong" | High |
| TCK-102 | Northbridge Bank | API returns 500 for unknown trades, pages our on-call | Medium |
| TCK-103 | Northbridge Bank | Desk P&L totals change between two refreshes | Critical |
| TCK-104 | Aldermoor Capital | Desk summary takes 12-15 seconds | High |
| TCK-105 | Aldermoor Capital | Pods OOMKilled every ~6 hours | Critical |
| TCK-106 | Northbridge Bank | Whole service freezes at market open | Critical |
| TCK-107 | Aldermoor Capital | Read-only user deleted trades (audit finding) | High |
| TCK-108 | Northbridge Bank | Nightly ingestion fails with 403 since upgrade | High |
| TCK-109 | Kestrel Asset Mgmt | 2.4.0 upgrade in PROD: CrashLoopBackOff, rolled back | Critical |
| FEAT-201 | (internal) | Expose aggregation latency metric for Grafana | - |
| TCK-110 | Northbridge Bank | Incident report request for yesterday 14:30 | High |

Every ticket has an acceptance test in `riskcube/src/test/java/com/riskcube/tickets/`.
Run one: `mvn test -Dtest=Tck101*`
