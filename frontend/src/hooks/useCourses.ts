import { useCallback, useEffect, useRef, useState } from 'react'
import { getCourses } from '../api/courses'
import { normalizeApiError, type ApiError } from '../api/client'
import type { Course } from '../types/api'

interface UseCoursesOptions {
  semester?: string
  query?: string
  enabled?: boolean
}

interface UseCoursesResult {
  courses: Course[]
  loading: boolean
  refreshing: boolean
  error: ApiError | null
  reload: () => void
}

export default function useCourses({
  semester,
  query,
  enabled = true,
}: UseCoursesOptions = {}): UseCoursesResult {
  const [courses, setCourses] = useState<Course[]>([])
  const [loading, setLoading] = useState(enabled)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const [reloadToken, setReloadToken] = useState(0)
  const hasDataRef = useRef(false)

  const reload = useCallback(() => setReloadToken((token) => token + 1), [])

  useEffect(() => {
    if (!enabled) return

    const controller = new AbortController()
    if (hasDataRef.current) {
      setRefreshing(true)
    } else {
      setLoading(true)
    }
    setError(null)

    getCourses({ semester, q: query, signal: controller.signal })
      .then((result) => {
        if (controller.signal.aborted) return
        hasDataRef.current = true
        setCourses(result)
        setError(null)
      })
      .catch((caught) => {
        if (controller.signal.aborted) return
        setError(normalizeApiError(caught))
      })
      .finally(() => {
        if (controller.signal.aborted) return
        setLoading(false)
        setRefreshing(false)
      })

    return () => controller.abort()
  }, [semester, query, enabled, reloadToken])

  return { courses, loading, refreshing, error, reload }
}
