# SlotMind: AI re-scheduling engine

When a provider goes OFFLINE or a slot is CANCELLED, SlotMind finds the affected appointments, asks Claude (or a deterministic rule engine) for a replacement slot and queues the suggestion for front-desk approval. Nothing is rebooked until a human accepts.

Spring Boot 3.5 / Java 21 / JPA (H2 default, Postgres optional) and a thin React 18 + Vite UI.

## Run it

Prerequisites: Java 21, Node 20+. Maven is not needed (`./mvnw`). Docker is only for Postgres.

```
# 1. backend (H2 in memory, demo data seeded at startup)
cd backend
./mvnw spring-boot:run            # http://localhost:8080

# 2. UI, in a second terminal
cd ui
npm ci && npm run dev             # http://localhost:5173 (proxies /api to :8080)
```

The AI strategy needs a key. Without one the app still works: every suggestion falls back to the rules and shows `fallbackCause: NO_API_KEY`.

```
export LLM_API_KEY=...            # Git Bash / Linux / macOS
$env:LLM_API_KEY="..."            # PowerShell
```

The key is read only from `LLM_API_KEY` (property `llm.api.key`). It is never logged or committed (`.env` is git-ignored; `.env.example` is the template).

### Postgres (optional)
```
docker compose up -d --wait       # host port 5433
cd backend
SPRING_PROFILES_ACTIVE=postgres ./mvnw spring-boot:run     # PowerShell: $env:SPRING_PROFILES_ACTIVE="postgres"
```

### Tests
```
cd backend && ./mvnw test         # 146 tests, H2, no network, no API key needed
cd ui && npm run lint && npm run build
```
The suite includes MockMvc integration tests per endpoint, unit tests for the rules and the AI failure paths (fake `LlmClient`), the asynchronous loop, and thread-based race tests for double accept and double booking.

## Demo path (also in `docs/DEMO.md`)
1. Open the UI. The queue is empty.
2. Set **Dr. Sarah Chen** OFFLINE. The call returns at once.
3. Within a second or two APT-001 and APT-002 appear with suggestions (both offered SLT-005, Dr. Verma).
4. Accept the first: 200, appointment RESCHEDULED, old slot freed. Accept the second: 409 `SLOT_NOT_OPEN`, the card shows why.

## Endpoints
| Method and path | Purpose |
|---|---|
| `POST /appointments` | book a provider + OPEN slot (409 if taken or provider offline) |
| `GET /appointments?status=&page=&size=` | filterable list, embeds the pending suggestion |
| `GET /appointments/{id}` | one appointment |
| `PATCH /providers/{id}/status` | `OFFLINE` strands CONFIRMED appointments and starts the async loop |
| `GET /providers`, `GET /slots` | rosters (slots paged, filter `status`, `providerId`) |
| `PATCH /slots/{id}/status` | `CANCELLED` strands the appointment on it and starts the loop |
| `POST /appointments/{id}/suggest?strategy=rule\|ai` | on-demand suggestion (409 if one is already pending) |
| `PATCH /suggestions/{id}` | `ACCEPTED` or `REJECTED`; accept is one atomic transaction |
| `GET /actuator/health` | health |

Errors always use `{timestamp,status,error,message,path,details[]}`; for 409 and 422 `details[0]` starts with a stable code (`SLOT_NOT_OPEN`, `PENDING_SUGGESTION_EXISTS`, `NO_CANDIDATE_SLOT` ...). `requests.http` has a ready-made request set.

## Configuration
| Property | Default | Meaning |
|---|---|---|
| `slotmind.scheduling.strategy` | `ai` | `ai` or `rule`; a request can override with `?strategy=` |
| `slotmind.seed.enabled` | `true` | load the demo data when the database is empty |
| `slotmind.llm.model` | `claude-sonnet-5-5` | Claude model (no sampling params are sent) |
| `slotmind.llm.timeout-seconds` | `8` | total budget per suggestion, including one repair retry |
| `slotmind.llm.max-candidates` | `12` | size of the closed slot list the model may choose from |
| `slotmind.clinic-zone` | `UTC` | zone used to write times in prompts and patient text |

## Where things are
`docs/ADR.md` decisions and what was not built (also linked from the root `ADR.md`) · `docs/DESIGN.md` architecture, ERD, API contracts · `docs/AI_LAYER.md` prompts, validation, fallback · `docs/PROBLEM.md` requirements and invariants · `docs/TASKS.md` and `docs/BUILD_LOG.md` how it was built, including bugs and AI corrections.

## Known limits
- The reschedule event is in-process: if the JVM dies between commit and handler, the appointment stays `RESCHEDULE_PENDING` with no suggestion until someone calls `POST /suggest` (outbox is listed in ADR "Not built").
- A suggestion does not reserve its slot, so two patients can be offered the same slot; the second accept gets a clean 409.
- No SSE stream, no creation UI for providers or slots (seed data only), no auth.
- Intermittent `PARSE_ERROR` was seen once on a real model call and not reproduced; it degrades to the rules suggestion and is visible in the UI and the `slotmind.ai.fallback` metric.
- The Postgres profile is configured but was not exercised in the final run (Docker unavailable on the build machine).
