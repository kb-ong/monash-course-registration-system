import { Ban, Check, Clock, Dot } from 'lucide-react'
import type { ReactNode } from 'react'
import { cn } from '../../lib/cn'

export type BadgeVariant =
  | 'neutral'
  | 'credit'
  | 'registered'
  | 'open'
  | 'closing'
  | 'full'
  | 'faculty'

interface BadgeProps {
  variant?: BadgeVariant
  children: ReactNode
  className?: string
  icon?: ReactNode
  showIcon?: boolean
}

const styles: Record<BadgeVariant, string> = {
  neutral: 'bg-monash-mist text-ink-700',
  credit: 'bg-chip text-ink-700',
  registered: 'bg-success-50 text-success-600',
  open: 'bg-success-50 text-success-600',
  closing: 'bg-warning-50 text-warning-700',
  full: 'bg-danger-50 text-danger-600',
  faculty: 'border border-line-300 bg-surface text-ink-700',
}

const defaultIcons: Partial<Record<BadgeVariant, ReactNode>> = {
  registered: <Check className="h-3 w-3" aria-hidden="true" />,
  open: <Dot className="h-3 w-3" aria-hidden="true" />,
  closing: <Clock className="h-3 w-3" aria-hidden="true" />,
  full: <Ban className="h-3 w-3" aria-hidden="true" />,
}

export default function Badge({
  variant = 'neutral',
  children,
  className,
  icon,
  showIcon = true,
}: BadgeProps) {
  const resolvedIcon = icon ?? defaultIcons[variant]
  return (
    <span
      className={cn(
        'inline-flex h-[22px] items-center gap-1.5 rounded-pill px-2 text-overline uppercase',
        styles[variant],
        className,
      )}
    >
      {showIcon && resolvedIcon ? resolvedIcon : null}
      {children}
    </span>
  )
}
