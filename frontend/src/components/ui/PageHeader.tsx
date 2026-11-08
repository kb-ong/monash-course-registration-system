import type { ReactNode } from 'react'
import { cn } from '../../lib/cn'

interface PageHeaderProps {
  eyebrow?: string
  title: string
  description?: string
  actions?: ReactNode
  children?: ReactNode
  className?: string
}

export default function PageHeader({
  eyebrow,
  title,
  description,
  actions,
  children,
  className,
}: PageHeaderProps) {
  return (
    <div className={cn('flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between', className)}>
      <div className="min-w-0">
        {eyebrow ? (
          <p className="text-overline uppercase tracking-[0.08em] text-blue-600">{eyebrow}</p>
        ) : null}
        <h1 className={cn('font-display text-h1 text-ink-900', eyebrow && 'mt-2')}>{title}</h1>
        <span className="gold-rule mt-4" aria-hidden="true" />
        {description ? <p className="mt-3 max-w-[68ch] text-body-lg text-ink-700">{description}</p> : null}
      </div>
      {actions ? <div className="flex flex-wrap items-center gap-3">{actions}</div> : null}
      {children ? <div className="w-full lg:w-auto">{children}</div> : null}
    </div>
  )
}
