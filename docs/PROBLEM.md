# PROBLEM: SlotMind AI Re-Scheduling Engine

Source: `slotmind-brief 1.html`. Where this file and the brief disagree, the brief wins. Budget: **3 h** (brief says 5 h; user decided 3 h). Solo.

## 1. Goal
CareBridge front-desk staff fix cancellations by hand: slow, error-prone, and double-bookings happen. Build a **reactive re-scheduling engine**. When a provider goes `OFFLINE` or a slot is `CANCELLED`, the system finds the affected appointments, produces rebook suggestions (rule-based or AI) **asynchronously**, and queues them for front-desk approval. The AI is advisory; a human accepts every rebooking.

Not in scope: patient portal, billing, waitlist auto-fill, room/equipment constraints, notifications, multi-clinic, auth (sprints 2 and 3 of the roadmap; only leave seams for them).

## 2. Decisions made at `read brief` (user answers)
| # | Decision |
|---|---|
| D1 | Budget is 3 h, not the brief's 5 h |
| D2 | Reject keeps the appointment `RESCHEDULE_PENDING`; staff may call `POST /appointments/{id}/suggest` for a fresh suggestion |
| D3 | Alternate provider must have the **same specialty**; `URGENT` may fall back to any non-OFFLINE provider |
| D4 | React 18 (Vite); polling, not SSE, for the queue |
| D5 | LLM provider is the Claude API (Anthropic Messages API), key from `LLM_API_KEY` |

## 3. Tasks and rubric
| Task | Deliverable | Acceptance criteria (brief) | Pts |
|---|---|---|---|
| T-1 | Domain model and API | Appointment, Provider, Slot, RescheduleSuggestion with explicit state machines. Endpoints: `POST /appointments`, `GET /appointments?status=`, `PATCH /providers/{id}/status`, `PATCH /slots/{id}/status`, `POST /appointments/{id}/suggest`, `PATCH /suggestions/{id}`. Accept updates appointment + old slot + new slot **atomically, no double-booking** | 20 |
| T-2 | Pluggable scheduling engine | Given appointment + available slots/providers, return **ordered recommendations**. Rule-based: same provider's nearest open slot; else nearest open slot with matching specialty. Switchable at runtime via config. HTTP and async callers use the same contract. A new `RoomConstraintStrategy` = implement the contract and register, nothing more | 25 |
| T-3 | AI scheduling strategy | LLM gets appointment, urgency, available slots with times, provider roster, trigger context. Returns `slotId`, optional `alternateProviderId`, `confidence`, `reasoning` that staff can read to patients. Validate slot and provider IDs against data; recommended slot must be `OPEN`; fall back to rule-based on LLM failure. **Two prompts**: provider offline vs slot cancelled | 25 |
| T-4 | Agentic re-schedule loop | `OFFLINE` provider: all its `CONFIRMED` appointments get suggestions, `triggerReason=PROVIDER_OFFLINE`. `CANCELLED` slot: its appointment gets a suggestion, `SLOT_CANCELLED`. Endpoints return immediately (async). Skip duplicate `PENDING` suggestions per appointment + triggerReason. AI failure means rule-based fallback, **not a silent drop**. Human checkpoint: front desk confirms before accept finalizes | 15 |
| T-5 | Front-desk UI | Floor: `RESCHEDULE_PENDING` appointments with inline suggestion (slot time, provider, confidence, reasoning); Accept/Reject; badge `PROVIDER_OFFLINE` / `SLOT_CANCELLED` / manual; provider list with status; slot list or calendar; polling, loading, error states | 12 (+8 ceiling) |
| T-6 | ADR and live walkthrough | `ADR.md` covering: scheduling logic placement, strategy switching, transactional accept, agentic loop decoupling, extensibility. Format Context → Options → Decision → Tradeoffs | 20 |
| Bonus | SSE | `POST /appointments/{id}/suggest/stream`, SSE reasoning stream | +5 |

**Total 117** (the brief's header says 115, an arithmetic slip) plus up to +13 bonus.

Submission: public GitHub repo (the user makes it public, never the assistant), 5-minute demo, `ADR.md`. Demo must show **provider OFFLINE → auto suggestions → accept rebooks**. Walkthrough trace: book appointment → provider offline → suggestion appears → accept updates slot atomically.

## 4. Domain (from the brief)
- **Appointment**: patient name, type `ROUTINE | URGENT | FOLLOW_UP`, provider, slot. Lifecycle `CONFIRMED → RESCHEDULE_PENDING → RESCHEDULED → COMPLETED`.
- **Provider**: name, specialty `GENERAL | SPECIALIST | PEDIATRIC`, status `AVAILABLE | BUSY | OFFLINE`.
- **Slot**: start time, duration minutes, provider, status `OPEN | BOOKED | CANCELLED`.
- **RescheduleSuggestion**: appointment, recommended slot ID, optional alternate provider, confidence, patient-friendly reasoning, status `PENDING → ACCEPTED | REJECTED`, triggerReason `INITIAL | PROVIDER_OFFLINE | SLOT_CANCELLED`.
- Seed (Addendum A): 5 providers `PRV-001..005`, 6 slots `SLT-001..006`, 4 appointments `APT-001..004`, string IDs.
- LLM output schema: `{"slotId":"...","alternateProviderId":"...","confidence":0.88,"reasoning":"..."}`.
- Gateway (Addendum B): `com.slotmind.ai.LLMGateway.callLLM(prompt)`. Only a stub comment is supplied, so we write the implementation (Claude API) behind our own interface.

## 5. Invariants (every task must preserve these)
- **I1** A slot is held by at most one active appointment. Enforced by a DB constraint plus a conditional state change, not only by service code.
- **I2** Accept is one transaction: appointment → `RESCHEDULED` with the new slot, old slot freed, new slot `BOOKED`, suggestion `ACCEPTED`. All or nothing.
- **I3** Suggestions do **not** reserve slots. Two suggestions can point at the same slot; accept re-validates the slot is still `OPEN`, and the loser gets 409.
- **I4** Every rebooking passes a human accept. The model never writes data.
- **I5** No LLM call inside a database transaction. Trigger endpoints return immediately; work runs after commit on a bounded executor.
- **I6** At most one `PENDING` suggestion per (appointment, triggerReason).
- **I7** LLM output is untrusted. Slot must exist and be `OPEN`; provider must exist and not be `OFFLINE`; confidence in [0,1]. Any failure uses the rule-based fallback; nothing is silently dropped.
- **I8** Status changes only through entity methods that enforce legal transitions.
- **I9** A slot of an `OFFLINE` provider, or a `CANCELLED` slot, is never recommended.
- **I10** Secrets come only from `LLM_API_KEY`; the app starts without one (falls back to rules).

## 6. Assumptions (flagged so the user can overrule)
- A1 Seed `NOW() + n` means n **minutes**.
- A2 On a trigger, affected `CONFIRMED` appointments become `RESCHEDULE_PENDING` in the same synchronous transaction as the provider/slot change. Suggestions are then created async. This makes the UI queue correct immediately.
- A3 Accept frees the old slot to `OPEN`, except a `CANCELLED` slot stays `CANCELLED`. Slots of an `OFFLINE` provider are excluded from candidates (I9).
- A4 If no candidate exists (e.g. a non-urgent PEDIATRIC appointment, since no open pediatric slot exists; the seed's APT-003 is URGENT so it falls to tier 3 and does get a suggestion), no suggestion is created. The appointment stays `RESCHEDULE_PENDING` and is flagged as needing manual handling.
- A5 Only `CONFIRMED` appointments are re-planned on a trigger. `POST /suggest` also works for `RESCHEDULE_PENDING` (triggerReason `INITIAL`).
- A6 `GET /appointments` list gets pagination (cap on size) because it can grow. No idempotency key: double-booking is prevented by the slot constraint (I1).
- A7 `BUSY` providers stay bookable. Only `OFFLINE` blocks.

## 7. Open questions (none blocking)
- Start clock time (needed for the board's Freeze time).
- On accept, if the suggestion's slot was taken by another accept: mark the loser's suggestion stale/REJECTED automatically, or just return 409 and let staff reject? Default: 409 only, suggestion stays `PENDING`.

## 8. Out of scope / not built (to list in ADR "Not built")
Auth, caching, message broker, rate limiting, waitlist, notifications, room constraints, multi-clinic, Lombok, Spring AI, Flyway, SSE for the queue.
