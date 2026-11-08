import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import {
  AlertTriangle,
  CalendarX,
  Download,
  MapPin,
  Printer,
  TriangleAlert,
} from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { exportRegistrationsCsv } from '../api/registrations'
import { normalizeApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import DropDialog from '../components/DropDialog'
import StatusBanner from '../components/StatusBanner'
import Badge from '../components/ui/Badge'
import Button from '../components/ui/Button'
import ButtonLink from '../components/ui/ButtonLink'
import EmptyState from '../components/ui/EmptyState'
import PageHeader from '../components/ui/PageHeader'
import SeatMeter from '../components/ui/SeatMeter'
import { Skeleton } from '../components/ui/Skeleton'
import StatRing from '../components/ui/StatRing'
import { useToast } from '../components/ui/Toast'
import {
  DAY_ORDER,
  clamp,
  creditBadge,
  creditPoints,
  dayShort,
  formatPrintedDate,
  scheduleLine,
  semesterName,
  timeRange,
  timeToMinutes,
} from '../lib/format'
import { useRegistrationPeriod } from '../registration/RegistrationPeriodContext'
import { useRegistrations } from '../registration/RegistrationsContext'
import type { CourseSchedule, DayCode, RegistrationItem } from '../types/api'

const START_MINUTES = 8 * 60
const END_MINUTES = 20 * 60
const ROW_HEIGHT = 28
const STEP_MINUTES = 30
const TOTAL_HEIGHT = ((END_MINUTES - START_MINUTES) / STEP_MINUTES) * ROW_HEIGHT

interface PositionedBlock {
  item: RegistrationItem
  slot: CourseSchedule
  top: number
  height: number
  leftPercent: number
  widthPercent: number
  clash: boolean
  clamped: boolean
}

function buildDayBlocks(items: RegistrationItem[], day: DayCode): PositionedBlock[] {
  const entries: Array<{ item: RegistrationItem; slot: CourseSchedule; start: number; end: number }> = []

  for (const item of items) {
    for (const slot of item.course.schedule) {
      if (slot.day !== day) continue
      const rawStart = timeToMinutes(slot.startTime)
      const rawEnd = timeToMinutes(slot.endTime)
      if (rawEnd <= START_MINUTES || rawStart >= END_MINUTES) continue
      entries.push({
        item,
        slot,
        start: clamp(rawStart, START_MINUTES, END_MINUTES),
        end: clamp(rawEnd, START_MINUTES, END_MINUTES),
      })
    }
  }

  entries.sort((a, b) => a.start - b.start || a.end - b.end)

  const laneEnds: number[] = []
  const assignments = entries.map((entry) => {
    let lane = laneEnds.findIndex((laneEnd) => laneEnd <= entry.start)
    if (lane === -1) {
      lane = laneEnds.length
      laneEnds.push(entry.end)
    } else {
      laneEnds[lane] = entry.end
    }
    return { ...entry, lane }
  })

  const laneCount = Math.max(1, laneEnds.length)

  return assignments.map((assignment) => {
    const clash = assignments.some(
      (other) =>
        other !== assignment &&
        other.start < assignment.end &&
        other.end > assignment.start,
    )
    const widthPercent = clash ? 100 / laneCount : 100
    const leftPercent = clash ? assignment.lane * widthPercent : 0
    const top = ((assignment.start - START_MINUTES) / STEP_MINUTES) * ROW_HEIGHT
    const height = Math.max(
      24,
      ((assignment.end - assignment.start) / STEP_MINUTES) * ROW_HEIGHT - 4,
    )
    return {
      item: assignment.item,
      slot: assignment.slot,
      top,
      height,
      leftPercent,
      widthPercent,
      clash,
      clamped:
        timeToMinutes(assignment.slot.startTime) < START_MINUTES ||
        timeToMinutes(assignment.slot.endTime) > END_MINUTES,
    }
  })
}

export default function Schedule() {
  const { student } = useAuth()
  const { data, loading, error, refresh } = useRegistrations()
  const { period } = useRegistrationPeriod()
  const { pushToast } = useToast()
  const reduceMotion = useReducedMotion()
  const [tab, setTab] = useState<'timetable' | 'courses'>('timetable')
  const [exporting, setExporting] = useState(false)
  const [dropTarget, setDropTarget] = useState<RegistrationItem | null>(null)

  const items = data?.items ?? []
  const totalCredits = data?.totalCredits ?? 0
  const maxCredits = data?.maxCredits ?? 24
  const semester = data?.semester ?? period?.semester
  const periodOpen = period?.open ?? false
  const actionsDisabled = loading || Boolean(error) || items.length === 0

  const days = DAY_ORDER.filter(
    (day, index) =>
      index < 5 ||
      items.some((item) => item.course.schedule.some((slot) => slot.day === day)),
  )

  const blocksByDay = days.map((day) => buildDayBlocks(items, day))
  const hasClash = blocksByDay.some((blocks) => blocks.some((block) => block.clash))
  const hasClamped = blocksByDay.some((blocks) => blocks.some((block) => block.clamped))

  const printRows = items
    .flatMap((item) =>
      item.course.schedule.map((slot) => ({ item, slot })),
    )
    .sort((a, b) => {
      const code = a.item.course.code.localeCompare(b.item.course.code)
      if (code !== 0) return code
      const day = DAY_ORDER.indexOf(a.slot.day) - DAY_ORDER.indexOf(b.slot.day)
      if (day !== 0) return day
      return timeToMinutes(a.slot.startTime) - timeToMinutes(b.slot.startTime)
    })

  const handleExport = async () => {
    setExporting(true)
    try {
      const blob = await exportRegistrationsCsv()
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = 'course-list.csv'
      document.body.appendChild(anchor)
      anchor.click()
      anchor.remove()
      URL.revokeObjectURL(url)
    } catch (caught) {
      const apiError = normalizeApiError(caught)
      pushToast({
        variant: 'error',
        title: 'Export failed.',
        description: apiError.isNetwork ? apiError.message : 'Try again in a moment.',
      })
    } finally {
      setExporting(false)
    }
  }

  const handlePrint = () => {
    window.print()
  }

  return (
    <>
      <div className="main-container pt-6 no-print">
        <StatusBanner />
      </div>

      <div className="main-container py-8 lg:py-12">
        <div className="no-print">
          <PageHeader
            eyebrow="Enrolment"
            title="My schedule"
            description={`${semesterName(semester)} · ${totalCredits} of ${maxCredits} credit points`}
            actions={
              <div className="flex flex-wrap items-end gap-5">
                <StatRing
                  value={totalCredits}
                  max={maxCredits}
                  loading={loading}
                  caption={
                    totalCredits >= maxCredits
                      ? 'Credit limit reached'
                      : totalCredits >= 18
                        ? `${maxCredits - totalCredits} credits left`
                        : 'credit points'
                  }
                />
                <div className="flex flex-wrap gap-3 pb-1">
                  <Button
                    variant="secondary"
                    onClick={() => void handleExport()}
                    loading={exporting}
                    disabled={actionsDisabled || exporting}
                    title={items.length === 0 ? 'Nothing to export yet' : undefined}
                  >
                    <Download className="h-4 w-4" aria-hidden="true" />
                    Export CSV
                  </Button>
                  <Button
                    variant="secondary"
                    onClick={handlePrint}
                    disabled={actionsDisabled}
                    title={items.length === 0 ? 'Nothing to print yet' : undefined}
                  >
                    <Printer className="h-4 w-4" aria-hidden="true" />
                    Print
                  </Button>
                </div>
              </div>
            }
          />
        </div>

        <div className="mt-7 no-print">
          {loading ? (
            <div className="grid gap-6 lg:grid-cols-[1.6fr_1fr]">
              <Skeleton className="h-[520px] rounded-lg" />
              <div className="space-y-3">
                <Skeleton className="h-[140px] rounded-lg" />
                <Skeleton className="h-[140px] rounded-lg" />
                <Skeleton className="h-[140px] rounded-lg" />
              </div>
            </div>
          ) : error && !data ? (
            <div className="rounded-lg border border-danger-50 bg-danger-50/60 p-6">
              <div className="flex items-start gap-2.5">
                <AlertTriangle
                  className="mt-0.5 h-4 w-4 shrink-0 text-danger-600"
                  aria-hidden="true"
                />
                <div>
                  <p className="text-body font-semibold text-ink-900">
                    We couldn't load your schedule.
                  </p>
                  <p className="mt-1 text-body-sm text-ink-500">Try again in a moment.</p>
                  <Button
                    variant="secondary"
                    size="sm"
                    className="mt-3"
                    onClick={() => void refresh()}
                  >
                    Retry
                  </Button>
                </div>
              </div>
            </div>
          ) : items.length === 0 ? (
            <div className="rounded-lg border border-line-200 bg-surface shadow-e1">
              <EmptyState
                icon={<CalendarX className="h-7 w-7" aria-hidden="true" />}
                title="You're not registered in anything yet."
                description="Browse the catalogue to find your first course."
                action={
                  <ButtonLink to="/catalog" variant="primary">
                    Browse catalogue
                  </ButtonLink>
                }
              />
            </div>
          ) : (
            <>
              <div className="mb-5 flex gap-1 rounded-md border border-line-200 bg-surface p-1 lg:hidden">
                <button
                  type="button"
                  onClick={() => setTab('timetable')}
                  aria-pressed={tab === 'timetable'}
                  className={`min-h-[44px] flex-1 rounded-sm text-body-sm font-semibold transition-colors duration-1 ${
                    tab === 'timetable'
                      ? 'bg-monash-navy text-white'
                      : 'text-ink-500 hover:bg-monash-mist'
                  }`}
                >
                  Timetable
                </button>
                <button
                  type="button"
                  onClick={() => setTab('courses')}
                  aria-pressed={tab === 'courses'}
                  className={`min-h-[44px] flex-1 rounded-sm text-body-sm font-semibold transition-colors duration-1 ${
                    tab === 'courses'
                      ? 'bg-monash-navy text-white'
                      : 'text-ink-500 hover:bg-monash-mist'
                  }`}
                >
                  Courses
                </button>
              </div>

              {error ? (
                <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-md border border-danger-50 bg-danger-50/60 px-4 py-3">
                  <p className="text-body-sm text-danger-600">
                    We couldn't refresh your schedule. Showing the last results.
                  </p>
                  <Button variant="secondary" size="sm" onClick={() => void refresh()}>
                    Retry
                  </Button>
                </div>
              ) : null}

              <div className="grid min-w-0 gap-6 lg:grid-cols-[1.6fr_1fr]">
                <section
                  aria-labelledby="timetable-heading"
                  className={`min-w-0 ${tab === 'timetable' ? 'block' : 'hidden lg:block'}`}
                >
                  <div className="overflow-hidden rounded-lg border border-line-200 bg-surface shadow-e1">
                    <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line-200 px-5 py-4">
                      <h2 id="timetable-heading" className="font-display text-h3 text-ink-900">
                        Timetable
                      </h2>
                      <p className="text-body-sm text-ink-400">Week view · 08:00–20:00</p>
                    </div>

                    <div className="overflow-x-auto p-4 scroll-area">
                      <div className="min-w-[640px]">
                        <div
                          className="grid"
                          style={{
                            gridTemplateColumns: `48px repeat(${days.length}, minmax(0, 1fr))`,
                          }}
                        >
                          <div />
                          {days.map((day) => (
                            <div
                              key={day}
                              className="border-l border-line-200 pb-2 text-center text-overline uppercase tracking-[0.08em] text-ink-500"
                            >
                              {dayShort(day)}
                            </div>
                          ))}
                        </div>

                        <div className="flex">
                          <div className="relative w-12 shrink-0" style={{ height: TOTAL_HEIGHT }}>
                            {Array.from({ length: 12 }).map((_, index) => {
                              const hour = 8 + index
                              const top = (hour * 60 - START_MINUTES) / STEP_MINUTES * ROW_HEIGHT
                              return (
                                <span
                                  key={hour}
                                  className="tabular absolute right-2 -translate-y-1/2 text-[11px] text-ink-400"
                                  style={{ top }}
                                >
                                  {String(hour).padStart(2, '0')}:00
                                </span>
                              )
                            })}
                          </div>

                          <div
                            className="grid flex-1 border-l border-line-200"
                            style={{
                              gridTemplateColumns: `repeat(${days.length}, minmax(0, 1fr))`,
                              height: TOTAL_HEIGHT,
                            }}
                          >
                            {days.map((day, dayIndex) => (
                              <div
                                key={day}
                                className="relative border-r border-line-200 last:border-r-0"
                                style={{
                                  height: TOTAL_HEIGHT,
                                  backgroundImage:
                                    'repeating-linear-gradient(to bottom, transparent 0, transparent 27px, var(--line-200) 27px, var(--line-200) 28px)',
                                }}
                              >
                                {blocksByDay[dayIndex].map((block, blockIndex) => {
                                  const height = block.height
                                  return (
                                    <Link
                                      key={`${block.item.registrationId}-${block.slot.day}-${block.slot.startTime}-${blockIndex}`}
                                      to={`/courses/${block.item.course.id}`}
                                      style={{
                                        top: block.top,
                                        height,
                                        left: `calc(${block.leftPercent}% + 2px)`,
                                        width: `calc(${block.widthPercent}% - 4px)`,
                                      }}
                                      className={`absolute overflow-hidden rounded-block border-l-2 border-monash-gold bg-monash-navy p-1.5 text-white shadow-e1 transition-colors duration-1 hover:bg-navy-700 ${
                                        block.clash ? 'ring-1 ring-warning-700/60' : ''
                                      }`}
                                      aria-label={`${block.item.course.code} ${block.slot.day} ${timeRange(
                                        block.slot.startTime,
                                        block.slot.endTime,
                                      )}${block.clamped ? ' (clamped)' : ''} at ${block.slot.location}`}
                                    >
                                      <span className="block text-[11px] font-semibold uppercase leading-3 tracking-[0.08em]">
                                        {block.item.course.code}
                                      </span>
                                      {height >= 44 ? (
                                        <span className="tabular mt-0.5 block text-[11px] leading-3 text-white/80">
                                          {timeRange(block.slot.startTime, block.slot.endTime)}
                                          {block.clamped ? ' †' : ''}
                                        </span>
                                      ) : null}
                                      {height >= 70 ? (
                                        <span className="mt-1 flex items-center gap-1 truncate text-[11px] leading-3 text-white/60">
                                          <MapPin className="h-2.5 w-2.5 shrink-0" aria-hidden="true" />
                                          <span className="truncate">{block.slot.location}</span>
                                        </span>
                                      ) : null}
                                      {block.clash && height >= 86 ? (
                                        <span className="mt-1 inline-block rounded-xs bg-warning-50 px-1 text-[11px] font-semibold uppercase tracking-[0.06em] text-warning-700">
                                          Clash
                                        </span>
                                      ) : null}
                                    </Link>
                                  )
                                })}
                              </div>
                            ))}
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  {hasClash || hasClamped ? (
                    <div className="mt-3 flex flex-col gap-1 text-body-sm text-ink-500">
                      {hasClash ? (
                        <p className="flex items-start gap-1.5">
                          <TriangleAlert
                            className="mt-0.5 h-3.5 w-3.5 shrink-0 text-warning-700"
                            aria-hidden="true"
                          />
                          Overlapping classes are shown side by side. Clashes are not blocked in
                          Phase 1.
                        </p>
                      ) : null}
                      {hasClamped ? (
                        <p>
                          † Classes outside 08:00–20:00 are clamped in this view. The full times are
                          shown in Your courses.
                        </p>
                      ) : null}
                    </div>
                  ) : null}
                </section>

                <section
                  aria-labelledby="courses-heading"
                  className={`min-w-0 ${tab === 'courses' ? 'block' : 'hidden lg:block'}`}
                >
                  <div className="flex items-baseline justify-between gap-3">
                    <h2 id="courses-heading" className="font-display text-h3 text-ink-900">
                      Your courses ({items.length})
                    </h2>
                    <span className="tabular text-body-sm text-ink-400">
                      {totalCredits} / {maxCredits} CP
                    </span>
                  </div>

                  <div className="mt-4 space-y-3">
                    <AnimatePresence initial={false}>
                      {items.map((item) => (
                        <motion.div
                          key={item.registrationId}
                          layout={!reduceMotion}
                          initial={reduceMotion ? { opacity: 0 } : { opacity: 0, y: 8 }}
                          animate={{ opacity: 1, y: 0 }}
                          exit={reduceMotion ? { opacity: 0 } : { opacity: 0, y: -6 }}
                          transition={{ duration: reduceMotion ? 0 : 0.2 }}
                          className="rounded-lg border border-line-200 bg-surface p-5 shadow-e1"
                        >
                          <div className="flex items-start justify-between gap-3">
                            <div className="min-w-0">
                              <Link
                                to={`/courses/${item.course.id}`}
                                className="text-overline uppercase tracking-[0.08em] text-blue-600 hover:text-blue-500"
                              >
                                {item.course.code}
                              </Link>
                              <Link
                                to={`/courses/${item.course.id}`}
                                className="mt-0.5 block text-body font-semibold leading-6 text-ink-900 hover:text-blue-600"
                              >
                                {item.course.name}
                              </Link>
                            </div>
                            <Badge variant="credit" showIcon={false}>
                              {creditBadge(item.course.creditHours)}
                            </Badge>
                          </div>

                          <ul className="mt-2.5 space-y-0.5">
                            {item.course.schedule.map((slot, index) => (
                              <li
                                key={`${slot.day}-${slot.startTime}-${index}`}
                                className="truncate text-body-sm text-ink-500"
                              >
                                <span className="tabular">{scheduleLine(slot)}</span>
                                <span className="text-ink-400"> · {slot.location}</span>
                              </li>
                            ))}
                          </ul>

                          <SeatMeter
                            availableSeats={item.course.availableSeats}
                            capacity={item.course.capacity}
                            className="mt-3"
                            compact
                          />

                          <div className="mt-3 flex items-center justify-between gap-3 border-t border-line-200 pt-3">
                            <p className="text-body-sm text-ink-400">
                              {creditPoints(item.course.creditHours)}
                            </p>
                            <Button
                              variant="danger-ghost"
                              size="sm"
                              onClick={() => setDropTarget(item)}
                              disabled={!periodOpen}
                              title={!periodOpen ? 'Registration is closed' : undefined}
                            >
                              Drop
                            </Button>
                          </div>
                        </motion.div>
                      ))}
                    </AnimatePresence>
                  </div>

                  <div className="mt-4 rounded-md bg-blue-50 px-4 py-3 text-body-sm text-monash-navy">
                    <p>
                      {period?.open
                        ? 'Registration is open. Dropping a course releases the seat immediately.'
                        : 'Registration is closed. Your enrolment stays visible, but no changes can be made.'}
                    </p>
                  </div>
                </section>
              </div>
            </>
          )}
        </div>

        {!loading && !error && items.length === 0 ? (
          <div className="mt-6 no-print">
            <p className="text-body-sm text-ink-400">
              Export and print become available once you register for a course.
            </p>
          </div>
        ) : null}
      </div>

      {/* Print-only document (FR5) */}
      <div className="print-only">
        <div className="print-header">
          <div className="print-title">Monash University · Course timetable</div>
          <p className="print-meta">
            {student?.name} ({student?.studentId}) · {semesterName(semester)} · {totalCredits} of{' '}
            {maxCredits} credit points
          </p>
          <p className="print-meta">Printed {formatPrintedDate(new Date())}</p>
        </div>

        <div className="print-subtitle">Weekly timetable</div>
        <table className="timetable-grid">
          <thead>
            <tr>
              <th>Day</th>
              <th>Classes</th>
            </tr>
          </thead>
          <tbody>
            {days.map((day) => (
              <tr key={day}>
                <td>{dayShort(day)}</td>
                <td>
                  {items
                    .flatMap((item) =>
                      item.course.schedule
                        .filter((slot) => slot.day === day)
                        .map((slot) => ({ item, slot })),
                    )
                    .sort(
                      (a, b) =>
                        timeToMinutes(a.slot.startTime) - timeToMinutes(b.slot.startTime),
                    )
                    .map(({ item, slot }, index) => (
                      <span key={`${item.registrationId}-${slot.startTime}-${index}`}>
                        {index > 0 ? <br /> : null}
                        {item.course.code} · {slot.startTime}-{slot.endTime} ·{' '}
                        {slot.location}
                      </span>
                    ))}
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <div className="print-subtitle">Course list</div>
        <table className="course-table">
          <thead>
            <tr>
              <th>Course Code</th>
              <th>Course Name</th>
              <th>Credit Hours</th>
              <th>Instructor</th>
              <th>Day</th>
              <th>Time</th>
              <th>Location</th>
            </tr>
          </thead>
          <tbody>
            {printRows.map(({ item, slot }, index) => (
              <tr key={`${item.registrationId}-${slot.day}-${slot.startTime}-${index}`}>
                <td>{item.course.code}</td>
                <td>{item.course.name}</td>
                <td>{item.course.creditHours}</td>
                <td>{item.course.instructor}</td>
                <td>{slot.day}</td>
                <td>
                  {slot.startTime}-{slot.endTime}
                </td>
                <td>{slot.location}</td>
              </tr>
            ))}
          </tbody>
        </table>

        <div className="print-footer">
          <span>Generated from the Monash Course Registration System</span>
          <span>Page 1</span>
        </div>
      </div>

      <DropDialog
        registration={dropTarget}
        open={dropTarget !== null}
        onClose={() => setDropTarget(null)}
        onSettled={() => void refresh()}
      />
    </>
  )
}
