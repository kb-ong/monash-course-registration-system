import { motion, useReducedMotion } from 'framer-motion'
import { cn } from '../../lib/cn'

interface StatRingProps {
  value: number
  max?: number
  caption?: string
  onDark?: boolean
  className?: string
  size?: number
  loading?: boolean
}

const RADIUS = 40
const CIRCUMFERENCE = 2 * Math.PI * RADIUS

export default function StatRing({
  value,
  max = 24,
  caption = 'credit points',
  onDark = false,
  className,
  size = 96,
  loading = false,
}: StatRingProps) {
  const reduceMotion = useReducedMotion()
  const safeMax = max > 0 ? max : 24
  const safeValue = Math.max(0, value)
  const ratio = Math.min(1, safeValue / safeMax)
  const incomplete = safeValue > safeMax

  const tone = incomplete || safeValue >= safeMax ? 'text-monash-gold' : safeValue / safeMax >= 0.75 ? 'text-warning-700' : 'text-blue-600'
  const strokeColour =
    incomplete || safeValue >= safeMax
      ? 'var(--monash-gold)'
      : safeValue / safeMax >= 0.75
        ? 'var(--warning-700)'
        : 'var(--monash-blue)'

  return (
    <div className={cn('flex flex-col items-center gap-2', className)}>
      <div
        className="relative"
        style={{ width: size, height: size }}
        role="meter"
        aria-busy={loading || undefined}
        aria-valuemin={0}
        aria-valuemax={safeMax}
        aria-valuenow={safeValue}
        aria-valuetext={
          loading
            ? 'Loading credit points'
            : `${safeValue} of ${safeMax} credit points used`
        }
        aria-label="Credit points used"
      >
        <svg
          viewBox="0 0 96 96"
          className="h-full w-full -rotate-90"
          aria-hidden="true"
          focusable="false"
        >
          <circle cx="48" cy="48" r={RADIUS} fill="none" stroke="var(--track)" strokeWidth="8" />
          <motion.circle
            cx="48"
            cy="48"
            r={RADIUS}
            fill="none"
            stroke={strokeColour}
            strokeWidth="8"
            strokeLinecap="round"
            strokeDasharray={CIRCUMFERENCE}
            initial={false}
            animate={{ strokeDashoffset: CIRCUMFERENCE * (1 - ratio) }}
            transition={
              reduceMotion
                ? { duration: 0 }
                : { duration: 0.32, ease: [0.2, 0.8, 0.2, 1] }
            }
          />
        </svg>
        <div
          className={cn(
            'absolute inset-0 flex items-baseline justify-center gap-1',
            onDark ? 'text-white' : 'text-ink-900',
          )}
        >
          <span className="tabular text-[22px] font-semibold leading-none">
            {loading ? '--' : safeValue}
          </span>
          <span
            className={cn(
              'tabular text-body-sm',
              onDark ? 'text-footer-muted' : 'text-ink-500',
            )}
          >
            / {safeMax}
          </span>
        </div>
      </div>
      <p
        className={cn(
          'text-body-sm',
          onDark ? 'text-footer-muted' : 'text-ink-500',
          tone === 'text-monash-gold' && onDark && 'text-monash-gold',
        )}
      >
        {caption}
      </p>
    </div>
  )
}
