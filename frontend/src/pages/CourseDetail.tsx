import { AlertTriangle, ArrowLeft, CalendarDays, Clock, MapPin } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { getCourse } from '../api/courses'
import { normalizeApiError, type ApiError } from '../api/client'
import DropDialog from '../components/DropDialog'
import RegisterDialog from '../components/RegisterDialog'
import StatusBanner from '../components/StatusBanner'
import Badge from '../components/ui/Badge'
import Button from '../components/ui/Button'
import ButtonLink from '../components/ui/ButtonLink'
import SeatMeter from '../components/ui/SeatMeter'
import { Skeleton } from '../components/ui/Skeleton'
import { creditPoints, dayShort, formatDate, scheduleLine, semesterName } from '../lib/format'
import { registerActionState } from '../lib/registrationState'
import { useRegistrationPeriod } from '../registration/RegistrationPeriodContext'
import { useRegistrations } from '../registration/RegistrationsContext'
import type { Course, RegistrationItem } from '../types/api'

export default function CourseDetail() {
  const { id } = useParams<{ id: string }>()
  const { data, isRegistered, getRegistration } = useRegistrations()
  const { period } = useRegistrationPeriod()

  const [course, setCourse] = useState<Course | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<ApiError | null>(null)
  const [reloadToken, setReloadToken] = useState(0)
  const [registerOpen, setRegisterOpen] = useState(false)
  const [dropTarget, setDropTarget] = useState<RegistrationItem | null>(null)

  useEffect(() => {
    if (!id) {
      setLoading(false)
      setError(normalizeApiError(new Error('Missing course id')))
      return
    }
    const controller = new AbortController()
    setLoading(true)
    setError(null)
    getCourse(id, controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return
        setCourse(result)
      })
      .catch((caught) => {
        if (controller.signal.aborted) return
        setError(normalizeApiError(caught))
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [id, reloadToken])

  const reload = () => setReloadToken((token) => token + 1)

  if (loading) {
    return (
      <div className="main-container py-8 lg:py-12">
        <div className="space-y-6">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="h-3 w-64" />
          <Skeleton className="h-10 w-2/3 max-w-[560px]" />
          <Skeleton className="h-5 w-80" />
          <div className="grid gap-8 pt-6 lg:grid-cols-[minmax(0,1fr)_360px]">
            <div className="space-y-4">
              <Skeleton className="h-6 w-48" />
              <Skeleton className="h-24 w-full" />
              <Skeleton className="h-6 w-40" />
              <Skeleton className="h-32 w-full" />
            </div>
            <Skeleton className="h-[320px] w-full rounded-lg" />
          </div>
        </div>
      </div>
    )
  }

  if (error?.code === 'COURSE_NOT_FOUND') {
    return <CourseNotFound />
  }

  if (error) {
    return (
      <div className="main-container py-16">
        <div className="mx-auto max-w-[520px] rounded-lg border border-danger-50 bg-danger-50/60 p-6">
          <div className="flex items-start gap-2.5">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-danger-600" aria-hidden="true" />
            <div>
              <p className="text-body font-semibold text-ink-900">
                We couldn't load this course.
              </p>
              <p className="mt-1 text-body-sm text-ink-500">Try again in a moment.</p>
              <Button variant="secondary" size="sm" className="mt-3" onClick={reload}>
                Retry
              </Button>
            </div>
          </div>
        </div>
      </div>
    )
  }

  if (!course) {
    return <CourseNotFound />
  }

  const registered = isRegistered(course.id)
  const registration = getRegistration(course.id)
  const totalCredits = data?.totalCredits ?? 0
  const full = course.availableSeats <= 0
  const periodOpen = period?.open ?? false
  const activeSemester = period?.semester ?? ''

  const action = registerActionState({
    course,
    registered,
    periodOpen,
    totalCredits,
    activeSemester,
  })
  const semesterBlocked = action.kind === 'semester'
  const actionLabel = action.label
  const actionReason =
    action.kind === 'period' && period
      ? `Registration closed ${formatDate(period.endAt)}.`
      : action.reason
  const disabled = action.disabled

  return (
    <>
      <div className="main-container pt-6">
        <StatusBanner />
      </div>

      <div className="main-container py-8 lg:py-12">
        <Link
          to="/catalog"
          className="inline-flex items-center gap-1.5 text-body-sm font-semibold text-ink-500 hover:text-ink-900"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          Back to catalogue
        </Link>

        <div className="mt-6">
          <p className="text-overline uppercase tracking-[0.08em] text-blue-600">
            {course.code} · {semesterName(course.semester)} · {course.faculty.toUpperCase()}
          </p>
          <h1 className="mt-3 max-w-[24ch] font-display text-h1 text-ink-900">{course.name}</h1>
          <span className="gold-rule mt-4" aria-hidden="true" />
          <p className="mt-4 text-body-lg text-ink-700">
            {course.instructor} · {creditPoints(course.creditHours)}
          </p>
          <div className="mt-4 flex flex-wrap items-center gap-2">
            <Badge variant="faculty">{semesterName(course.semester)}</Badge>
            {semesterBlocked ? (
              <Badge variant="neutral" showIcon={false}>
                Not open
              </Badge>
            ) : null}
          </div>
        </div>

        <div className="mt-10 grid gap-8 lg:grid-cols-[minmax(0,1fr)_360px] lg:gap-12">
          <div className="min-w-0 space-y-10">
            <section aria-labelledby="about-heading">
              <h2 id="about-heading" className="font-display text-h2 text-ink-900">
                About this unit
              </h2>
              <p className="mt-3 max-w-[72ch] text-body-lg text-ink-700">{course.description}</p>
            </section>

            <section aria-labelledby="times-heading">
              <h2 id="times-heading" className="font-display text-h2 text-ink-900">
                Class times
              </h2>
              <div className="mt-4 overflow-hidden rounded-lg border border-line-200 bg-surface shadow-e1">
                {course.schedule.length === 0 ? (
                  <p className="p-6 text-body text-ink-500">Schedule to be advised.</p>
                ) : (
                  <ul className="divide-y divide-line-200">
                    {course.schedule.map((slot, index) => (
                      <li
                        key={`${slot.day}-${slot.startTime}-${index}`}
                        className="flex flex-col gap-2 px-5 py-4 sm:flex-row sm:items-center sm:justify-between"
                      >
                        <div className="flex items-center gap-4">
                          <span className="flex h-10 w-14 shrink-0 items-center justify-center rounded-md bg-monash-mist text-overline uppercase tracking-[0.08em] text-blue-600">
                            {dayShort(slot.day)}
                          </span>
                          <div>
                            <p className="tabular text-body font-semibold text-ink-900">
                              {slot.startTime}–{slot.endTime}
                            </p>
                            <p className="flex items-center gap-1.5 text-body-sm text-ink-500">
                              <MapPin className="h-3.5 w-3.5" aria-hidden="true" />
                              {slot.location}
                            </p>
                          </div>
                        </div>
                        <span className="flex items-center gap-1.5 text-body-sm text-ink-400 sm:text-right">
                          <Clock className="h-3.5 w-3.5" aria-hidden="true" />
                          {scheduleLine(slot)}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </section>

            <section aria-labelledby="assessment-heading">
              <h2 id="assessment-heading" className="font-display text-h2 text-ink-900">
                Assessment &amp; prerequisites
              </h2>
              <p className="mt-3 max-w-[68ch] text-body text-ink-700">
                None for Phase 1. This catalogue is open enrolment with no prerequisite checks and no
                timetable clash detection.
              </p>
            </section>
          </div>

          <aside aria-label="Registration">
            <div className="space-y-5 lg:sticky lg:top-24">
              <div className="rounded-lg border border-line-200 bg-surface p-6 shadow-e1 sm:p-8">
                <div className="flex items-baseline justify-between gap-3">
                  <p className="text-body font-semibold text-ink-900">
                    {course.availableSeats} of {course.capacity} seats available
                  </p>
                  {full ? <Badge variant="full">Full</Badge> : null}
                </div>
                <SeatMeter
                  availableSeats={course.availableSeats}
                  capacity={course.capacity}
                  className="mt-3"
                />

                <div className="mt-5 border-t border-line-200 pt-5">
                  {registered ? (
                    <div className="space-y-3">
                      <div className="flex items-center gap-2">
                        <Badge variant="registered">Registered</Badge>
                      </div>
                      <ButtonLink to="/schedule" variant="secondary" className="w-full">
                        View in schedule
                      </ButtonLink>
                      <Button
                        variant="danger-ghost"
                        className="w-full"
                        onClick={() => registration && setDropTarget(registration)}
                        disabled={!periodOpen || !registration}
                        title={!periodOpen ? 'Registration is closed' : undefined}
                      >
                        Drop course
                      </Button>
                      {!periodOpen ? (
                        <p className="text-body-sm text-ink-500">
                          Dropping is unavailable while registration is closed.
                        </p>
                      ) : null}
                    </div>
                  ) : (
                    <>
                      <Button
                        variant="primary"
                        size="lg"
                        className="w-full"
                        onClick={() => setRegisterOpen(true)}
                        disabled={disabled}
                        title={actionReason}
                      >
                        {actionLabel}
                      </Button>
                      {!semesterBlocked ? (
                        <p className="mt-3 text-body-sm text-ink-500">
                          Adding this brings you to {action.nextCredits} of 24 credits.
                        </p>
                      ) : null}
                      {semesterBlocked ? (
                        <p className="mt-3 text-body-sm text-ink-700">
                          {actionReason} Registration is open for {semesterName(activeSemester)} only.
                        </p>
                      ) : null}
                      {action.kind === 'full' ? (
                        <p className="mt-2 text-body-sm text-danger-600">
                          No seats available. Join the catalogue next semester — there is no waitlist.
                        </p>
                      ) : null}
                      {action.kind === 'period' ? (
                        <p className="mt-2 text-body-sm text-ink-500">{actionReason}</p>
                      ) : null}
                      {action.kind === 'credit' ? (
                        <p className="mt-2 text-body-sm text-warning-700">{actionReason}</p>
                      ) : null}
                    </>
                  )}
                </div>
              </div>

              <div className="rounded-lg border border-line-200 bg-surface p-6 shadow-e1 sm:p-8">
                <h2 className="font-display text-h3 text-ink-900">At a glance</h2>
                <span className="gold-rule mt-3" aria-hidden="true" />
                <dl className="mt-4 divide-y divide-line-200">
                  <div className="flex items-center justify-between gap-4 pb-2.5">
                    <dt className="text-body-sm text-ink-500">Code</dt>
                    <dd className="tabular text-body-sm font-semibold text-ink-900">{course.code}</dd>
                  </div>
                  <div className="flex items-center justify-between gap-4 py-2.5">
                    <dt className="text-body-sm text-ink-500">Credit</dt>
                    <dd className="tabular text-body-sm font-semibold text-ink-900">
                      {course.creditHours} CP
                    </dd>
                  </div>
                  <div className="flex items-center justify-between gap-4 py-2.5">
                    <dt className="text-body-sm text-ink-500">Faculty</dt>
                    <dd className="text-body-sm font-semibold text-ink-900">{course.faculty}</dd>
                  </div>
                  <div className="flex items-center justify-between gap-4 py-2.5">
                    <dt className="text-body-sm text-ink-500">Capacity</dt>
                    <dd className="tabular text-body-sm font-semibold text-ink-900">
                      {course.capacity}
                    </dd>
                  </div>
                  <div className="flex items-center justify-between gap-4 pt-2.5">
                    <dt className="text-body-sm text-ink-500">Semester</dt>
                    <dd className="text-body-sm font-semibold text-ink-900">
                      {semesterName(course.semester)}
                    </dd>
                  </div>
                </dl>
              </div>

              <div className="flex items-start gap-2.5 rounded-md bg-blue-50 px-4 py-3 text-body-sm text-monash-navy">
                <CalendarDays className="mt-0.5 h-4 w-4 shrink-0 text-blue-600" aria-hidden="true" />
                <p>
                  {period
                    ? semesterBlocked
                      ? `This unit runs in ${semesterName(course.semester)}. Registration is open for ${semesterName(activeSemester)} only.`
                      : period.open
                        ? `Registration is open until ${formatDate(period.endAt)}.`
                        : `Registration is closed. It opened ${formatDate(period.startAt)} and closed ${formatDate(period.endAt)}.`
                    : 'Registration window information is currently unavailable.'}
                </p>
              </div>
            </div>
          </aside>
        </div>
      </div>

      <RegisterDialog
        course={course}
        open={registerOpen}
        onClose={() => setRegisterOpen(false)}
        onSettled={reload}
      />
      <DropDialog
        registration={dropTarget}
        open={dropTarget !== null}
        onClose={() => setDropTarget(null)}
        onSettled={reload}
      />
    </>
  )
}

function CourseNotFound() {
  return (
    <div className="main-container flex min-h-[60vh] flex-col items-center justify-center py-16 text-center">
      <p className="font-display text-[64px] font-semibold leading-none text-monash-navy">404</p>
      <h1 className="mt-4 font-display text-h2 text-ink-900">We couldn't find that course.</h1>
      <p className="mt-2 max-w-[46ch] text-body text-ink-500">
        The course may have been removed, or the link may be out of date.
      </p>
      <ButtonLink to="/catalog" variant="primary" className="mt-6">
        Back to catalogue
      </ButtonLink>
    </div>
  )
}
