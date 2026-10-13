# Demo script (5 minutes)

Setup before recording: fresh backend start (so the seed is clean and times are in the future), `LLM_API_KEY` exported so the badge shows **AI**, UI open on http://localhost:5173. Seed times are relative to startup, so restart the backend if it has been running for more than about 60 minutes.

## 0:00 The problem (30 s)
Provider goes offline, the front desk phones patients by hand. SlotMind detects it, proposes a rebook with an AI-written explanation, and a human confirms. Nothing changes until Accept.

## 0:30 Show the starting state (30 s)
Providers panel: Dr. Chen BUSY, Dr. Verma AVAILABLE. Slots panel: SLT-005 (Verma) OPEN. Queue is empty.

## 1:00 Trigger (60 s)
Click **Set offline** on Dr. Sarah Chen.
- The request returns immediately. Planning happens in the background after commit (`RescheduleHandler`, AFTER_COMMIT, bounded executor).
- Both of her patients appear in the queue, first with a "No suggestion yet" row, then with a card: AI badge, confidence, trigger "Provider offline", and the reasoning a coordinator can read to the patient.
Point out: the slot id in the card is validated against live data; the model can only choose from a closed list built by the rule engine.

## 2:00 Accept and the race (90 s)
- Accept APT-001's suggestion: appointment becomes RESCHEDULED, SLT-005 turns BOOKED, Chen's old slot is freed, all in one transaction.
- Accept APT-002's suggestion (same slot SLT-005): red message `SLOT_NOT_OPEN: SLT-005 is BOOKED`. That is the AGT-3 case. Say why: suggestions do not reserve slots (ADR-006), accept re-validates, and three layers back it up (state check, `@Version`, UNIQUE `slot_id`). `ConcurrentAcceptTest` fires 6 threads at one slot, 5 times: one 200, five 409.
- Reject it instead, then use "Request suggestion" (or `POST /suggest`) to show the rejected slot is excluded next time and the case becomes "Needs attention: NO_CANDIDATE_SLOT" when nothing else is open.

## 3:30 Resilience (60 s)
Restart without a key (or `?strategy=rule`): the same flow still produces suggestions, with a `fallback: NO_API_KEY` badge and `source: Rules`. Timeout, gateway error, malformed JSON, a slot outside the list and a model refusal all take the same path (typed `fallbackCause`, counted in `slotmind.ai.fallback`). The pluggable seam is `SchedulingStrategy`; a `RoomConstraintStrategy` would be one new class.

## 4:30 Close (30 s)
Tests: `./mvnw test` 146 green, including the async loop and the thread races. Decisions: `ADR.md`. Not built and why: the ADR "Not built" table (outbox, holds, SSE, auth).

## curl fallback (if the UI misbehaves)
```
curl -X PATCH localhost:8080/providers/PRV-001/status -H 'Content-Type: application/json' -d '{"status":"OFFLINE"}'
curl "localhost:8080/appointments?status=RESCHEDULE_PENDING"
curl -X PATCH localhost:8080/suggestions/<id> -H 'Content-Type: application/json' -d '{"status":"ACCEPTED"}'
```
