import { motion, useReducedMotion } from 'framer-motion'
import { ArrowRight, Clock, MapPin } from 'lucide-react'
import { Link } from 'react-router-dom'
import { cn } from '../lib/cn'
import { creditBadge, scheduleLine } from '../lib/format'
import { registerActionState } from '../lib/registrationState'
import type { Course } from '../types/api'
import Badge from './ui/Badge'
import Button from './ui/Button'
import SeatMeter from './ui/SeatMeter'

interface CourseCardProps {
  course: Course
  index?: number
  registered: boolean
  periodOpen: boolean
  totalCredits: number
  /** The semester registrations are accepted for; other terms are blocked. */
  activeSemester?: string
  onRegister: (course: Course) => void
}

export default function CourseCard({
  course,
  index = 0,
  registered,
  periodOpen,
  totalCredits,
  activeSemester,
  onRegister,
}: CourseCardProps) {
  const reduceMotion = useReducedMotion()
  const full = course.availableSeats <= 0
  const firstSlot = course.schedule[0]

  const action = registerActionState({
    course,
    registered,
    periodOpen,
    totalCredits,
    activeSemester,
  })
  const semesterBlocked = action.kind === 'semester'

  return (
    <motion.article
      initial={reduceMotion ? { opacity: 0 } : { opacity: 0, y: 10 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{
        duration: reduceMotion ? 0 : 0.32,
        delay: reduceMotion ? 0 : Math.min(index, 5) * 0.04,
        ease: [0.16, 1, 0.3, 1],
      }}
      whileHover={reduceMotion ? undefined : { y: -2 }}
      className={cn(
        'group flex min-h-[232px] flex-col gap-3 rounded-lg border bg-surface p-6 shadow-e1 transition-shadow duration-2 hover:shadow-e2',
        full ? 'border-danger-50' : 'border-line-200',
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-overline uppercase tracking-[0.08em] text-blue-600">{course.code}</p>
          <span
            className="mt-1 block h-[2px] w-10 origin-left scale-x-0 bg-monash-gold transition-transform duration-2 group-hover:scale-x-100"
            aria-hidden="true"
          />
        </div>
        <div className="flex shrink-0 flex-wrap justify-end gap-1.5">
          {full ? (
            <Badge variant="full">Full</Badge>
          ) : (
            <Badge variant="credit" showIcon={false}>
              {creditBadge(course.creditHours)}
            </Badge>
          )}
          {registered ? <Badge variant="registered">Registered</Badge> : null}
          {semesterBlocked ? (
            <Badge variant="neutral" showIcon={false}>
              Not open
            </Badge>
          ) : null}
        </div>
      </div>

      <h3 className="line-clamp-2 min-h-[52px] text-h4 text-ink-900">{course.name}</h3>

      <p className="truncate text-body-sm text-ink-500">
        {course.faculty} · {course.instructor}
      </p>

      <div className="rounded-md border border-line-200 bg-canvas px-3 py-2">
        {firstSlot ? (
          <>
            <div className="flex min-w-0 items-center gap-1.5 text-body-sm font-semibold text-ink-700">
              <Clock className="h-3.5 w-3.5 shrink-0 text-ink-500" aria-hidden="true" />
              <span className="tabular">{scheduleLine(firstSlot)}</span>
              {course.schedule.length > 1 ? (
                <span className="font-normal text-ink-400">+{course.schedule.length - 1} more</span>
              ) : null}
            </div>
            <div className="mt-0.5 flex min-w-0 items-center gap-1.5 text-body-sm text-ink-500">
              <MapPin className="h-3.5 w-3.5 shrink-0 text-ink-400" aria-hidden="true" />
              <span className="truncate">{firstSlot.location}</span>
            </div>
          </>
        ) : (
          <p className="text-body-sm text-ink-500">Schedule to be advised</p>
        )}
      </div>

      <SeatMeter availableSeats={course.availableSeats} capacity={course.capacity} className="mt-1" />

      <div className="mt-auto flex items-center justify-between gap-3 border-t border-line-200 pt-4">
        {registered ? (
          <Link
            to="/schedule"
            className="inline-flex min-h-[44px] items-center gap-2 text-body font-semibold text-blue-600 underline underline-offset-[3px] hover:text-blue-500 sm:min-h-0"
          >
            View in schedule
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Link>
        ) : (
          <Button
            variant="primary"
            size="md"
            onClick={() => onRegister(course)}
            disabled={action.disabled}
            title={action.reason}
            aria-label={action.reason ? `${action.label} — ${action.reason}` : action.label}
            className="min-w-[132px] whitespace-nowrap"
          >
            {action.label}
          </Button>
        )}

        <Link
          to={`/courses/${course.id}`}
          className="inline-flex min-h-[44px] items-center gap-1.5 text-body-sm font-semibold text-blue-600 hover:text-blue-500 sm:min-h-0"
        >
          Details
          <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
        </Link>
      </div>
    </motion.article>
  )
}
