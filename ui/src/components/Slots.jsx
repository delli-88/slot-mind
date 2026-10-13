import { useState } from 'react'
import { api } from '../api'

const fmt = (iso) =>
  new Date(iso).toLocaleString([], { weekday: 'short', hour: '2-digit', minute: '2-digit' })

export default function Slots({ data, onChanged }) {
  const [message, setMessage] = useState(null)

  async function cancel(id) {
    setMessage(null)
    try {
      await api.cancelSlot(id)
    } catch (e) {
      setMessage(e.message)
    }
    onChanged()
  }

  return (
    <section>
      <h2>Slots</h2>
      <ul className="list">
        {(data?.items ?? []).map((s) => (
          <li key={s.id}>
            <span>
              <strong>{s.id}</strong> <span className="muted">{s.providerName}</span>
            </span>
            <span>{fmt(s.startTime)}</span>
            <span className={`badge status-${s.status}`}>{s.status}</span>
            {s.status !== 'CANCELLED' ? (
              <button className="danger" onClick={() => cancel(s.id)}>
                Cancel
              </button>
            ) : (
              <span />
            )}
          </li>
        ))}
      </ul>
      {message && <p className="error">{message}</p>}
    </section>
  )
}
