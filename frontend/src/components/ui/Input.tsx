import { AlertCircle, ChevronDown } from 'lucide-react'
import {
  forwardRef,
  useId,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
} from 'react'
import { cn } from '../../lib/cn'

interface FieldChrome {
  label?: string
  hideLabel?: boolean
  hint?: string
  error?: string
}

function FieldMessages({
  hint,
  error,
  hintId,
  errorId,
}: {
  hint?: string
  error?: string
  hintId: string
  errorId: string
}) {
  return (
    <>
      {hint && !error ? (
        <p id={hintId} className="text-body-sm text-ink-500">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} className="flex items-start gap-1.5 text-body-sm text-danger-600">
          <AlertCircle className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
          <span>{error}</span>
        </p>
      ) : null}
    </>
  )
}

export interface InputProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size'>,
    FieldChrome {
  inputSize?: 'md' | 'lg'
  leftIcon?: ReactNode
  rightSlot?: ReactNode
  containerClassName?: string
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  {
    label,
    hideLabel = false,
    hint,
    error,
    inputSize = 'md',
    leftIcon,
    rightSlot,
    id,
    className,
    containerClassName,
    'aria-describedby': ariaDescribedBy,
    ...rest
  },
  ref,
) {
  const generatedId = useId()
  const inputId = id ?? `field-${generatedId}`
  const hintId = `${inputId}-hint`
  const errorId = `${inputId}-error`
  const describedBy =
    [ariaDescribedBy, hint && !error ? hintId : null, error ? errorId : null]
      .filter(Boolean)
      .join(' ') || undefined

  return (
    <div className={cn('flex flex-col gap-1.5', containerClassName)}>
      {label ? (
        <label
          htmlFor={inputId}
          className={cn('text-label text-ink-700', hideLabel && 'sr-only')}
        >
          {label}
        </label>
      ) : null}
      <div className="relative">
        {leftIcon ? (
          <span
            className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-ink-400"
            aria-hidden="true"
          >
            {leftIcon}
          </span>
        ) : null}
        <input
          ref={ref}
          id={inputId}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={cn(
            'w-full rounded-md border bg-surface text-body text-ink-900 transition-colors duration-1',
            'placeholder:text-ink-400',
            'hover:border-ink-400',
            'focus:outline-none focus-visible:outline-none focus:ring-[3px]',
            'disabled:cursor-not-allowed disabled:border-line-200 disabled:bg-monash-mist disabled:text-ink-400',
            inputSize === 'lg' ? 'h-12' : 'h-11',
            leftIcon ? 'pl-11' : 'pl-3.5',
            rightSlot ? 'pr-12' : 'pr-3.5',
            error
              ? 'border-danger-600 focus:border-danger-600 focus:ring-danger-600/15'
              : 'border-line-300 focus:border-blue-600 focus:ring-blue-600/20',
            className,
          )}
          {...rest}
        />
        {rightSlot ? (
          <span className="absolute right-1.5 top-1/2 -translate-y-1/2">{rightSlot}</span>
        ) : null}
      </div>
      <FieldMessages hint={hint} error={error} hintId={hintId} errorId={errorId} />
    </div>
  )
})

export interface SelectOption {
  value: string
  label: string
  disabled?: boolean
}

export interface SelectProps
  extends SelectHTMLAttributes<HTMLSelectElement>,
    FieldChrome {
  options: SelectOption[]
  containerClassName?: string
  inputSize?: 'md' | 'lg'
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  {
    label,
    hideLabel = false,
    hint,
    error,
    options,
    id,
    className,
    containerClassName,
    inputSize = 'md',
    'aria-describedby': ariaDescribedBy,
    ...rest
  },
  ref,
) {
  const generatedId = useId()
  const selectId = id ?? `select-${generatedId}`
  const hintId = `${selectId}-hint`
  const errorId = `${selectId}-error`
  const describedBy =
    [ariaDescribedBy, hint && !error ? hintId : null, error ? errorId : null]
      .filter(Boolean)
      .join(' ') || undefined

  return (
    <div className={cn('flex flex-col gap-1.5', containerClassName)}>
      {label ? (
        <label
          htmlFor={selectId}
          className={cn('text-label text-ink-700', hideLabel && 'sr-only')}
        >
          {label}
        </label>
      ) : null}
      <div className="relative">
        <select
          ref={ref}
          id={selectId}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy}
          className={cn(
            'w-full appearance-none rounded-md border bg-surface pl-3.5 pr-10 text-body text-ink-900 transition-colors duration-1',
            'hover:border-ink-400 focus:border-blue-600 focus:outline-none focus-visible:outline-none focus:ring-[3px] focus:ring-blue-600/20',
            'disabled:cursor-not-allowed disabled:border-line-200 disabled:bg-monash-mist disabled:text-ink-400',
            inputSize === 'lg' ? 'h-12' : 'h-11',
            error ? 'border-danger-600' : 'border-line-300',
            className,
          )}
          {...rest}
        >
          {options.map((option) => (
            <option key={option.value} value={option.value} disabled={option.disabled}>
              {option.label}
            </option>
          ))}
        </select>
        <ChevronDown
          className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-500"
          aria-hidden="true"
        />
      </div>
      <FieldMessages hint={hint} error={error} hintId={hintId} errorId={errorId} />
    </div>
  )
})
