import type { HTMLAttributes } from 'react'
import { cn } from '../../lib/cn'

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  padded?: boolean
}

export default function Card({ className, padded = true, ...rest }: CardProps) {
  return (
    <div
      className={cn(
        'rounded-lg border border-line-200 bg-surface shadow-e1',
        padded && 'p-6',
        className,
      )}
      {...rest}
    />
  )
}
