import { useCallback, useEffect, useRef, useState } from 'react'

// Calls load() now and every `ms` milliseconds. Keeps the last good data while an error is shown.
export function usePolling(load, ms = 4000) {
  const [state, setState] = useState({ data: null, error: null, loading: true })
  const loadRef = useRef(load)
  useEffect(() => {
    loadRef.current = load
  })

  const refresh = useCallback(async () => {
    try {
      const data = await loadRef.current()
      setState({ data, error: null, loading: false })
    } catch (error) {
      setState((s) => ({ ...s, error, loading: false }))
    }
  }, [])

  useEffect(() => {
    refresh()
    const timer = setInterval(refresh, ms)
    return () => clearInterval(timer)
  }, [refresh, ms])

  return { ...state, refresh }
}
