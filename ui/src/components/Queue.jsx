import { useState } from 'react'
import { api } from '../api'

const fmt = (iso) =>
  new Date(iso).toLocaleString([], { weekday: 'short', hour: '2-digit', minute: '2-digit' })

const TRIGGER = {
  INITIAL: 'Requested',
  PROVIDER_OFFLINE: 'Provider offline',
  SLOT_CANCELLED: 'Slot cancelled',
}

function SuggestionCard({ appointment, busy, onDecide }) {
  const s = appointment.pendingSuggestion
  return (
    <div className="suggestion">
      <div className="badges">
        <span className={`badge ${s.source === 'LLM' ? 'ai' : 'rules'}`}>
          {s.source === 'LLM' ? 'AI' : 'Rules'}
        </span>
        {s.fallbackCause && <span className="badge warn">fallback: {s.fallbackCause}</span>}
        <span className="badge">{TRIGGER[s.triggerReason] ?? s.triggerReason}</span>
        <span className={`badge ${s.lowConfidence ? 'warn' : 'ok'}`}>
          {Math.round(s.confidence * 100)}% confidence{s.lowConfidence ? ' (low)' : ''}
        </span>
      </div>
      <p className="move">
        {s.recommendedSlot.providerName} at <strong>{fmt(s.recommendedSlot.startTime)}</strong>
        {s.alternateProvider && <span className="muted"> (different provider)</span>}
      </p>
      <p className="reasoning">{s.reasoning}</p>
      <div className="actions">
        <button disabled={busy} onClick={() => onDecide(s.id, 'ACCEPTED')}>
          Accept
        </button>
        <button className="secondary" disabled={busy} onClick={() => onDecide(s.id, 'REJECTED')}>
          Reject
        </button>
      </div>
    </div>
  )
}

function Row({ appointment, onChanged }) {
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState(null)

  async function run(action) {
    setBusy(true)
    setMessage(null)
    try {
      await action()
    } catch (e) {
      // 409 / 422: show the backend's stable code and text from details[0]
      setMessage(e.message)
    } finally {
      setBusy(false)
      onChanged()
    }
  }

  const a = appointment
  const planning = !a.pendingSuggestion && !a.attentionReason
  return (
    <li className="card">
      <div className="head">
        <strong>{a.patientName}</strong>
        <span className={`badge ${a.type === 'URGENT' ? 'warn' : ''}`}>{a.type}</span>
        <span className="muted">
          {a.id} · was {a.provider.name} {fmt(a.slot.startTime)}
        </span>
      </div>
      {a.pendingSuggestion && (
        <SuggestionCard
          appointment={a}
          busy={busy}
          onDecide={(id, status) => run(() => api.decide(id, status))}
        />
      )}
      {planning && <p className="muted">Planning a new slot…</p>}
      {a.attentionReason && (
        <div className="attention">
          <span className="badge warn">Needs attention: {a.attentionReason}</span>
          <button disabled={busy} onClick={() => run(() => api.suggest(a.id))}>
            Request suggestion
          </button>
        </div>
      )}
      {message && <p className="error">{message}</p>}
    </li>
  )
}

export default function Queue({ data, onChanged }) {
  const items = data?.items ?? []
  return (
    <section>
      <h2>
        Reschedule queue <span className="count">{items.length}</span>
      </h2>
      {items.length === 0 ? (
        <p className="muted">Nothing needs rescheduling.</p>
      ) : (
        <ul className="cards">
          {items.map((a) => (
            <Row key={a.id} appointment={a} onChanged={onChanged} />
          ))}
        </ul>
      )}
    </section>
  )
}
