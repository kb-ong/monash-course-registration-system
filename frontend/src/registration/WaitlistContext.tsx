import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { normalizeApiError, type ApiError } from '../api/client'
import { getWaitlist, joinWaitlist, leaveWaitlist } from '../api/waitlist'
import type {
  WaitlistItem,
  WaitlistJoinResponse,
  WaitlistLeaveResponse,
  WaitlistResponse,
} from '../types/api'

interface WaitlistContextValue {
  data: WaitlistResponse | null
  loading: boolean
  error: ApiError | null
  refresh: () => Promise<void>
  entryFor: (courseId: string) => WaitlistItem | undefined
  join: (courseId: string) => Promise<WaitlistJoinResponse>
  leave: (waitlistId: number) => Promise<WaitlistLeaveResponse>
  mutatingWaitlistId: number | null
}

const WaitlistContext = createContext<WaitlistContextValue | null>(null)

/**
 * FR11: the authenticated student's queue places. Positions are derived server-side; the client
 * mirrors them and never computes a position from local state.
 */
export function WaitlistProvider({ children }: { children: ReactNode }) {
  const [data, setData] = useState<WaitlistResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ApiError | null>(null)
  const [mutatingWaitlistId, setMutatingWaitlistId] = useState<number | null>(null)

  const refresh = useCallback(async () => {
    try {
      const next = await getWaitlist()
      setData(next)
      setError(null)
    } catch (caught) {
      setError(normalizeApiError(caught))
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    getWaitlist(controller.signal)
      .then((next) => {
        if (controller.signal.aborted) return
        setData(next)
        setError(null)
      })
      .catch((caught) => {
        if (controller.signal.aborted) return
        setError(normalizeApiError(caught))
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })

    // Positions move when other students leave or are promoted; a light poll plus focus refresh
    // keeps the displayed position honest without a manual reload.
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') void refresh()
    }, 30_000)
    const onFocus = () => void refresh()
    window.addEventListener('focus', onFocus)

    return () => {
      controller.abort()
      window.clearInterval(timer)
      window.removeEventListener('focus', onFocus)
    }
  }, [refresh])

  const join = useCallback(
    async (courseId: string) => {
      const result = await joinWaitlist(courseId)
      setMutatingWaitlistId(result.waitlistId)
      try {
        await refresh()
        return result
      } finally {
        setMutatingWaitlistId(null)
      }
    },
    [refresh],
  )

  const leave = useCallback(
    async (waitlistId: number) => {
      setMutatingWaitlistId(waitlistId)
      try {
        const result = await leaveWaitlist(waitlistId)
        await refresh()
        return result
      } finally {
        setMutatingWaitlistId(null)
      }
    },
    [refresh],
  )

  const entryFor = useCallback(
    (courseId: string) => data?.items.find((item) => item.course.id === courseId),
    [data],
  )

  const value = useMemo<WaitlistContextValue>(
    () => ({
      data,
      loading,
      error,
      refresh,
      entryFor,
      join,
      leave,
      mutatingWaitlistId,
    }),
    [data, loading, error, refresh, entryFor, join, leave, mutatingWaitlistId],
  )

  return <WaitlistContext.Provider value={value}>{children}</WaitlistContext.Provider>
}

export function useWaitlist(): WaitlistContextValue {
  const context = useContext(WaitlistContext)
  if (!context) throw new Error('useWaitlist must be used inside WaitlistProvider')
  return context
}
