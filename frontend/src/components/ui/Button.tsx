import { Loader2 } from 'lucide-react'
import { forwardRef, type ButtonHTMLAttributes } from 'react'
import { cn } from '../../lib/cn'

export type ButtonVariant =
  | 'primary'
  | 'secondary'
  | 'gold'
  | 'ghost'
  | 'danger'
  | 'danger-ghost'
  | 'link'

export type ButtonSize = 'sm' | 'md' | 'lg' | 'icon'

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  size?: ButtonSize
  loading?: boolean
}

const variantClasses: Record<ButtonVariant, string> = {
  primary:
    'bg-monash-navy text-white hover:bg-navy-700 active:bg-navy-900 active:scale-[.99] disabled:bg-line-200 disabled:text-ink-400',
  secondary:
    'bg-surface text-ink-900 border border-line-300 hover:bg-monash-mist active:bg-line-200 disabled:border-line-200 disabled:bg-surface disabled:text-ink-400',
  gold: 'bg-monash-gold text-ink-900 hover:bg-gold-500 active:bg-gold-700 active:scale-[.99] disabled:bg-gold-100 disabled:text-ink-400',
  ghost:
    'bg-transparent text-blue-600 hover:bg-blue-50 active:bg-blue-100 disabled:bg-transparent disabled:text-ink-400',
  danger:
    'bg-danger-600 text-white hover:bg-danger-700 active:bg-danger-800 active:scale-[.99] disabled:bg-danger-50 disabled:text-danger-300',
  'danger-ghost':
    'bg-transparent text-danger-600 hover:bg-danger-50 active:bg-danger-50 disabled:bg-transparent disabled:text-ink-400',
  link: 'bg-transparent p-0 text-blue-600 underline underline-offset-[3px] hover:text-blue-500 hover:bg-transparent disabled:text-ink-400',
}

const sizeClasses: Record<ButtonSize, string> = {
  sm: 'h-8 px-3 text-button-sm rounded-sm',
  md: 'h-10 px-4 text-button rounded-md',
  lg: 'h-12 px-6 text-button rounded-md',
  icon: 'h-10 w-10 min-w-[44px] p-0 rounded-md sm:min-w-0',
}

export function buttonClassName({
  variant = 'primary',
  size = 'md',
  className,
}: {
  variant?: ButtonVariant
  size?: ButtonSize
  className?: string
} = {}): string {
  return cn(
    'inline-flex items-center justify-center gap-2 font-semibold transition-colors duration-1',
    'disabled:cursor-not-allowed',
    variant !== 'link' && 'min-h-[44px] sm:min-h-0',
    variantClasses[variant],
    sizeClasses[size],
    className,
  )
}

const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  {
    variant = 'primary',
    size = 'md',
    loading = false,
    className,
    children,
    disabled,
    type = 'button',
    ...rest
  },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={buttonClassName({ variant, size, className })}
      {...rest}
    >
      {loading ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : null}
      {children}
    </button>
  )
})

export default Button
