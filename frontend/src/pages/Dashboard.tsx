import { motion, useReducedMotion } from 'framer-motion'
import { AlertTriangle, ArrowRight, CalendarDays, Clock, MapPin } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import CourseCard from '../components/CourseCard'
import RegisterDialog from '../components/RegisterDialog'
import StatusBanner from '../components/StatusBanner'
import Badge from '../components/ui/Badge'
import Button from '../components/ui/Button'
import ButtonLink from '../components/ui/ButtonLink'
import EmptyState from '../components/ui/EmptyState'
import SeatMeter from '../components/ui/SeatMeter'
import { CourseCardSkeleton, Skeleton } from '../components/ui/Skeleton'
import StatRing from '../components/ui/StatRing'
import useCourses from '../hooks/useCourses'
import {
  creditBadge,
  dayRank,
  dayShort,
  formatDate,
  formatDateTime,
  greetingWithName,
  semesterName,
  timeToMinutes,
} from '../lib/format'
import { useRegistrationPeriod } from '../registration/RegistrationPeriodContext'
import { useRegistrations } from '../registration/RegistrationsContext'
import type { Course, CourseSchedule, RegistrationItem } from '../types/api'

interface ClassEntry {
  course: Course
  slot: CourseSchedule
}

export default function Dashboard() {
  const { student } = useAuth()
  const { data, loading, error, refresh, isRegistered } = useRegistrations()
  const { period } = useRegistrationPeriod()
  const {
    courses,
    loading: coursesLoading,
    error: coursesError,
    reload: reloadCourses,
  } = useCourses()
  const reduceMotion = useReducedMotion()
  const [selectedCourse, setSelectedCourse] = useState<Course | null>(null)

  const items = data?.items ?? []
  const totalCredits = data?.totalCredits ?? 0
  const semester = period ? semesterName(period.semester) : 'Semester 2 2026'
  const periodOpen = period?.open ?? false

  const classEntries: ClassEntry[] = items
    .flatMap((item) => item.course.schedule.map((slot) => ({ course: item.course, slot })))
    .sort((a, b) => {
      const dayDifference = dayRank(a.slot.day) - dayRank(b.slot.day)
      if (dayDifference !== 0) return dayDifference
      return timeToMinutes(a.slot.startTime) - timeToMinutes(b.slot.startTime)
    })
    .slice(0, 4)

  const heroCopy =
    items.length === 0
      ? "You're not registered in anything yet."
      : `You're registered in ${items.length} course${items.length === 1 ? '' : 's'} and ${totalCredits} of 24 credit points.`

  return (
    <>
      <section className="hero-navy hero-grain on-navy relative overflow-hidden text-white">
        <div className="main-container flex min-h-[360px] flex-col justify-end gap-8 pb-16 pt-24 lg:min-h-[340px] lg:flex-row lg:items-end lg:justify-between lg:pb-16 lg:pt-[112px]">
          <motion.div
            className="max-w-[700px]"
            initial={reduceMotion ? { opacity: 0 } : { opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: reduceMotion ? 0 : 0.56, ease: [0.16, 1, 0.3, 1] }}
          >
            <p className="text-overline uppercase tracking-[0.08em] text-white/60">{semester}</p>
            <span className="gold-rule mt-4" aria-hidden="true" />
            <h1 className="mt-6 font-display text-[30px] font-semibold leading-[34px] tracking-[-0.02em] text-white sm:text-display-2 sm:leading-[48px]">
              {greetingWithName(period?.serverTime, student?.name)}
            </h1>
            <p className="mt-4 max-w-[56ch] text-body-lg text-white/70">{heroCopy}</p>

            <div className="mt-7 flex flex-wrap items-center gap-3">
              <ButtonLink to="/catalog" variant="gold" size="lg">
                Browse catalogue
                <ArrowRight className="h-4 w-4" aria-hidden="true" />
              </ButtonLink>
              <ButtonLink
                to="/schedule"
                variant="secondary"
                size="lg"
                className="border-white/25 bg-white/10 text-white hover:bg-white/15 active:bg-white/20"
              >
                View my schedule
              </ButtonLink>
            </div>
          </motion.div>

          <motion.div
            className="hidden shrink-0 rounded-xl p-6 glass-card md:block"
            initial={reduceMotion ? { opacity: 0 } : { opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{
              duration: reduceMotion ? 0 : 0.56,
              delay: reduceMotion ? 0 : 0.1,
              ease: [0.16, 1, 0.3, 1],
            }}
          >
            <StatRing value={totalCredits} loading={loading} onDark />
            <p className="mt-3 max-w-[180px] text-center text-body-sm text-white/60">
              {loading
                ? 'Loading your enrolment'
                : totalCredits >= 24
                  ? 'Credit limit reached'
                  : `${24 - totalCredits} credits remaining`}
            </p>
          </motion.div>
        </div>
      </section>

      <div className="main-container relative z-10 -mt-6">
        <StatusBanner />
      </div>

      <div className="main-container space-y-12 py-10 lg:space-y-16 lg:py-14">
        <div className="grid gap-8 lg:grid-cols-[1.6fr_1fr]">
          <section aria-labelledby="next-classes-heading">
            <div className="flex items-end justify-between gap-4">
              <div>
                <p className="text-overline uppercase tracking-[0.08em] text-blue-600">
                  This week
                </p>
                <h2 id="next-classes-heading" className="mt-1.5 font-display text-h2 text-ink-900">
                  Next classes
                </h2>
              </div>
              <Link
                to="/schedule"
                className="inline-flex items-center gap-1.5 text-body-sm font-semibold text-blue-600 hover:text-blue-500"
              >
                Full timetable
                <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
              </Link>
            </div>

            <div className="mt-5">
              {loading ? (
                <div className="grid gap-4 sm:grid-cols-2">
                  <Skeleton className="h-[116px] rounded-lg" />
                  <Skeleton className="h-[116px] rounded-lg" />
                </div>
              ) : error && !data ? (
                <ErrorPanel
                  title="We couldn't load your schedule."
                  onRetry={() => void refresh()}
                />
              ) : items.length === 0 ? (
                <div className="rounded-lg border border-line-200 bg-surface shadow-e1">
                  <EmptyState
                    icon={<CalendarDays className="h-7 w-7" aria-hidden="true" />}
                    title="You're not registered in anything yet."
                    description="Browse the catalogue to find your first course."
                    action={
                      <ButtonLink to="/catalog" variant="primary">
                        Browse catalogue
                      </ButtonLink>
                    }
                    className="py-10"
                  />
                </div>
              ) : classEntries.length === 0 ? (
                <p className="rounded-lg border border-line-200 bg-surface p-6 text-body text-ink-500 shadow-e1">
                  Your courses do not have published class times yet.
                </p>
              ) : (
                <div className="grid gap-4 sm:grid-cols-2">
                  {classEntries.map(({ course, slot }, index) => (
                    <motion.div
                      key={`${course.id}-${slot.day}-${slot.startTime}`}
                      initial={reduceMotion ? { opacity: 0 } : { opacity: 0, y: 8 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{
                        duration: reduceMotion ? 0 : 0.32,
                        delay: reduceMotion ? 0 : Math.min(index, 5) * 0.04,
                        ease: [0.16, 1, 0.3, 1],
                      }}
                    >
                      <Link
                        to={`/courses/${course.id}`}
                        className="group flex min-h-[116px] gap-4 rounded-lg border border-line-200 bg-surface p-5 shadow-e1 transition-shadow duration-2 hover:shadow-e2"
                      >
                        <div className="flex h-12 w-14 shrink-0 flex-col items-center justify-center rounded-md bg-monash-mist">
                          <span className="text-overline uppercase tracking-[0.08em] text-blue-600">
                            {dayShort(slot.day)}
                          </span>
                          <span className="tabular text-body-sm font-semibold text-ink-900">
                            {slot.startTime}
                          </span>
                        </div>
                        <div className="min-w-0 flex-1">
                          <p className="text-overline uppercase tracking-[0.08em] text-ink-400">
                            {course.code}
                          </p>
                          <p className="mt-0.5 line-clamp-2 text-body font-semibold text-ink-900">
                            {course.name}
                          </p>
                          <p className="mt-1 flex items-center gap-1.5 text-body-sm text-ink-500">
                            <Clock className="h-3.5 w-3.5" aria-hidden="true" />
                            <span className="tabular">
                              {slot.startTime}–{slot.endTime}
                            </span>
                          </p>
                          <p className="mt-0.5 flex items-center gap-1.5 truncate text-body-sm text-ink-400">
                            <MapPin className="h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                            <span className="truncate">{slot.location}</span>
                          </p>
                        </div>
                      </Link>
                    </motion.div>
                  ))}
                </div>
              )}
            </div>
          </section>

          <aside aria-labelledby="term-heading">
            <div className="flex items-end justify-between gap-4">
              <h2 id="term-heading" className="font-display text-h2 text-ink-900">
                Term at a glance
              </h2>
            </div>
            <div className="mt-5 rounded-lg border border-line-200 bg-surface p-6 shadow-e1">
              <dl className="divide-y divide-line-200">
                <div className="flex items-center justify-between gap-4 pb-3">
                  <dt className="text-body-sm text-ink-500">Semester</dt>
                  <dd className="text-body font-semibold text-ink-900">{semester}</dd>
                </div>
                <div className="flex items-center justify-between gap-4 py-3">
                  <dt className="text-body-sm text-ink-500">Registration opens</dt>
                  <dd className="tabular text-body-sm font-medium text-ink-900">
                    {period ? formatDate(period.startAt) : '—'}
                  </dd>
                </div>
                <div className="flex items-center justify-between gap-4 py-3">
                  <dt className="text-body-sm text-ink-500">Registration closes</dt>
                  <dd className="tabular text-body-sm font-medium text-ink-900">
                    {period ? formatDateTime(period.endAt) : '—'}
                  </dd>
                </div>
                <div className="flex items-center justify-between gap-4 py-3">
                  <dt className="text-body-sm text-ink-500">Credit limit</dt>
                  <dd className="tabular text-body font-semibold text-ink-900">24</dd>
                </div>
                <div className="flex items-center justify-between gap-4 pt-3">
                  <dt className="text-body-sm text-ink-500">Catalogue</dt>
                  <dd className="tabular text-body font-semibold text-ink-900">
                    {coursesLoading || coursesError ? '—' : `${courses.length} courses`}
                  </dd>
                </div>
              </dl>
              <div className="mt-5 border-t border-line-200 pt-4">
                <Link
                  to="/schedule"
                  className="inline-flex items-center gap-1.5 text-body-sm font-semibold text-blue-600 hover:text-blue-500"
                >
                  Manage my enrolment
                  <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
                </Link>
              </div>
            </div>
          </aside>
        </div>

        <section aria-labelledby="your-courses-heading">
          <div className="flex items-end justify-between gap-4">
            <div>
              <p className="text-overline uppercase tracking-[0.08em] text-blue-600">Enrolment</p>
              <h2 id="your-courses-heading" className="mt-1.5 font-display text-h2 text-ink-900">
                Your courses
              </h2>
            </div>
            <Link
              to="/schedule"
              className="inline-flex items-center gap-1.5 text-body-sm font-semibold text-blue-600 hover:text-blue-500"
            >
              My schedule
              <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
            </Link>
          </div>

          <div className="mt-5">
            {loading ? (
              <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                <Skeleton className="h-[76px] rounded-lg" />
                <Skeleton className="h-[76px] rounded-lg" />
                <Skeleton className="h-[76px] rounded-lg" />
              </div>
            ) : items.length === 0 ? (
              <p className="text-body text-ink-500">
                Nothing registered yet — the catalogue is the place to start.
              </p>
            ) : (
              <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                {items.slice(0, 3).map((item: RegistrationItem) => (
                  <Link
                    key={item.registrationId}
                    to={`/courses/${item.course.id}`}
                    className="flex min-w-0 items-center justify-between gap-4 rounded-lg border border-line-200 bg-surface px-4 py-3 shadow-e1 transition-shadow duration-2 hover:shadow-e2"
                  >
                    <div className="min-w-0">
                      <p className="text-overline uppercase tracking-[0.08em] text-blue-600">
                        {item.course.code}
                      </p>
                      <p className="truncate text-body-sm font-semibold text-ink-900">
                        {item.course.name}
                      </p>
                    </div>
                    <Badge variant="credit" showIcon={false}>
                      {creditBadge(item.course.creditHours)}
                    </Badge>
                  </Link>
                ))}
                {items.length > 3 ? (
                  <Link
                    to="/schedule"
                    className="flex min-h-[76px] items-center justify-center rounded-lg border border-dashed border-line-300 px-4 py-3 text-body-sm font-semibold text-blue-600 hover:border-blue-600 hover:text-blue-500"
                  >
                    +{items.length - 3} more in My schedule
                  </Link>
                ) : null}
              </div>
            )}
          </div>
        </section>

        <section aria-labelledby="featured-heading">
          <div className="flex items-end justify-between gap-4">
            <div>
              <p className="text-overline uppercase tracking-[0.08em] text-blue-600">Catalogue</p>
              <h2 id="featured-heading" className="mt-1.5 font-display text-h2 text-ink-900">
                Featured this semester
              </h2>
            </div>
            <Link
              to="/catalog"
              className="inline-flex items-center gap-1.5 text-body-sm font-semibold text-blue-600 hover:text-blue-500"
            >
              View all{courses.length > 0 ? ` ${courses.length}` : ''}
              <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
            </Link>
          </div>

          <div className="mt-5">
            {coursesLoading ? (
              <div className="grid gap-4 md:grid-cols-2 md:gap-6 xl:grid-cols-3">
                <CourseCardSkeleton />
                <CourseCardSkeleton />
                <CourseCardSkeleton />
              </div>
            ) : coursesError ? (
              <ErrorPanel
                title="We couldn't load courses."
                onRetry={() => reloadCourses()}
              />
            ) : (
              <div className="grid gap-4 md:grid-cols-2 md:gap-6 xl:grid-cols-3">
                {courses.slice(0, 3).map((course, index) => (
                  <CourseCard
                    key={course.id}
                    course={course}
                    index={index}
                    registered={isRegistered(course.id)}
                    periodOpen={periodOpen}
                    totalCredits={totalCredits}
                    activeSemester={period?.semester}
                    onRegister={setSelectedCourse}
                  />
                ))}
              </div>
            )}
          </div>
        </section>

        {items.length > 0 ? (
          <section aria-labelledby="seat-watch-heading" className="hidden lg:block">
            <h2 id="seat-watch-heading" className="font-display text-h2 text-ink-900">
              Seat watch
            </h2>
            <p className="mt-1 text-body-sm text-ink-500">
              Live availability in the courses you hold.
            </p>
            <div className="mt-5 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {items.slice(0, 3).map((item) => (
                <div
                  key={item.registrationId}
                  className="rounded-lg border border-line-200 bg-surface p-4 shadow-e1"
                >
                  <div className="flex items-center justify-between gap-3">
                    <p className="text-overline uppercase tracking-[0.08em] text-blue-600">
                      {item.course.code}
                    </p>
                    <Badge variant="registered">Registered</Badge>
                  </div>
                  <SeatMeter
                    availableSeats={item.course.availableSeats}
                    capacity={item.course.capacity}
                    className="mt-3"
                    compact
                  />
                </div>
              ))}
            </div>
          </section>
        ) : null}
      </div>

      <RegisterDialog
        course={selectedCourse}
        open={selectedCourse !== null}
        onClose={() => setSelectedCourse(null)}
        onSettled={() => reloadCourses()}
      />
    </>
  )
}

function ErrorPanel({ title, onRetry }: { title: string; onRetry: () => void }) {
  return (
    <div className="rounded-lg border border-danger-50 bg-danger-50/60 p-6">
      <div className="flex items-start gap-2.5">
        <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-danger-600" aria-hidden="true" />
        <div>
          <p className="text-body font-semibold text-ink-900">{title}</p>
          <p className="mt-1 text-body-sm text-ink-500">Try again in a moment.</p>
          <Button variant="secondary" size="sm" className="mt-3" onClick={onRetry}>
            Retry
          </Button>
        </div>
      </div>
    </div>
  )
}
