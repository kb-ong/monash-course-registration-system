import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import { AlertTriangle, Check, Info, X } from 'lucide-react'
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { cn } from '../../lib/cn'

export type ToastVariant = 'success' | 'error' | 'info'

interface ToastOptions {
  title: string
  description?: string
  variant?: ToastVariant
  duration?: number
}

interface ToastRecord {
  id: number
  title: string
  description?: string
  variant: ToastVariant
  duration: number
}

interface ToastContextValue {
  pushToast: (options: ToastOptions) => void
  dismiss: (id: number) => void
}

const ToastContext = createContext<ToastContextValue | null>(null)

const variantStyles: Record<
  ToastVariant,
  { accent: string; iconWrap: string; icon: ReactNode }
> = {
  success: {
    accent: 'bg-success-600',
    iconWrap: 'bg-success-50 text-success-600',
    icon: <Check className="h-4 w-4" aria-hidden="true" />,
  },
  error: {
    accent: 'bg-danger-600',
    iconWrap: 'bg-danger-50 text-danger-600',
    icon: <AlertTriangle className="h-4 w-4" aria-hidden="true" />,
  },
  info: {
    accent: 'bg-blue-600',
    iconWrap: 'bg-blue-50 text-blue-600',
    icon: <Info className="h-4 w-4" aria-hidden="true" />,
  },
}

function ToastItem({
  toast,
  dismiss,
}: {
  toast: ToastRecord
  dismiss: (id: number) => void
}) {
  const reduceMotion = useReducedMotion()
  const [paused, setPaused] = useState(false)
  const remainingRef = useRef(toast.duration)
  const startRef = useRef(0)
  const styles = variantStyles[toast.variant]

  useEffect(() => {
    if (paused) return
    startRef.current = Date.now()
    const timer = window.setTimeout(() => dismiss(toast.id), remainingRef.current)
    return () => {
      window.clearTimeout(timer)
      remainingRef.current = Math.max(0, remainingRef.current - (Date.now() - startRef.current))
    }
  }, [paused, dismiss, toast.id])

  return (
    <motion.div
      layout
      initial={reduceMotion ? { opacity: 0 } : { opacity: 0, y: 8 }}
      animate={{ opacity: 1, y: 0 }}
      exit={reduceMotion ? { opacity: 0 } : { opacity: 0, y: 8 }}
      transition={{ duration: reduceMotion ? 0 : 0.2, ease: [0.2, 0.8, 0.2, 1] }}
      role={toast.variant === 'error' ? 'alert' : 'status'}
      aria-live={toast.variant === 'error' ? 'assertive' : 'polite'}
      className="pointer-events-auto relative flex w-full gap-3 overflow-hidden rounded-toast bg-surface p-4 pl-5 shadow-e3 sm:w-[360px]"
      onMouseEnter={() => setPaused(true)}
      onMouseLeave={() => setPaused(false)}
      onFocusCapture={() => setPaused(true)}
      onBlurCapture={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setPaused(false)
      }}
    >
      <span className={cn('absolute inset-y-0 left-0 w-[3px]', styles.accent)} aria-hidden="true" />
      <span
        className={cn(
          'mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full',
          styles.iconWrap,
        )}
        aria-hidden="true"
      >
        {styles.icon}
      </span>
      <div className="min-w-0 flex-1 pr-1">
        <p className="text-body font-semibold text-ink-900">{toast.title}</p>
        {toast.description ? (
          <p className="mt-1 text-body-sm text-ink-500">{toast.description}</p>
        ) : null}
      </div>
      <button
        type="button"
        onClick={() => dismiss(toast.id)}
        className="-mr-1 -mt-1 flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-ink-400 transition-colors duration-1 hover:bg-monash-mist hover:text-ink-700"
        aria-label="Dismiss notification"
      >
        <X className="h-4 w-4" aria-hidden="true" />
      </button>
    </motion.div>
  )
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastRecord[]>([])
  const idRef = useRef(0)

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id))
  }, [])

  const pushToast = useCallback((options: ToastOptions) => {
    idRef.current += 1
    const variant = options.variant ?? 'info'
    const record: ToastRecord = {
      id: idRef.current,
      title: options.title,
      description: options.description,
      variant,
      duration: options.duration ?? (variant === 'error' ? 8000 : 5000),
    }
    setToasts((current) => [...current.slice(-2), record])
  }, [])

  const value = useMemo<ToastContextValue>(() => ({ pushToast, dismiss }), [pushToast, dismiss])

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        className="pointer-events-none fixed inset-x-3 top-3 z-50 flex flex-col gap-3 sm:inset-x-auto sm:bottom-6 sm:right-6 sm:top-auto sm:w-[360px]"
        aria-label="Notifications"
      >
        <AnimatePresence initial={false}>
          {toasts.map((toast) => (
            <ToastItem key={toast.id} toast={toast} dismiss={dismiss} />
          ))}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  )
}

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext)
  if (!context) throw new Error('useToast must be used inside ToastProvider')
  return context
}
