import { Loader2 } from 'lucide-react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'

export function AuthLoadingScreen() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-5 bg-canvas">
      <div className="flex items-center gap-3">
        <span className="text-[14px] font-bold tracking-[0.18em] text-monash-navy">MONASH</span>
        <span className="h-5 w-px bg-line-300" aria-hidden="true" />
        <span className="text-body-sm text-ink-500">Course Registration</span>
      </div>
      <span className="flex items-center gap-2 text-body-sm text-ink-500" role="status">
        <Loader2 className="h-4 w-4 animate-spin text-monash-blue" aria-hidden="true" />
        Restoring your session
      </span>
    </div>
  )
}

export default function RequireAuth() {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') {
    return <AuthLoadingScreen />
  }

  if (status === 'anonymous') {
    return (
      <Navigate
        to="/login"
        replace
        state={{ from: `${location.pathname}${location.search}` }}
      />
    )
  }

  return <Outlet />
}
