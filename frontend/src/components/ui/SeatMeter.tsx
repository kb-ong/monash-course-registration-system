import { motion, useReducedMotion } from 'framer-motion'
import { cn } from '../../lib/cn'

interface SeatMeterProps {
  availableSeats: number
  capacity: number
  className?: string
  compact?: boolean
}

function fillColour(ratio: number): string {
  if (ratio <= 0) return 'bg-danger-600'
  if (ratio <= 0.1) return 'bg-warning-700'
  if (ratio <= 0.5) return 'bg-monash-gold'
  return 'bg-success-600'
}

export default function SeatMeter({
  availableSeats,
  capacity,
  className,
  compact = false,
}: SeatMeterProps) {
  const reduceMotion = useReducedMotion()
  const safeCapacity = Math.max(0, capacity)
  const safeAvailable = Math.max(0, Math.min(availableSeats, safeCapacity))
  const ratio = safeCapacity > 0 ? safeAvailable / safeCapacity : 0
  const percent = Math.round(ratio * 1000) / 10

  const rightLabel =
    safeAvailable === 0
      ? 'No seats available'
      : ratio <= 0.1
        ? `Only ${safeAvailable} left`
        : `${safeAvailable} of ${safeCapacity} available`

  const rightTone =
    safeAvailable === 0
      ? 'text-danger-600'
      : ratio <= 0.1
        ? 'font-semibold text-warning-700'
        : 'text-ink-500'

  return (
    <div className={cn('flex flex-col gap-2', className)}>
      <div className="flex items-baseline justify-between gap-3">
        <span className="text-overline uppercase text-ink-500">Seats</span>
        <span className={cn('tabular text-body-sm', rightTone)}>{rightLabel}</span>
      </div>
      <div
        role="meter"
        aria-valuemin={0}
        aria-valuemax={safeCapacity}
        aria-valuenow={safeAvailable}
        aria-valuetext={`${safeAvailable} of ${safeCapacity} seats available`}
        aria-label="Seats available"
        className={cn(
          'h-1.5 w-full overflow-hidden rounded-pill bg-track',
          safeAvailable === 0 && 'ring-1 ring-inset ring-danger-50',
          compact && 'h-1',
        )}
      >
        <motion.div
          className={cn('h-full rounded-pill', fillColour(ratio))}
          initial={false}
          animate={{ width: `${percent}%` }}
          transition={
            reduceMotion
              ? { duration: 0 }
              : { duration: 0.32, ease: [0.2, 0.8, 0.2, 1] }
          }
        />
      </div>
    </div>
  )
}
