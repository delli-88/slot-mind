# CLAUDE.md: SlotMind hackathon (solo, 3 hours, Java backend focus)

Backend is ~80% of the work and all of the grading. React is a thin view. Prefer anything that makes the backend more correct, testable or demoable. The user owns every decision and must be able to explain every line.

## Commands (user -> me)
`read brief` | `design` | `do T-<id>` (one task, then stop) | `do sprint S<n>` (all tasks, update TASKS.md and BUILD_LOG.md after EACH, stop at the sprint boundary or when a test fails twice) | `explain T-<id>` | `status` | `commit` / `commit and push` | `cut` | `refresh`.
Never write application code before a `do ...`. Never commit or push on my own: show `git status`, changed files and a proposed message, then wait. No destructive git. Never make the repo public. Tick TASKS.md immediately when a task is done; after any compaction or new session run the context refresh.

## Rules of engineering (always)
- controller (HTTP only) -> service (rules, transactions) -> repository; constructor injection; DTO records; entities never returned.
- Bean Validation on every request; one `@RestControllerAdvice`; error body `{timestamp,status,error,message,path,details[]}`.
- State changes only through entity methods enforcing legal transitions. `@Version` on contended rows, 409 on conflict.
- Unique constraints, FKs, indexes; enums as STRING.
- Events after commit (`@TransactionalEventListener(AFTER_COMMIT)` + `@Async` bounded executor). No LLM or external call inside a transaction.
- Every external call has a timeout and a fallback. Secrets only from `LLM_API_KEY` (`llm.api.key`); nothing committed.
- Tests: unit for rules and failure paths, MockMvc integration test per endpoint (H2), thread test for double accept.
- Two seams only: `SchedulingStrategy` (rule + AI) and `LlmClient` (fake in tests). No caching, CQRS, brokers, generic bases, mapping libs.

## Document ownership (each fact in ONE file; update the owner in the same task)
docs/PROBLEM.md requirements and invariants | docs/DESIGN.md architecture, ERD, API contracts, flows, errors | docs/AI_LAYER.md prompts, validation, fallback, AI tests | docs/ADR.md decisions and "Not built" | docs/TASKS.md board | docs/BUILD_LOG.md log, bugs, AI corrections | README.md run instructions.
Response DTO fields in DESIGN section 3 are the source of truth for code, tests and React types.

## Context refresh (before every sprint, after compaction, on `refresh`)
Read in order: this file; TASKS.md "Current state" and last 5 BUILD_LOG entries; PROBLEM.md invariants; the design sections the sprint needs (S1 architecture+ERD+errors; S2 ERD+API+errors+ADR; S3 AI_LAYER+flows 4.1-4.2; S4 flows 4.1,4.3,4.4 + AI fallback + ADR 004/005; S5 API responses + ADR-009; S6 README, ADR Not built, rubric); `git status`; `git log --oneline -10`. Print under 10 lines: sprint and tasks, invariants, files to change, doc/code mismatches, open questions. Wait if docs and code disagree.

## Build loop (every task)
1 restate task and done-when in two lines. 2 write the test first, show it failing. 3 smallest code that passes. 4 run the new test, then the full suite, then check the app starts. 5 update owned docs, TASKS.md row + Current state, BUILD_LOG.md. 6 print: 5-line explanation (what, where, why, what could break, how tested), one line on race/duplicate/slow-dependency risk, `git status`, proposed commit message. 7 `do T-`: stop. If a test fails twice, stop and report. Never weaken or skip a test.

## Time
Budget 3 h: S0 0-20, S1 20-35, S2 35-75, S3 75-100, S4 100-130, S5 130-150, FREEZE at 150, S6 150-180 (README, ADR final, secrets scan, clean-shell run, demo, push). Report ahead/behind in minutes at every boundary and `status`. Cut order when behind >15 min: UI polish, SSE, extras beyond the brief, deployment, tests beyond failure paths. Never cut: validation, fallback, conflict handling, main-flow integration tests, README, ADR, secrets scan.

## Project facts
Package `com.slotmind`; Spring Boot 3.5, Java 21, Maven (`backend/mvnw`), H2 default and tests, Postgres via `docker compose` (profile `postgres`, port 5433); UI in `ui/` (Vite, React 18, proxy to :8080). Claude model is `slotmind.llm.model` (default `claude-sonnet-5-5`), no sampling params.
