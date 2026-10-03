export type ApiEnvelope<T> = { data: T; timestamp: string }
export type UserProfile = { username: string; role: 'USER' | 'ADMIN' }
export type LoginResponse = { accessToken: string; tokenType: string; expiresIn: number; user: UserProfile }
export type RetryPolicy = {
  maxRetries: number
  initialBackoffSeconds: number
  maxBackoffSeconds: number
  backoffMultiplier: number
}
export type Job = {
  id: string
  name: string
  description: string | null
  status: 'DRAFT' | 'ACTIVE' | 'PAUSED' | 'CANCELLED' | 'ARCHIVED'
  createdAt: string
  updatedAt: string
  createdBy: string
  priority: number
  timeoutSeconds: number
  retryPolicy: RetryPolicy
  configuration: Record<string, unknown>
}
export type Page<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}
export type Execution = {
  id: string
  jobId: string
  attemptId: string
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'TIMED_OUT'
  attemptNumber: number
  workerId: string | null
  startedAt: string | null
  completedAt: string | null
  errorCode: string | null
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}
export type Task = {
  id: string
  jobId: string
  name: string
  taskType: string
  sequenceOrder: number
  status: string
  configuration: Record<string, unknown>
  createdAt: string
  updatedAt: string
}
export type Schedule = {
  id: string
  jobId: string
  type: 'ONE_TIME' | 'CRON'
  cronExpression: string | null
  oneTimeAt: string | null
  nextRunAt: string
  lastScheduledAt: string | null
}

export class ApiError extends Error {
  constructor(message: string, readonly status: number, readonly code?: string) {
    super(message)
  }
}

let bearerToken: string | null = localStorage.getItem('jobtantra.accessToken')
let onUnauthorized: (() => void) | undefined

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

export function saveSession(token: string) {
  bearerToken = token
  localStorage.setItem('jobtantra.accessToken', token)
}

export function clearSession() {
  bearerToken = null
  localStorage.removeItem('jobtantra.accessToken')
}

export function userFromToken(token: string): UserProfile {
  try {
    const payload = token.split('.')[1]
    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/')
    const claims = JSON.parse(atob(normalized)) as { sub?: string; roles?: string[] }
    return {
      username: claims.sub ?? 'user',
      role: claims.roles?.includes('ADMIN') ? 'ADMIN' : 'USER',
    }
  } catch {
    return { username: 'user', role: 'USER' }
  }
}

async function request<T>(path: string, init: RequestInit = {}, protectedRequest = true): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (protectedRequest && bearerToken) headers.set('Authorization', `Bearer ${bearerToken}`)
  const response = await fetch(path, { ...init, headers })
  if (response.status === 401 && protectedRequest) {
    clearSession()
    onUnauthorized?.()
  }
  const payload = response.status === 204 ? null : await response.json().catch(() => null)
  if (!response.ok) {
    const message = response.status === 403
      ? 'Access denied for this resource.'
      : (payload as { message?: string; error?: string } | null)?.message
        ?? (payload as { error?: string } | null)?.error
        ?? `Request failed (${response.status})`
    throw new ApiError(message, response.status, (payload as { code?: string } | null)?.code)
  }
  return payload as T
}

const data = async <T,>(path: string, init?: RequestInit, protectedRequest = true) =>
  (await request<ApiEnvelope<T>>(path, init, protectedRequest)).data

export const api = {
  login: (username: string, password: string) => data<LoginResponse>('/api/v1/auth/login', {
    method: 'POST', body: JSON.stringify({ username, password }),
  }, false),
  jobs: (page = 0, size = 100) => data<Page<Job>>(`/api/v1/jobs?page=${page}&size=${size}`),
  job: (id: string) => data<Job>(`/api/v1/jobs/${id}`),
  createJob: (body: unknown) => data<Job>('/api/v1/jobs', { method: 'POST', body: JSON.stringify(body) }),
  activateJob: (id: string) => data<Job>(`/api/v1/jobs/${id}/activate`, { method: 'POST' }),
  cancelJob: (id: string) => data<Job>(`/api/v1/jobs/${id}/cancel`, { method: 'POST' }),
  deleteJob: (id: string) => request<void>(`/api/v1/jobs/${id}`, { method: 'DELETE' }),
  executions: (id: string) => data<Execution[]>(`/api/v1/jobs/${id}/executions`),
  execution: (id: string) => data<Execution>(`/api/v1/executions/${id}`),
  cancelExecution: (id: string) => data<Execution>(`/api/v1/executions/${id}/cancel`, { method: 'POST' }),
  retryExecution: (id: string) => data<Execution>(`/api/v1/executions/${id}/retry`, { method: 'POST' }),
  tasks: (id: string) => data<Task[]>(`/api/v1/jobs/${id}/tasks`),
  schedule: (id: string) => data<Schedule>(`/api/v1/jobs/${id}/schedule`),
}