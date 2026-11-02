# ADR-005: Privacy-Safe Operational Reporting & BI Data Boundary

**Status**: Accepted
**Date**: October 24, 2026
**Context**:
Hospital operations managers require aggregated KPIs (mean/median/P90 waiting times, hourly arrival distributions, no-show rates) for analysis in Power BI Desktop and Microsoft Excel. Connecting BI tools directly to production operational tables risks exposing sensitive patient records (names, phone numbers) and running unindexed analytical queries that lock transactional tables.

**Decision**:
1. Separate reporting into dedicated, read-only aggregate endpoints and curated SQL exports.
2. Anonymize all reporting datasets: strip patient names, phone numbers, addresses, and primary patient IDs. Retain only clinical department, doctor pseudonym, token sequence, and durations.
3. Use PostgreSQL Common Table Expressions (CTEs), `EXTRACT(EPOCH ...)`, and `percentile_cont()` window functions to calculate mathematical truth server-side rather than delegating complex data transformations to client spreadsheets.

**Alternatives Considered**:
1. *Direct Read-Replica Connection to Power BI*: Good for enterprise scale, but overkill for local single-database setup.
2. *Exposing Raw Visits in REST API*: Poses privacy violations and transfers heavy aggregation load to client devices.

**Consequences & Tradeoffs**:
- *Positive*: Zero PII leakage in BI reports or presentations; safe sharing with stakeholders and recruiters; high query efficiency.
- *Negative*: Ad-hoc queries outside the curated dimensions require expanding the reporting view.
