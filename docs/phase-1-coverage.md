# Phase 1 Coverage — Japan + Multi-Country Concurrent Ingest

## Strategy Change (2026-07-18)

Originally: Japan Supreme Court only (1K, Week 3)  
**Updated**: Japan + USA + EU + International (concurrent, Week 2-3)

## Target Coverage (Phase 1, Week 2-3)

| Jurisdiction | Source | Method | Target | Start Date |
|---|---|---|---|---|
| 🇯🇵 Japan (SC) | courts.go.jp API | REST | 1,000 decisions | 2026-07-22 |
| 🇺🇸 USA (SC) | Google Scholar | Web scrape | 400 decisions | 2026-07-22 |
| 🇺🇸 USA (Appellate) | RECAP API | REST | 500 decisions | 2026-07-22 |
| 🇪🇺 EU (ECJ/CJEU) | CURIA SPARQL | Graph query | 200 decisions | 2026-07-22 |
| 🌍 ICC | icc-cpi.int | HTML scrape | 50 cases | 2026-07-23 |
| 🌍 ICJ | icj-cij.org | HTML scrape | 30 cases | 2026-07-23 |
| **TOTAL** | — | — | **2,180 decisions** | **2026-07-25** |

## Concurrent Execution Plan

```
┌─ Japan SC (REST)      ────┐
├─ USA SC (scrape)      ────┤
├─ USA RECAP (REST)     ────┤ → Aggregate → Datomic → Schema Validation
├─ EU CURIA (SPARQL)    ────┤
├─ ICC (HTML scrape)    ────┤
└─ ICJ (HTML scrape)    ────┘

All fetches run in parallel (async/go).
No sequential blocking.
ETA: ~15 min total (vs 90+ min if sequential).
```

## Implementation Phases

### Phase 1a: Fetch Layer (REST/SPARQL)
- Japan SC: ✓ Done (mock)
- USA SC (Scholar): In progress (Selenium)
- USA RECAP: In progress (REST API)
- EU CURIA: In progress (SPARQL)

### Phase 1b: Parse Layer
- Full-text PDF → text extraction (PDFBox / Tesseract for images)
- HTML → structured extraction (jsoup / regex)
- Statute reference extraction (shared hanrei logic)
- Judge name extraction (per-country patterns)

### Phase 1c: Entity Building & Transact
- Build `:hanrei/case` entities (all countries)
- Build `:hanrei/judge` entities (all countries, public role names only)
- Build `:hanrei/opinion` entities
- Transact to kotoba Datomic (batch mode, 100 entities/tx)

### Phase 1d: Validation
- Schema compliance check (all 10 attributes present)
- Statute reference resolution (fuzzy-match to houbun, target 70%+)
- Duplicate detection (by case-id + source)
- PII audit (no personal contact info, role names only)

## Coverage Honesty

**Phase 1 Coverage Target**: 2,180 decisions across 6 jurisdictions

This is **NOT representative** of global case law (500M+ cases globally).
Phase 1 is a **proof-of-concept pilot** across geographically/legally diverse sources.

**Explicitly NOT included** (Phase 2+):
- Appellate courts other than USA/EU apex
- Lower courts (only Phase 2 Japan lower court pilot)
- Private arbitration (e.g., LCIA, ICC Arbitration vs ICC justice court)
- Administrative tribunals (e.g., WIPO, ITC)
- Subnational courts (UK crown courts, German Landesgerichte, etc.)

Phase 2+ will expand. Phase 1 validates architecture & scraping/API integration across diverse sources.
