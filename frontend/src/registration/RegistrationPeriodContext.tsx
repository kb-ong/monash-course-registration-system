import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { getRegistrationPeriod } from '../api/registrations'
import { normalizeApiError, type ApiError } from '../api/client'
import type { RegistrationPeriod } from '../types/api'

interface RegistrationPeriodContextValue {
  period: RegistrationPeriod | null
  loading: boolean
  error: ApiError | null
  refresh: () => Promise<void>
}

const RegistrationPeriodContext = createContext<RegistrationPeriodContextValue | null>(null)

export function RegistrationPeriodProvider({ children }: { children: ReactNode }) {
  const [period, setPeriod] = useState<RegistrationPeriod | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ApiError | null>(null)

  const refresh = useCallback(async () => {
    try {
      const next = await getRegistrationPeriod()
      setPeriod(next)
      setError(null)
    } catch (caught) {
      setError(normalizeApiError(caught))
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    getRegistrationPeriod(controller.signal)
      .then((next) => {
        if (controller.signal.aborted) return
        setPeriod(next)
        setError(null)
      })
      .catch((caught) => {
        if (controller.signal.aborted) return
        setError(normalizeApiError(caught))
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    return () => controller.abort()
  }, [])

  const value = useMemo<RegistrationPeriodContextValue>(
    () => ({ period, loading, error, refresh }),
    [period, loading, error, refresh],
  )

  return (
    <RegistrationPeriodContext.Provider value={value}>
      {children}
    </RegistrationPeriodContext.Provider>
  )
}

export function useRegistrationPeriod(): RegistrationPeriodContextValue {
  const context = useContext(RegistrationPeriodContext)
  if (!context) {
    throw new Error('useRegistrationPeriod must be used inside RegistrationPeriodProvider')
  }
  return context
}
