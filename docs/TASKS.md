# Task board     Budget: 3h   Start: 12:21 (user: 30 min elapsed at 12:51)   Freeze: 14:51 (minute 150)   End: 15:21   Now: 13:58
Legend: TODO · DOING · DONE · BLOCKED · CUT

## Current state (rewrite fully on every update)
Sprint: S3 code and tests complete (3.1-3.5), full suite green (0 failures); real Claude call NOT yet made (LLM_API_KEY not set in the shell) | Running: yes | Broken: nothing | Uncommitted: S3 (ai/ package, RescheduleService persist re-check, application.properties, tests, docs) | Last commit: 21a84bc feat(s2) | Next 3: real call with key (3.5), then S4 4.1, 4.2, 4.3 | Open questions: key for the manual call | Time: minute ~97 of 150, on plan (S3 window ends at 100)

## Sprints
| ID | Sprint | Window (min) | Est. task minutes | Exit criteria | Status |
|---|---|---|---|---|---|
| S0 | Read, customise, design docs approved | 0-20 | n/a | PROBLEM, DESIGN, AI_LAYER, ADR, TASKS approved | written, awaiting approval |
| S1 | Skeleton + first vertical slice | 20-35 | 17 | app runs, health UP, seed loaded, GET /providers + integration test, error body | TODO |
| S2 | Core domain | 35-75 | 41 | core endpoints with validation, error body, state rules, rule engine, tests | TODO |
| S3 | AI layer | 75-100 | 24 | gateway wrapped, prompts, validator, fallback, AI tests green, one real call verified | code+tests DONE, real call pending key |
| S4 | Agentic loop and concurrency | 100-130 | 30 | after-commit trigger, suggestion queue, offline re-plan, 409 + thread test, end-to-end trace | TODO |
| S5 | UI | 130-150 | 20 | one screen: data, queue, accept/reject, trigger badge, polling | TODO |
| S6 | Prove and ship | 150-180 (feature freeze at 150) | 30 | README, ADR final, secrets scan, clean-shell run, demo script, user makes repo public | TODO |

Estimates are wall-clock at AI-pair pace with no failing tests. Many tasks are under the 10-minute size floor of the working rules, because 28 tasks in 150 minutes cannot be 10 to 20 minutes each. Cut order below is the safety valve. Sprint totals run 2 minutes over the S1 window and 1 minute over S2; recovered by cutting S4.6 or S5.3 polish if behind.

## Tasks
Rows: goal (req/rubric) | files | test | done when | evidence | commit. Files per task are at most 5. Enums are nested inside entities.

### S0 Design
- [x] 0.1 PROBLEM.md written (Gate 1 confirmed by user answers)  (req/rubric: all)
      files: docs/PROBLEM.md | test: n/a | done when: user confirmed | evidence: user answers D1-D5 | commit:
- [x] 0.2 DESIGN.md, AI_LAYER.md, ADR.md, TASKS.md, BUILD_LOG.md, CLAUDE.md written  (req/rubric: Gate 2)
      files: docs/*, CLAUDE.md | test: Gate 2 checklist | done when: user approves | evidence: Gate 2 printed | commit:

### S1 Skeleton and first vertical slice  (window 20-35)
- [x] 1.1 Bootstrap: package `com.slotmind`, add `anthropic-java`, `slotmind.*` properties, ISO-date Jackson, `Clock` bean, test properties  (T-1 foundation)
      files: pom.xml, SlotMindApplication.java, application.properties, config/ClockConfig.java, SlotMindApplicationTests.java, IntegrationTest.java (6 files; shared test annotation) | test: contextLoads + GET /actuator/health is UP | done when: `mvnw test` green and app starts | evidence: `./mvnw -q -B test` -> SlotMindApplicationTests 2 tests, 0 failures (failed first with missing Clock bean) | commit:
- [x] 1.2 Provider and Slot entities with `@Version` and transition methods, repositories  (T-1, I8)
      files: domain/Provider.java, Slot.java, ProviderRepository.java, SlotRepository.java, test/domain/SlotTransitionTest.java | test: Slot OPEN->BOOKED->OPEN, CANCELLED terminal, illegal move throws; repo persist+reload | done when: green | evidence: SlotTransitionTest 7 tests, 0 failures (red first: classes missing) | commit:
- [x] 1.3 Appointment and RescheduleSuggestion entities with constraints, repositories  (T-1, I1, I6)
      files: domain/Appointment.java, RescheduleSuggestion.java, AppointmentRepository.java, SuggestionRepository.java, test/domain/ConstraintTest.java | test: duplicate `slot_id` and duplicate `pending_key` violate; lifecycle illegal moves throw | done when: green | evidence: ConstraintTest 5 tests green (sprint-batch) | commit:
- [x] 1.4 SeedData runner with clock-relative slots (Addendum A)  (brief Addendum A)
      files: config/SeedData.java, application.properties, test/config/SeedDataTest.java | test: after startup 5 providers, 6 slots, 4 appointments; SLT-005 starts now+150 min | done when: green; disabled by `slotmind.seed.enabled=false` | evidence: SeedDataTest 2 tests green | commit:
- [x] 1.5 Error model: `ApiError`, `NotFoundException`, `ConflictException`, `UnprocessableException`, `GlobalExceptionHandler`  (DESIGN section 5)
      files: api/ApiError.java, service/*Exception (3), api/GlobalExceptionHandler.java, test/api/ErrorModelTest.java | test: 400 field errors, 404, 409 code, 422, 500 hides internals | done when: body shape matches DESIGN 5 | evidence: ErrorModelTest 9 tests green (test-only ErrorProbeController) | commit:
- [x] 1.6 First vertical slice: `GET /providers`  (T-5 provider list, 3.4)
      files: api/ProviderResponse.java, service/ProviderService.java, api/ProviderController.java, test/api/ProviderApiTest.java | test: IT 200 seed list sorted by name | done when: green and app runs against H2 | evidence: ProviderApiTest 1 test green | commit:

### S2 Core domain  (window 35-75)
- [x] 2.1 `POST /appointments`  (T-1, 3.1)
      files: api/AppointmentRequest.java, AppointmentResponse.java, AppointmentController.java, service/AppointmentService.java, test/api/BookAppointmentTest.java | test: IT 201 + Location, 400 field errors, 404, 409 SLOT_NOT_OPEN / PROVIDER_OFFLINE | done when: slot becomes BOOKED | evidence: BookAppointmentTest 8 green | commit:
- [x] 2.2 `GET /appointments` paged + filter, `GET /appointments/{id}`  (T-1, 3.2, 3.3)
      files: api/PageResponse.java, AppointmentController.java, service/AppointmentService.java, repository query, test/api/ListAppointmentsTest.java | test: IT status filter, size capped at 50, sort by slot start, 400 bad status, 404, embeds pending suggestion with one query | done when: green | evidence: ListAppointmentsTest 6 green | commit:
- [x] 2.3 `PATCH /providers/{id}/status` (state change + marks CONFIRMED appointments RESCHEDULE_PENDING, no event yet)  (T-1, 3.5)
      files: api/ProviderController.java, ProviderStatusRequest/Response.java, service/ProviderService.java, test/api/ProviderStatusTest.java | test: IT PRV-001 OFFLINE returns [APT-001, APT-002]; same status no-op; 400/404 | done when: green | evidence: ProviderStatusTest 4 green | commit:
- [x] 2.4 `GET /slots` and `PATCH /slots/{id}/status` (CANCELLED only)  (T-1, 3.6, 3.7)
      files: api/SlotController.java, SlotStatusRequest/Response.java, service/SlotService.java, test/api/SlotApiTest.java | test: IT filter+paging; cancel marks holder RESCHEDULE_PENDING; non-CANCELLED target gives 409 ILLEGAL_TRANSITION | done when: green | evidence: SlotApiTest 6 green | commit:
- [x] 2.5 Scheduling seam and rule engine  (T-2, 1.3, 1.4)
      files: scheduling/SchedulingStrategy.java (with nested request/recommendation records), RuleBasedStrategy.java, StrategyRegistry.java, test/scheduling/RuleBasedStrategyTest.java, test/scheduling/StrategyRegistryTest.java | test: unit tiers, URGENT fallback, excluded slots, offline provider, ties, empty; registry picks by name | done when: green | evidence: RuleBasedStrategyTest 7 + StrategyRegistryTest 3 green | commit:
- [x] 2.6 `RescheduleService.plan` with rules only: snapshot, strategy, persist, dedupe, `attention_reason`  (T-2, T-4 core, I5, I6)
      files: service/RescheduleService.java, service/SnapshotBuilder.java, api/SuggestionResponse.java (from-entity factory), test/service/RescheduleServiceTest.java | test: seed APT-001 and APT-002 both get SLT-005; duplicate (appointment, trigger) skipped; no candidate sets `attention_reason` | done when: green | evidence: RescheduleServiceTest 6 + SeedScenarioTest 2 green (demo: APT-001 and APT-002 both get SLT-005) | commit:
- [x] 2.7 `POST /appointments/{id}/suggest` (+ `?strategy=`)  (T-1, 3.8)
      files: api/AppointmentController.java, service/AppointmentService.java, service/StrategyParam.java (validates `?strategy=`), test/api/SuggestApiTest.java | test: IT 201, 409 pending exists, 409 wrong status, 422 no candidate, 400 unknown strategy | done when: green | evidence: SuggestApiTest 6 green | commit:
- [x] 2.8 `PATCH /suggestions/{id}` accept and reject  (T-1 transactional accept, 3.9, I2)
      files: api/SuggestionController.java, DecisionResponse.java, service/SuggestionService.java, test/api/DecisionApiTest.java | test: IT accept moves appointment to RESCHEDULED, old slot OPEN, new slot BOOKED; reject keeps RESCHEDULE_PENDING; 409 for stale slot / not pending / offline provider; rollback leaves nothing partial | done when: green | evidence: DecisionApiTest 9 green incl. 6-thread double accept (1x200, 5x409), repeated 3x stable | commit:

### S3 AI layer  (window 75-100)
- [x] 3.1 `LlmClient` seam, `FakeLlmClient`, `LLMGateway` on `anthropic-java`, startup key check  (T-3, I10)
      files: ai/LlmClient.java, ai/LLMGateway.java, ai/LlmProperties.java, test/ai/FakeLlmClient.java, test/ai/LlmGatewayTest.java | test: blank key means no network call and a clear exception; timeout config applied; key never in toString/logs | done when: green | evidence: LlmGatewayTest 3 green (blank key -> NO_API_KEY, no network; key absent from toString; settings applied) | commit:
- [x] 3.2 `PromptBuilder`: two system prompts, user template, name sanitising, local time format  (T-3 two prompts, AGT-2)
      files: ai/PromptBuilder.java, test/ai/PromptBuilderTest.java | test: A17, A18 (marker text per trigger; no patient name; injected provider name sanitised) | done when: green | evidence: PromptBuilderTest 5 green (A17, A18 incl. injected provider name) | commit:
- [x] 3.3 `LlmResponseValidator`: extract, strict parse, membership, provider, confidence, reasoning cleaning  (T-3 validation, I7)
      files: ai/LlmSuggestion.java, ai/LlmResponseValidator.java, ai/FallbackCause.java, test/ai/LlmResponseValidatorTest.java | test: A1-A3, A6-A13 | done when: green, every failure returns a typed cause | evidence: LlmResponseValidatorTest 12 green (A1-A3, A6-A13); a12 failed twice, code fixed, assertions unchanged | commit:
- [x] 3.4 `AiStrategy`: call, repair retry, fallback to rules, counters  (T-3 fallback, T-2)
      files: ai/AiStrategy.java (in ai/, not scheduling/, so scheduling stays dependency-free), ai/AiMetrics.java, test/scheduling/AiStrategyTest.java | test: A4, A5, A14, A15, A16, A21 with fake client | done when: green | evidence: AiStrategyTest 12 green (A4, A5, A14-A16, A20, A21, repair budget, cap) | commit:
- [x] 3.5 Wire into `RescheduleService`: persist-time slot re-check, no-candidate skips the LLM, config switch; one real call  (T-3, I3)
      files: service/RescheduleService.java, application.properties, test/service/AiRescheduleTest.java, docs/BUILD_LOG.md | test: A19, A20, `strategy=ai/rule` switching; manual real call recorded | done when: green; real call result logged | evidence: AiRescheduleTest 6 green (A19, A20, ai/rule switching, cause persisted). Real call PENDING: needs LLM_API_KEY | commit:

### S4 Agentic loop and concurrency  (window 100-130)
- [ ] 4.1 Async executor, `RescheduleRequested` event, `RescheduleHandler` (AFTER_COMMIT, per-id try/catch)  (T-4, ADR-005)
      files: config/AsyncConfig.java, service/RescheduleRequested.java, service/RescheduleHandler.java, test/service/RescheduleHandlerTest.java | test: each id planned; one failing id does not stop the rest; not run on rollback | done when: green | evidence: | commit:
- [ ] 4.2 Publish `PROVIDER_OFFLINE` from `ProviderService`  (T-4, demo path)
      files: service/ProviderService.java, test/service/OfflineLoopTest.java | test: IT PATCH PRV-001 OFFLINE returns immediately, then both appointments get PENDING suggestions (polling wait) | done when: green | evidence: | commit:
- [ ] 4.3 Publish `SLOT_CANCELLED` from `SlotService`  (T-4)
      files: service/SlotService.java, test/service/CancelLoopTest.java | test: IT cancel SLT-002 gives one SLOT_CANCELLED suggestion; cancel of an unheld slot publishes nothing | done when: green | evidence: | commit:
- [ ] 4.4 Dedupe and failure paths through the loop  (T-4 duplicates, AI failure not silent)
      files: test/service/LoopFailureTest.java, service/RescheduleService.java (fixes only) | test: repeated trigger creates no duplicate; fake LLM failure still yields a RULES suggestion with cause; ROUTINE pediatric appointment gets `attention_reason NO_CANDIDATE_SLOT` | done when: green | evidence: | commit:
- [ ] 4.5 Thread-based concurrency tests  (AGT-3, I1, I2)
      files: test/service/ConcurrentAcceptTest.java | test: N threads accept two suggestions for one slot gives exactly one 200 and the rest 409; same suggestion twice gives one success; N concurrent bookings of one slot gives one 201 | done when: green and repeatable (run 5x) | evidence: | commit:
- [ ] 4.6 End-to-end walkthrough test  (brief walkthrough trace, demo)
      files: test/e2e/WalkthroughTest.java | test: book, PRV-001 OFFLINE, suggestions appear, accept first (200), accept second (409) | done when: green | evidence: | commit:

### S5 UI  (window 130-150)
- [ ] 5.1 API client, polling hook (4 s), queue list with suggestion card, badges, loading/error  (T-5 floor)
      files: ui/src/api.js, ui/src/usePolling.js, ui/src/App.jsx, ui/src/App.css, ui/src/components/Queue.jsx | test: `npm run lint` and `npm run build` pass; manual: queue shows APT-001/002 after OFFLINE | done when: fields match DESIGN section 3 | evidence: | commit:
- [ ] 5.2 Accept/Reject, 409 message from `details[0]`, needs-attention row with Request suggestion  (T-5 floor)
      files: ui/src/components/Queue.jsx, ui/src/api.js, ui/src/App.css | test: lint, build, manual second accept shows slot-taken message | done when: green | evidence: | commit:
- [ ] 5.3 Providers panel with OFFLINE/AVAILABLE buttons and slot list  (T-5 floor)
      files: ui/src/components/Providers.jsx, ui/src/components/Slots.jsx, ui/src/App.jsx | test: lint, build, manual full demo path | done when: demo path works in the browser | evidence: | commit:
- [ ] 5.4 [OPTIONAL, first cut] `POST /appointments/{id}/suggest/stream` SSE bonus  (+5)
      files: api/StreamController.java, test/api/StreamApiTest.java | test: IT receives status then result events | done when: green | evidence: | commit:

### S6 Prove and ship  (150-180, freeze at 150)
- [ ] 6.1 README: run, env vars, docker, tests, endpoint list, demo notes, known limits  (deliverable)
      files: README.md | test: commands in README executed once | done when: accurate | evidence: | commit:
- [ ] 6.2 ADR final pass and secrets scan  (Gate 3)
      files: docs/ADR.md | test: `git grep -nI` for key patterns clean; `.env` ignored | done when: ADR matches code; "Not built" verified | evidence: | commit:
- [ ] 6.3 Clean-shell run from a fresh clone: `mvnw test`, Postgres profile, `npm ci && npm run build`  (Gate 3)
      files: none | test: all green | done when: demo path works on Postgres | evidence: | commit:
- [ ] 6.4 Demo script and 5-minute walkthrough notes; user records the video and makes the repo public  (submission)
      files: docs/DEMO.md | test: dry run of the script | done when: user says done | evidence: | commit:

## Cut order (when behind by more than 15 minutes at a boundary)
1) UI polish 2) SSE to polling (already polling; drop 5.4) 3) extras beyond the brief (`?strategy=`, `GET /appointments/{id}`) 4) optional deployment 5) tests beyond failure paths. NEVER cut: validation, fallback, conflict handling, integration tests of the main flow, README, ADR, secrets scan.
