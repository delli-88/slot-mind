// Thin client over the SlotMind backend. Field names follow DESIGN section 3.
const BASE = '/api'

export class ApiError extends Error {
  constructor(status, body) {
    // details[0] carries the stable code (e.g. SLOT_NOT_OPEN: SLT-005 is BOOKED)
    super(body?.details?.[0] ?? body?.message ?? `HTTP ${status}`)
    this.status = status
    this.body = body
  }
}

async function request(path, options = {}) {
  let res
  try {
    res = await fetch(BASE + path, {
      headers: { 'Content-Type': 'application/json' },
      ...options,
    })
  } catch {
    throw new ApiError(0, { message: 'Backend unreachable' })
  }
  const text = await res.text()
  const body = text ? JSON.parse(text) : null
  if (!res.ok) throw new ApiError(res.status, body)
  return body
}

const patch = (path, status) =>
  request(path, { method: 'PATCH', body: JSON.stringify({ status }) })

export const api = {
  appointments: (status) =>
    request(`/appointments?size=50${status ? `&status=${status}` : ''}`),
  providers: () => request('/providers'),
  slots: () => request('/slots?size=50'),
  setProviderStatus: (id, status) => patch(`/providers/${id}/status`, status),
  cancelSlot: (id) => patch(`/slots/${id}/status`, 'CANCELLED'),
  decide: (suggestionId, status) => patch(`/suggestions/${suggestionId}`, status),
  suggest: (appointmentId) =>
    request(`/appointments/${appointmentId}/suggest`, { method: 'POST' }),
}
