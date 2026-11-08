import {
  AlertTriangle,
  CalendarClock,
  CalendarX2,
  CheckCircle2,
  Clock,
  Loader2,
} from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { cn } from '../lib/cn'
import {
  countdownParts,
  formatDate,
  formatDateTime,
  relativeTime,
} from '../lib/format'
import { useRegistrationPeriod } from '../registration/RegistrationPeriodContext'

type BannerTone = 'open' | 'opening' | 'pending' | 'closed' | 'unknown' | 'loading'

const tones: Record<
  BannerTone,
  { shell: string; accent: string; icon: ReactNode }
> = {
  open: {
    shell: 'border-success-600/15 bg-success-50 text-success-600',
    accent: 'bg-success-600',
    icon: <CheckCircle2 className="h-4 w-4" aria-hidden="true" />,
  },
  opening: {
    shell: 'border-monash-gold/25 bg-warning-50 text-warning-700',
    accent: 'bg-monash-gold',
    icon: <Clock className="h-4 w-4" aria-hidden="true" />,
  },
  pending: {
    shell: 'border-blue-600/15 bg-blue-50 text-monash-navy',
    accent: 'bg-blue-600',
    icon: <CalendarClock className="h-4 w-4" aria-hidden="true" />,
  },
  closed: {
    shell: 'border-danger-600/15 bg-danger-50 text-danger-600',
    accent: 'bg-danger-600',
    icon: <CalendarX2 className="h-4 w-4" aria-hidden="true" />,
  },
  unknown: {
    shell: 'border-line-200 bg-monash-mist text-ink-700',
    accent: 'bg-ink-400',
    icon: <AlertTriangle className="h-4 w-4" aria-hidden="true" />,
  },
  loading: {
    shell: 'border-line-200 bg-monash-mist text-ink-500',
    accent: 'bg-line-300',
    icon: <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />,
  },
}

export default function StatusBanner({ className }: { className?: string }) {
  const { period, error, refresh } = useRegistrationPeriod()
  const [now, setNow] = useState(() =>
    period ? new Date(period.serverTime).getTime() : Date.now(),
  )
  const offsetRef = useRef(0)
  const boundaryRef = useRef<string | null>(null)

  useEffect(() => {
    if (!period) return
    offsetRef.current = new Date(period.serverTime).getTime() - Date.now()
    setNow(Date.now() + offsetRef.current)
  }, [period])

  const startMs = period ? new Date(period.startAt).getTime() : 0
  const endMs = period ? new Date(period.endAt).getTime() : 0
  const startDelta = startMs - now
  const openingSoon = Boolean(period) && startDelta > 0 && startDelta <= 7 * 86_400_000

  useEffect(() => {
    if (!period) return
    const timer = window.setInterval(() => setNow(Date.now() + offsetRef.current), 1000)
    return () => window.clearInterval(timer)
  }, [period])

  useEffect(() => {
    if (!period) return
    const boundary = period.open ? period.endAt : period.startAt
    const reached = now >= new Date(boundary).getTime()
    const shouldRefetch = period.open ? reached : reached && now <= endMs
    if (shouldRefetch && boundaryRef.current !== boundary) {
      boundaryRef.current = boundary
      void refresh()
    }
  }, [now, period, endMs, refresh])

  let tone: BannerTone = 'loading'
  let message = 'Checking registration status'
  let meta: ReactNode = null
  const nowIso = new Date(now).toISOString()

  if (!period && error) {
    tone = 'unknown'
    message = 'Registration status unavailable'
    meta = (
      <button
        type="button"
        onClick={() => void refresh()}
        className="rounded-md px-2 py-1 text-body-sm font-semibold text-ink-700 underline underline-offset-[3px] hover:text-ink-900"
      >
        Retry
      </button>
    )
  } else if (period) {
    if (period.open) {
      tone = 'open'
      message = `Registration open · closes ${formatDateTime(period.endAt)}`
      meta = (
        <span className="tabular text-body-sm font-normal text-success-600">
          {relativeTime(period.endAt, nowIso)}
        </span>
      )
    } else if (openingSoon) {
      tone = 'opening'
      message = `Registration opens ${formatDate(period.startAt)}`
      meta = (
        <span className="tabular text-body-sm font-semibold text-warning-700">
          <span aria-hidden="true">{countdownParts(period.startAt, now)}</span>
          <span className="sr-only">{relativeTime(period.startAt, nowIso)}</span>
        </span>
      )
    } else if (now < startMs) {
      tone = 'pending'
      message = `Registration opens ${formatDate(period.startAt)}`
      meta = (
        <span className="tabular text-body-sm font-normal text-monash-navy/80">
          {relativeTime(period.startAt, nowIso)}
        </span>
      )
    } else {
      tone = 'closed'
      message = `Registration closed ${formatDate(period.endAt)}`
      meta = <span className="text-body-sm font-normal text-danger-600/80">Read-only</span>
    }
  }

  const style = tones[tone]

  return (
    <div
      className={cn(
        'status-banner relative flex min-h-[44px] flex-col gap-1 overflow-hidden rounded-md border py-2.5 pl-5 pr-4 sm:flex-row sm:items-center sm:justify-between sm:gap-4 sm:py-2',
        style.shell,
        className,
      )}
      aria-live="polite"
    >
      <span className={cn('absolute inset-y-0 left-0 w-[3px]', style.accent)} aria-hidden="true" />
      <div className="flex min-w-0 items-center gap-2">
        {style.icon}
        <p className="text-body-sm font-semibold">{message}</p>
      </div>
      {meta ? <div className="pl-6 sm:pl-0">{meta}</div> : null}
    </div>
  )
}
