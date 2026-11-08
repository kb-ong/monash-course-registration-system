import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { login as loginRequest, getMe } from '../api/auth'
import { SESSION_EXPIRED_EVENT, tokenStore } from '../api/client'
import type { Student } from '../types/api'

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

interface AuthContextValue {
  status: AuthStatus
  student: Student | null
  token: string | null
  login: (studentId: string, password: string) => Promise<Student>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate()
  const location = useLocation()
  const [token, setToken] = useState<string | null>(() => tokenStore.get())
  const [student, setStudent] = useState<Student | null>(null)
  const [status, setStatus] = useState<AuthStatus>(() =>
    tokenStore.get() ? 'loading' : 'anonymous',
  )

  // Rehydrate a stored session exactly once on app bootstrap.
  useEffect(() => {
    const stored = tokenStore.get()
    if (!stored) {
      setStatus('anonymous')
      return
    }

    let cancelled = false
    setStatus('loading')
    getMe()
      .then((profile) => {
        if (cancelled) return
        setStudent(profile)
        setToken(stored)
        setStatus('authenticated')
      })
      .catch(() => {
        if (cancelled) return
        tokenStore.clear()
        setToken(null)
        setStudent(null)
        setStatus('anonymous')
      })

    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    const onExpired = () => {
      setToken(null)
      setStudent(null)
      setStatus('anonymous')
      if (location.pathname !== '/login') {
        navigate('/login?reason=expired', {
          replace: true,
          state: { from: `${location.pathname}${location.search}` },
        })
      }
    }

    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired)
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired)
  }, [navigate, location.pathname, location.search])

  const login = useCallback(async (studentId: string, password: string) => {
    const result = await loginRequest(studentId, password)
    tokenStore.set(result.token)
    setToken(result.token)
    setStudent(result.student)
    setStatus('authenticated')
    return result.student
  }, [])

  const logout = useCallback(() => {
    tokenStore.clear()
    setToken(null)
    setStudent(null)
    setStatus('anonymous')
    navigate('/login', { replace: true })
  }, [navigate])

  const value = useMemo<AuthContextValue>(
    () => ({ status, student, token, login, logout }),
    [status, student, token, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
