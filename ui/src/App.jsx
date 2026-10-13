import './App.css'
import { api } from './api'
import Providers from './components/Providers'
import Queue from './components/Queue'
import Slots from './components/Slots'
import { usePolling } from './usePolling'

export default function App() {
  const queue = usePolling(() => api.appointments('RESCHEDULE_PENDING'))
  const providers = usePolling(api.providers)
  const slots = usePolling(api.slots)

  const refreshAll = () => {
    queue.refresh()
    providers.refresh()
    slots.refresh()
  }
  const error = queue.error ?? providers.error ?? slots.error
  const loading = queue.loading && providers.loading && slots.loading

  return (
    <main>
      <header>
        <h1>SlotMind</h1>
        <span className="muted">AI rescheduling · refreshes every 4 s</span>
      </header>
      {error && <p className="banner">Cannot reach the backend: {error.message}. Showing last data.</p>}
      {loading ? (
        <p className="muted">Loading…</p>
      ) : (
        <div className="grid">
          <Queue data={queue.data} onChanged={refreshAll} />
          <div className="side">
            <Providers providers={providers.data} onChanged={refreshAll} />
            <Slots data={slots.data} onChanged={refreshAll} />
          </div>
        </div>
      )}
    </main>
  )
}
