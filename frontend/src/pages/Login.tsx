import { motion, useReducedMotion } from 'framer-motion'
import { ArrowRight, Eye, EyeOff, Info } from 'lucide-react'
import { useRef, useState, type FormEvent } from 'react'
import { Navigate, useLocation, useSearchParams } from 'react-router-dom'
import { normalizeApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import Button from '../components/ui/Button'
import { Input } from '../components/ui/Input'
import { formatDate, semesterName } from '../lib/format'
import { useRegistrationPeriod } from '../registration/RegistrationPeriodContext'

interface LocationState {
  from?: unknown
}

function destinationFrom(state: unknown): string {
  if (typeof state !== 'object' || state === null) return '/'
  const from = (state as LocationState).from
  if (typeof from !== 'string') return '/'
  if (
    !from.startsWith('/') ||
    from.startsWith('//') ||
    from.startsWith('/\\') ||
    from.startsWith('/login')
  ) {
    return '/'
  }
  return from
}

export default function Login() {
  const { status, login } = useAuth()
  const { period } = useRegistrationPeriod()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const reduceMotion = useReducedMotion()

  const [studentId, setStudentId] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [fieldErrors, setFieldErrors] = useState<{ studentId?: string; password?: string }>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [expiredDismissed, setExpiredDismissed] = useState(false)

  const studentIdRef = useRef<HTMLInputElement>(null)
  const passwordRef = useRef<HTMLInputElement>(null)

  const destination = destinationFrom(location.state)
  const expired = searchParams.get('reason') === 'expired' && !expiredDismissed
  const semester = period ? semesterName(period.semester) : 'Semester 2 2026'
  const semesterEyebrow = period
    ? `SEMESTER ${period.semester.slice(-1)} · ${period.semester.slice(0, 4)}`
    : 'SEMESTER 2 · 2026'
  const hasFieldErrors = Boolean(fieldErrors.studentId || fieldErrors.password)
  const periodCopy = period
    ? period.open
      ? `Registration open until ${formatDate(period.endAt)}`
      : new Date(period.serverTime) < new Date(period.startAt)
        ? `Registration opens ${formatDate(period.startAt)}`
        : `Registration closed ${formatDate(period.endAt)}`
    : 'Registration window published by the university'

  if (status === 'authenticated') {
    return <Navigate to={destination} replace />
  }

  const validate = (): boolean => {
    const next: { studentId?: string; password?: string } = {}
    const trimmedId = studentId.trim()
    if (!trimmedId) {
      next.studentId = 'Enter your student ID.'
    } else if (!/^[0-9]{8}$/.test(trimmedId)) {
      next.studentId = 'Enter your 8-digit student ID.'
    }
    if (!password) {
      next.password = 'Enter your password.'
    }
    setFieldErrors(next)
    if (next.studentId) {
      studentIdRef.current?.focus()
      return false
    }
    if (next.password) {
      passwordRef.current?.focus()
      return false
    }
    return true
  }

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (submitting) return
    setFormError(null)
    if (!validate()) return

    setSubmitting(true)
    try {
      await login(studentId.trim(), password)
    } catch (caught) {
      const error = normalizeApiError(caught)
      if (error.code === 'INVALID_CREDENTIALS' || error.status === 401) {
        setFormError("We couldn't sign you in. Check your student ID and password.")
        setPassword('')
        window.setTimeout(() => passwordRef.current?.focus(), 0)
      } else if (error.code === 'NETWORK_ERROR') {
        setFormError("Can't reach the server. Check your connection and try again.")
      } else {
        setFormError(error.message)
      }
    } finally {
      setSubmitting(false)
    }
  }

  const fillDemo = () => {
    setStudentId('32012345')
    setPassword('Password123!')
    setFieldErrors({})
    setFormError(null)
    studentIdRef.current?.focus()
  }

  return (
    <div className="min-h-screen bg-canvas">
      <a
        href="#main"
        className="skip-link sr-only rounded-md bg-surface px-4 py-2 text-body font-semibold text-monash-navy focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-30"
      >
        Skip to main content
      </a>
      <header className="on-navy absolute inset-x-0 top-0 z-20 flex h-16 items-center lg:h-[72px]">
        <div className="main-container flex w-full items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="text-[14px] font-bold tracking-[0.18em] text-white">MONASH</span>
            <span className="h-5 w-px bg-white/25" aria-hidden="true" />
            <span className="text-body-sm text-white/70">Course Registration</span>
          </div>
          <span className="hidden text-body-sm text-white/60 sm:block">{semester}</span>
        </div>
      </header>

      <main id="main" className="grid min-h-screen lg:grid-cols-[3fr_2fr]">
        <section className="hero-navy hero-grain on-navy relative flex flex-col justify-center overflow-hidden px-6 pb-8 pt-20 sm:px-10 sm:pb-12 sm:pt-24 lg:px-16 lg:py-24 lg:pt-28">
          <motion.div
            className="relative z-10 max-w-[560px]"
            initial={reduceMotion ? { opacity: 0 } : { opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: reduceMotion ? 0 : 0.56, ease: [0.16, 1, 0.3, 1] }}
          >
            <p className="hidden text-overline uppercase tracking-[0.08em] text-white/60 sm:block">
              {semesterEyebrow}
            </p>
            <span className="gold-rule mt-5 hidden sm:block" aria-hidden="true" />
            <h1 className="mt-0 max-w-[18ch] font-display text-[30px] font-semibold leading-[34px] tracking-[-0.02em] text-white sm:mt-7 sm:max-w-[15ch] sm:text-display-1 sm:leading-[60px]">
              Course registration, without the queue.
            </h1>
            <p className="mt-5 hidden max-w-[46ch] text-body-lg text-white/70 sm:block">
              Choose your units, watch the seats and lock in your timetable in minutes.
            </p>

            <ul className="mt-8 hidden space-y-2.5 text-body-sm text-white/75 sm:block">
              <li className="flex items-center gap-2.5">
                <span className="h-1.5 w-1.5 shrink-0 bg-monash-gold" aria-hidden="true" />
                {semester} · Open enrolment
              </li>
              <li className="flex items-center gap-2.5">
                <span className="h-1.5 w-1.5 shrink-0 bg-monash-gold" aria-hidden="true" />
                {periodCopy}
              </li>
            </ul>

            <div className="mt-10 hidden max-w-[420px] rounded-xl p-5 glass-card sm:block">
              <p className="font-display text-[18px] leading-7 text-white/90">
                “Registration took forty seconds. I could see every seat before I chose.”
              </p>
              <p className="mt-3 text-body-sm text-white/60">
                Aisha R. · Bachelor of Computer Science
              </p>
            </div>
          </motion.div>
        </section>

        <section className="flex items-center justify-center bg-canvas px-4 py-10 sm:px-8 lg:px-10 lg:py-24">
          <motion.div
            className="w-full max-w-[440px]"
            initial={reduceMotion ? { opacity: 0 } : { opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{
              duration: reduceMotion ? 0 : 0.44,
              delay: reduceMotion ? 0 : 0.08,
              ease: [0.16, 1, 0.3, 1],
            }}
          >
            <div className="rounded-2xl border border-line-200 bg-surface p-6 shadow-e1 sm:p-8">
              <h2 className="font-display text-h2 text-ink-900">Welcome back</h2>
              <p className="mt-2 text-body text-ink-700">
                Sign in to register for {semester}.
              </p>

              <div className="mt-6 space-y-4">
                {expired ? (
                  <div
                    role="status"
                    className="flex items-start gap-2 rounded-md border border-blue-600/15 bg-blue-50 px-3 py-2.5 text-body-sm text-monash-navy"
                  >
                    <Info className="mt-0.5 h-4 w-4 shrink-0 text-blue-600" aria-hidden="true" />
                    <span className="flex-1">Your session expired. Please sign in again.</span>
                    <button
                      type="button"
                      onClick={() => setExpiredDismissed(true)}
                      className="-m-1 rounded p-1 text-body-sm font-semibold text-blue-600 hover:text-blue-500"
                      aria-label="Dismiss session notice"
                    >
                      Dismiss
                    </button>
                  </div>
                ) : null}

                {formError ? (
                  <div
                    role="alert"
                    aria-live="assertive"
                    className="rounded-md border border-danger-600/20 bg-danger-50 px-3 py-2.5 text-body-sm text-danger-600"
                  >
                    {formError}
                  </div>
                ) : null}

                {hasFieldErrors ? (
                  <div
                    role="alert"
                    className="rounded-md border border-danger-600/20 bg-danger-50 px-3 py-2.5 text-body-sm text-danger-600"
                  >
                    Check the highlighted fields and try again.
                  </div>
                ) : null}

                <form onSubmit={(event) => void handleSubmit(event)} noValidate className="space-y-4">
                  <Input
                    ref={studentIdRef}
                    label="Student ID"
                    name="studentId"
                    inputMode="numeric"
                    autoComplete="username"
                    placeholder="32012345"
                    maxLength={8}
                    inputSize="lg"
                    value={studentId}
                    onChange={(event) => {
                      setStudentId(event.target.value)
                      setFormError(null)
                      if (fieldErrors.studentId) setFieldErrors((prev) => ({ ...prev, studentId: undefined }))
                    }}
                    error={fieldErrors.studentId}
                    disabled={submitting}
                    autoFocus
                  />

                  <Input
                    ref={passwordRef}
                    label="Password"
                    name="password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="current-password"
                    placeholder="Your password"
                    maxLength={72}
                    inputSize="lg"
                    value={password}
                    onChange={(event) => {
                      setPassword(event.target.value)
                      setFormError(null)
                      if (fieldErrors.password) setFieldErrors((prev) => ({ ...prev, password: undefined }))
                    }}
                    error={fieldErrors.password}
                    disabled={submitting}
                    rightSlot={
                      <button
                        type="button"
                        onClick={() => setShowPassword((shown) => !shown)}
                        aria-pressed={showPassword}
                        aria-label={showPassword ? 'Hide password' : 'Show password'}
                        className="flex h-11 w-11 items-center justify-center rounded-md text-ink-400 transition-colors duration-1 hover:bg-monash-mist hover:text-ink-700"
                      >
                        {showPassword ? (
                          <EyeOff className="h-4 w-4" aria-hidden="true" />
                        ) : (
                          <Eye className="h-4 w-4" aria-hidden="true" />
                        )}
                      </button>
                    }
                  />

                  <Button
                    type="submit"
                    variant="primary"
                    size="lg"
                    loading={submitting}
                    className="w-full"
                  >
                    {submitting ? 'Signing in…' : 'Sign in'}
                  </Button>
                </form>

                <div className="border-t border-line-200 pt-4">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <p className="text-overline uppercase tracking-[0.08em] text-ink-400">
                        Demo account
                      </p>
                      <p className="mt-1 font-mono text-body-sm text-ink-700">
                        32012345 / Password123!
                      </p>
                    </div>
                    <button
                      type="button"
                      onClick={fillDemo}
                      className="inline-flex items-center gap-1 text-body-sm font-semibold text-blue-600 hover:text-blue-500"
                    >
                      Use demo
                      <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
                    </button>
                  </div>
                </div>
              </div>
            </div>

            <p className="mt-4 text-center text-body-sm text-ink-400">
              Monash University · Phase 1
            </p>
          </motion.div>
        </section>
      </main>
    </div>
  )
}
