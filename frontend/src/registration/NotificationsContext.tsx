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
import { getNotifications, markNotificationRead } from '../api/notifications'
import type { NotificationsResponse } from '../types/api'

interface NotificationsContextValue {
  data: NotificationsResponse | null
  unreadCount: number
  loading: boolean
  error: ApiError | null
  refresh: () => Promise<void>
  markRead: (notificationId: number) => Promise<void>
}

const NotificationsContext = createContext<NotificationsContextValue | null>(null)

const POLL_INTERVAL_MS = 30_000

/**
 * FR11: the in-app notification inbox. The server is the source of truth, so the inbox is polled
 * while the app is open; a promotion that happens in another browser appears without a reload.
 */
export function NotificationsProvider({ children }: { children: ReactNode }) {
  const [data, setData] = useState<NotificationsResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ApiError | null>(null)

  const refresh = useCallback(async () => {
    try {
      const next = await getNotifications()
      setData(next)
      setError(null)
    } catch (caught) {
      setError(normalizeApiError(caught))
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    getNotifications(controller.signal)
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

    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') void refresh()
    }, POLL_INTERVAL_MS)
    const onFocus = () => void refresh()
    window.addEventListener('focus', onFocus)

    return () => {
      controller.abort()
      window.clearInterval(timer)
      window.removeEventListener('focus', onFocus)
    }
  }, [refresh])

  const markRead = useCallback(
    async (notificationId: number) => {
      await markNotificationRead(notificationId)
      setData((current) => {
        if (!current) return current
        const items = current.items.map((item) =>
          item.id === notificationId ? { ...item, read: true } : item,
        )
        return { ...current, items, unreadCount: items.filter((item) => !item.read).length }
      })
    },
    [],
  )

  const value = useMemo<NotificationsContextValue>(
    () => ({
      data,
      unreadCount: data?.unreadCount ?? 0,
      loading,
      error,
      refresh,
      markRead,
    }),
    [data, loading, error, refresh, markRead],
  )

  return (
    <NotificationsContext.Provider value={value}>{children}</NotificationsContext.Provider>
  )
}

export function useNotifications(): NotificationsContextValue {
  const context = useContext(NotificationsContext)
  if (!context) throw new Error('useNotifications must be used inside NotificationsProvider')
  return context
}
