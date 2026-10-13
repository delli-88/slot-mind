# Build log

Time-ordered. Times are elapsed minutes from the start clock (to be filled once the start time is known). Each task adds an entry right after it finishes.

## Entries
| # | Elapsed | Task | What happened | Evidence |
|---|---|---|---|---|
| 1 | 0 | S0 read brief | Read the HTML brief and repo (starter: Spring Boot 3.5.16, Java 21, Vite React 18.3.1, no domain code). Reported differences. User decided: 3 h budget, rejected suggestion keeps RESCHEDULE_PENDING, same-specialty alternates (URGENT any), React with polling, Claude API | user answers D1-D5 |
| 2 | | S0 PROBLEM.md | Requirements, rubric, invariants I1-I10, assumptions A1-A7 | docs/PROBLEM.md |
| 3 | | S0 DESIGN.md | Architecture, ERD, 11 endpoint contracts, 4 flows, error model | docs/DESIGN.md |
| 4 | | S0 AI_LAYER.md | Two prompts, validation pipeline, fallback, 21 AI tests; adapted from the reference example | docs/AI_LAYER.md |
| 5 | | S0 ADR, TASKS, BUILD_LOG, CLAUDE | ADR-001..010, board with 28 build tasks, project rules | docs/ADR.md, TASKS.md |

| 6 | 37 | 1.1 bootstrap | Moved to `com.slotmind`, added `anthropic-java` 2.34.0, `slotmind.*` props, `open-in-view=false`, `Clock` bean, shared `@IntegrationTest` annotation (seed off). Test failed first on the missing Clock bean, then passed | `./mvnw -q -B test`: 2 tests green |
| 7 | 45 | 1.2 Provider/Slot | Entities with @Version, transition methods, IllegalTransitionException, repos. User changed process: tests per sprint, not per task (time) | SlotTransitionTest 7 green |
| 8 | 70 | 1.3-1.6 | Appointment/Suggestion entities, SeedData (Addendum A), error model + handler, GET /providers. Tests batched at sprint end per user. Each test class gets its own H2 db (random uuid) to avoid shared in-memory name clashes | 26 tests green, 0 failures |
| 9 | 77 | 2.1-2.8 | Booking, list/get, provider and slot status, rule engine + registry, RescheduleService (snapshot tx -> strategy -> write tx), suggest, accept/reject. Tests batched at sprint end. `rescheduleQueued` is true when appointments were stranded, but the event is not published until S4 | 83 tests green; thread test 3 extra runs stable |
| 10 | 97 | 3.1-3.5 | AI layer: `LlmClient` seam + `LLMGateway` (anthropic-java, no sampling params, maxRetries 0, per-request timeout), `PromptBuilder` (two prompts), `LlmResponseValidator`, `AiStrategy` (closed candidate set = rule ranking capped at 12, one repair retry if >=3 s left, fallback with cause), `AiMetrics` counter, persist-time slot re-check in `RescheduleService`. Model default `claude-sonnet-5-5` (user choice). `AiStrategy` lives in `ai/`, not `scheduling/`. Tests batched at sprint end. Real call NOT done: `LLM_API_KEY` not set in the shell | full suite green, 0 failures; 38 new tests cover A1-A21 |
| 11 | 108 | 3.5 real call | User ran the app with `LLM_API_KEY`, model `claude-sonnet-5-5`, effort low, via `POST /appointments/APT-001/suggest?strategy=ai` after PRV-001 OFFLINE. First attempt returned `source RULES, fallbackCause PARSE_ERROR` (gateway worked, so key/model/effort were accepted; cause not logged). Added WARN logs for rejected replies and failed calls plus a DEBUG raw-reply log. Second attempt: `source LLM`, `fallbackCause null`, slot SLT-005, alternate PRV-004, confidence 0.6, reasoning "Your earlier time with Dr. Chen is no longer available. The closest opening is with Dr. Rahul Verma ... about 90 minutes later. Would that work for you?" (90 min is correct: original +60, new +150). Latency not measured | user-pasted responses; AI tests still green |

## Bugs we hit
| Symptom | Hypothesis | How found | Fix | Lesson |
|---|---|---|---|---|
| 4 S2 tests failed on first run (sort, 3 planner tests) | production sort or ranking wrong | read assertion output: sort test collected nested `pendingSuggestion.recommendedSlot.startTime` too; planner tests got `SLT-F3`, an earlier test's open slot in the shared per-class DB | fixed the tests only: read `/slot/startTime` per item; targets became same-provider (tier 1) slots; one new test uses SPECIALIST so it cannot leak into the PEDIATRIC no-candidate test. Assertions not weakened | tests that rank over all open slots must control which slots exist |
| `a12` reasoning-cleaning test failed twice | validator replaced ids only via a regex needing 2+ chars after the hyphen (SLT-A, PRV-A slipped through); then the appointment id (never sent, but echo-able) was not covered | assertion output after each run | replace known provider/slot ids exactly (longest first), also the appointment id and its slot id; assertions unchanged | map known ids first, regex only as a safety net |
| `a18` injection test failed once | test asserted the prompt has no `<`, but the 4 structural tags contain it | assertion output | test now asserts exactly 4 `<` characters; sanitising code was right | assert the property you mean |
| First real AI call returned PARSE_ERROR, second worked | max_tokens cut-off (thinking shares the 2048 budget), non-JSON reply, or a failed validation | not found: the reason was only logged at DEBUG | added WARN with reason + DEBUG raw reply; NOT reproduced yet | log the reason at WARN from the start; do not claim a fix without a reproduction |

## AI corrections (where the assistant or the user got it wrong, and how it was caught)
| Where | Wrong | Caught by | Fix |
|---|---|---|---|
| Brief header | "115 pts total" but the six task weights sum to 117 | adding the task weights during `read brief` | PROBLEM.md uses 117 plus bonuses |
| Reference AI example | specified `temperature 0` | Claude API reference: current models reject `temperature`, `top_p`, `top_k` with a 400 | AI_LAYER.md drops sampling params; determinism from closed set, validation and fallback |
| DESIGN.md first draft | planned a hand-written `RestClient` for the Claude API | Claude API guidance: use the official SDK in Java projects | switched to `anthropic-java`; stack line updated |
| PROBLEM.md A4 and DESIGN.md 422 example | said APT-003 would have no candidate | tracing seed data against decision D3 (URGENT may use any provider) while writing ADR/TASKS | examples now use a non-urgent pediatric appointment; APT-003 gets a tier-3 suggestion |
| Earlier chat reply | claimed the "pediatric edge case" for APT-003 | same trace as above | corrected in docs; noted here |
