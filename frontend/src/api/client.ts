import axios from 'axios'
import type { ApiErrorPayload, ErrorCode } from '../types/api'

export const TOKEN_KEY = 'monash.token'
export const SESSION_EXPIRED_EVENT = 'monash:session-expired'

export const tokenStore = {
  get(): string | null {
    try {
      return window.localStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },
  set(token: string): void {
    try {
      window.localStorage.setItem(TOKEN_KEY, token)
    } catch {
      // Storage can be unavailable in private modes; the in-memory session still works.
    }
  },
  clear(): void {
    try {
      window.localStorage.removeItem(TOKEN_KEY)
    } catch {
      // Ignore.
    }
  },
}

export class ApiError extends Error {
  readonly code: ErrorCode
  readonly status: number | null
  readonly isNetwork: boolean

  constructor(code: ErrorCode, message: string, status: number | null, isNetwork = false) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
    this.isNetwork = isNetwork
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isApiErrorPayload(value: unknown): value is ApiErrorPayload {
  if (!isRecord(value) || !isRecord(value.error)) return false
  return typeof value.error.code === 'string' && typeof value.error.message === 'string'
}

export function normalizeApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error

  if (axios.isAxiosError(error)) {
    const status = error.response?.status ?? null
    const payload: unknown = error.response?.data

    if (isApiErrorPayload(payload)) {
      return new ApiError(payload.error.code, payload.error.message, status)
    }

    if (status === null) {
      return new ApiError(
        'NETWORK_ERROR',
        "Can't reach the server. Check your connection and try again.",
        null,
        true,
      )
    }

    if (status === 401) {
      return new ApiError('UNAUTHORIZED', 'Your session is no longer valid.', status)
    }

    return new ApiError(
      'UNKNOWN_ERROR',
      error.response?.statusText || 'Something went wrong. Try again in a moment.',
      status,
    )
  }

  if (error instanceof Error) {
    return new ApiError('UNKNOWN_ERROR', error.message, null)
  }

  return new ApiError('UNKNOWN_ERROR', 'Something went wrong. Try again in a moment.', null)
}

export function isSessionExpiredError(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401
}

const client = axios.create({
  baseURL: '/api',
  headers: {
    Accept: 'application/json',
    'Content-Type': 'application/json',
  },
})

client.interceptors.request.use((config) => {
  const token = tokenStore.get()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

client.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    const normalized = normalizeApiError(error)

    if (
      normalized.status === 401 &&
      !axios.isCancel(error) &&
      !isLoginRequest(error) &&
      tokenStore.get() !== null
    ) {
      tokenStore.clear()
      window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT))
    }

    return Promise.reject(normalized)
  },
)

function isLoginRequest(error: unknown): boolean {
  if (!axios.isAxiosError(error)) return false
  return (error.config?.url ?? '').includes('/auth/login')
}

export default client
