import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import {
  dropRegistration,
  getRegistrations,
  registerCourse,
} from '../api/registrations'
import { normalizeApiError, type ApiError } from '../api/client'
import type {
  DropResponse,
  RegisterResponse,
  RegistrationItem,
  RegistrationsResponse,
} from '../types/api'

interface RegistrationsContextValue {
  data: RegistrationsResponse | null
  loading: boolean
  error: ApiError | null
  refresh: () => Promise<void>
  isRegistered: (courseId: string) => boolean
  getRegistration: (courseId: string) => RegistrationItem | undefined
  register: (courseId: string) => Promise<RegisterResponse>
  drop: (registrationId: string) => Promise<DropResponse>
  mutatingCourseId: string | null
  mutatingRegistrationId: string | null
}

const RegistrationsContext = createContext<RegistrationsContextValue | null>(null)

export function RegistrationsProvider({ children }: { children: ReactNode }) {
  const [data, setData] = useState<RegistrationsResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ApiError | null>(null)
  const [mutatingCourseId, setMutatingCourseId] = useState<string | null>(null)
  const [mutatingRegistrationId, setMutatingRegistrationId] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    try {
      const next = await getRegistrations()
      setData(next)
      setError(null)
    } catch (caught) {
      setError(normalizeApiError(caught))
    }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    getRegistrations(controller.signal)
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

    return () => controller.abort()
  }, [])

  const register = useCallback(
    async (courseId: string) => {
      setMutatingCourseId(courseId)
      try {
        const result = await registerCourse(courseId)
        await refresh()
        return result
      } finally {
        setMutatingCourseId(null)
      }
    },
    [refresh],
  )

  const drop = useCallback(
    async (registrationId: string) => {
      setMutatingRegistrationId(registrationId)
      try {
        const result = await dropRegistration(registrationId)
        await refresh()
        return result
      } finally {
        setMutatingRegistrationId(null)
      }
    },
    [refresh],
  )

  const isRegistered = useCallback(
    (courseId: string) => data?.items.some((item) => item.course.id === courseId) ?? false,
    [data],
  )

  const getRegistration = useCallback(
    (courseId: string) => data?.items.find((item) => item.course.id === courseId),
    [data],
  )

  const value = useMemo<RegistrationsContextValue>(
    () => ({
      data,
      loading,
      error,
      refresh,
      isRegistered,
      getRegistration,
      register,
      drop,
      mutatingCourseId,
      mutatingRegistrationId,
    }),
    [
      data,
      loading,
      error,
      refresh,
      isRegistered,
      getRegistration,
      register,
      drop,
      mutatingCourseId,
      mutatingRegistrationId,
    ],
  )

  return (
    <RegistrationsContext.Provider value={value}>{children}</RegistrationsContext.Provider>
  )
}

export function useRegistrations(): RegistrationsContextValue {
  const context = useContext(RegistrationsContext)
  if (!context) throw new Error('useRegistrations must be used inside RegistrationsProvider')
  return context
}
