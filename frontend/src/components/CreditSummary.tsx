import { AlertTriangle, CheckCircle2 } from 'lucide-react'
import { cn } from '../lib/cn'

interface CreditSummaryProps {
  totalCredits: number
  maxCredits: number
  className?: string
}

/**
 * FR10: the catalogue/schedule-wide warning that always names the remaining available credits.
 * The server remains authoritative; this is the student-facing summary of the configured limit.
 */
export default function CreditSummary({
  totalCredits,
  maxCredits,
  className,
}: CreditSummaryProps) {
  const remaining = Math.max(0, maxCredits - totalCredits)
  const over = totalCredits > maxCredits
  const atLimit = !over && remaining === 0
  const near = !over && !atLimit && maxCredits > 0 && totalCredits / maxCredits >= 0.75
  const warning = over || atLimit || near

  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(
        'flex flex-col gap-1 rounded-md border px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:gap-4',
        over
          ? 'border-danger-600/20 bg-danger-50 text-danger-600'
          : warning
            ? 'border-monash-gold/30 bg-warning-50 text-warning-700'
            : 'border-line-200 bg-surface text-ink-700',
        className,
      )}
    >
      <p className="flex items-center gap-2 text-body-sm font-semibold">
        {warning ? (
          <AlertTriangle className="h-4 w-4 shrink-0" aria-hidden="true" />
        ) : (
          <CheckCircle2 className="h-4 w-4 shrink-0 text-success-600" aria-hidden="true" />
        )}
        <span>
          {totalCredits} of {maxCredits} credit points registered
        </span>
      </p>
      <p className={cn('pl-6 text-body-sm sm:pl-0', !warning && 'text-ink-500')}>
        {over
          ? `${totalCredits - maxCredits} credit${totalCredits - maxCredits === 1 ? '' : 's'} over the limit`
          : atLimit
            ? 'Credit limit reached'
            : `${remaining} credit${remaining === 1 ? '' : 's'} remaining`}
      </p>
    </div>
  )
}
