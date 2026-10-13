import { useState } from 'react'
import { api } from '../api'

export default function Providers({ providers, onChanged }) {
  const [message, setMessage] = useState(null)

  async function setStatus(id, status) {
    setMessage(null)
    try {
      await api.setProviderStatus(id, status)
    } catch (e) {
      setMessage(e.message)
    }
    onChanged()
  }

  return (
    <section>
      <h2>Providers</h2>
      <ul className="list">
        {(providers ?? []).map((p) => (
          <li key={p.id}>
            <span>
              <strong>{p.name}</strong> <span className="muted">{p.specialty}</span>
            </span>
            <span className={`badge status-${p.status}`}>{p.status}</span>
            {p.status === 'OFFLINE' ? (
              <button className="secondary" onClick={() => setStatus(p.id, 'AVAILABLE')}>
                Set available
              </button>
            ) : (
              <button className="danger" onClick={() => setStatus(p.id, 'OFFLINE')}>
                Set offline
              </button>
            )}
          </li>
        ))}
      </ul>
      {message && <p className="error">{message}</p>}
    </section>
  )
}
