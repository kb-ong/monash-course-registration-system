import { AlertTriangle, Search, SearchX, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { getSemesters } from '../api/courses'
import CourseCard from '../components/CourseCard'
import RegisterDialog from '../components/RegisterDialog'
import StatusBanner from '../components/StatusBanner'
import Button from '../components/ui/Button'
import EmptyState from '../components/ui/EmptyState'
import { Input, Select } from '../components/ui/Input'
import PageHeader from '../components/ui/PageHeader'
import { CourseGridSkeleton } from '../components/ui/Skeleton'
import useCourses from '../hooks/useCourses'
import useDebouncedValue from '../hooks/useDebouncedValue'
import { cn } from '../lib/cn'
import { facultyShort, semesterName } from '../lib/format'
import { useRegistrationPeriod } from '../registration/RegistrationPeriodContext'
import { useRegistrations } from '../registration/RegistrationsContext'
import type { Course, Semester } from '../types/api'

const FACULTIES = [
  'All',
  'Information Technology',
  'Business',
  'Engineering',
  'Law',
  'Science',
]

export default function Catalog() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { data, isRegistered } = useRegistrations()
  const { period } = useRegistrationPeriod()

  const qParam = searchParams.get('q') ?? ''
  const semesterParam = searchParams.get('semester') ?? ''
  const [search, setSearch] = useState(qParam)
  const [faculty, setFaculty] = useState('All')
  const [semesters, setSemesters] = useState<Semester[]>([])
  const [semestersFailed, setSemestersFailed] = useState(false)
  const [selectedCourse, setSelectedCourse] = useState<Course | null>(null)
  const searchRef = useRef<HTMLInputElement>(null)

  // The registration term is authoritative from the period endpoint; the semester list is a
  // fallback while it loads. Never fall back to `semesters[0]` — that could be a prior term.
  const activeSemester =
    period?.semester ?? semesters.find((semester) => semester.active)?.code ?? ''
  // Only the active term can be the selected/registration semester, even via a deep link.
  const selectedSemester = activeSemester
  const debouncedSearch = useDebouncedValue(search, 300)

  const { courses, loading, refreshing, error, reload } = useCourses({
    semester: selectedSemester || undefined,
    query: debouncedSearch.trim() || undefined,
  })

  useEffect(() => {
    const controller = new AbortController()
    getSemesters(controller.signal)
      .then((result) => {
        if (controller.signal.aborted) return
        setSemesters(result)
        setSemestersFailed(false)
      })
      .catch(() => {
        if (controller.signal.aborted) return
        setSemestersFailed(true)
      })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    setSearch(qParam)
  }, [qParam])

  useEffect(() => {
    if (!activeSemester || semesterParam === activeSemester) return
    const next = new URLSearchParams(searchParams)
    next.set('semester', activeSemester)
    setSearchParams(next, { replace: true })
  }, [activeSemester, semesterParam, searchParams, setSearchParams])

  const updateSearch = (value: string) => {
    setSearch(value)
    const next = new URLSearchParams(searchParams)
    if (value) next.set('q', value)
    else next.delete('q')
    setSearchParams(next, { replace: true })
  }

  const updateSemester = (value: string) => {
    // Non-active terms are rendered as disabled options; ignore them defensively.
    if (activeSemester && value !== activeSemester) return
    const next = new URLSearchParams(searchParams)
    if (value) next.set('semester', value)
    else next.delete('semester')
    setSearchParams(next, { replace: true })
  }

  const clearSearch = () => {
    updateSearch('')
    searchRef.current?.focus()
  }

  const clearFilters = () => {
    setFaculty('All')
    clearSearch()
  }

  const visibleCourses =
    faculty === 'All' ? courses : courses.filter((course) => course.faculty === faculty)

  const totalCredits = data?.totalCredits ?? 0
  const periodOpen = period?.open ?? false
  const semesterOptions = (() => {
    const options = semesters.map((semester) => {
      const open = Boolean(activeSemester) && semester.code === activeSemester
      return {
        value: semester.code,
        label: open ? semester.name : `${semester.name} — Not open`,
        disabled: !open && Boolean(activeSemester),
      }
    })
    // Keep the active term selectable even if the semester list failed or omitted it.
    if (activeSemester && !options.some((option) => option.value === activeSemester)) {
      options.unshift({
        value: activeSemester,
        label: semesterName(activeSemester),
        disabled: false,
      })
    }
    return options.sort(
      (a, b) => Number(b.value === activeSemester) - Number(a.value === activeSemester),
    )
  })()
  const hasQuery = search.trim().length > 0
  const noResults = !loading && !error && visibleCourses.length === 0

  return (
    <>
      <div className="main-container pt-6">
        <StatusBanner />
      </div>

      <div className="main-container py-8 lg:py-12">
        <PageHeader
          eyebrow="Course catalogue"
          title="Catalogue"
          description="Find your next unit, check the seats and register while the window is open."
        />

        <div className="mt-8 grid gap-3 lg:grid-cols-[minmax(0,1fr)_260px]">
          <Input
            ref={searchRef}
            label="Search courses"
            hideLabel
            type="search"
            maxLength={100}
            placeholder="Try FIT2004 or Algorithms"
            hint="Matches course code or name"
            value={search}
            onChange={(event) => updateSearch(event.target.value)}
            leftIcon={<Search className="h-[18px] w-[18px]" />}
            rightSlot={
              search ? (
                <button
                  type="button"
                  onClick={clearSearch}
                  className="flex h-11 w-11 items-center justify-center rounded-md text-ink-400 transition-colors duration-1 hover:bg-monash-mist hover:text-ink-700"
                  aria-label="Clear search"
                >
                  <X className="h-4 w-4" aria-hidden="true" />
                </button>
              ) : null
            }
            autoComplete="off"
          />

          <Select
            label="Semester"
            hideLabel
            value={selectedSemester}
            onChange={(event) => updateSemester(event.target.value)}
            options={
              semesterOptions.length > 0
                ? semesterOptions
                : [{ value: selectedSemester, label: selectedSemester || 'Active semester' }]
            }
            disabled={semesterOptions.length === 0}
          />
        </div>

        <div className="mt-4 flex flex-wrap items-center gap-2">
          <span className="mr-1 text-body-sm text-ink-500">Faculty</span>
          {FACULTIES.map((option) => {
            const label = option === 'All' ? 'All' : facultyShort(option)
            const active = faculty === option
            return (
              <button
                key={option}
                type="button"
                onClick={() => setFaculty(option)}
                aria-pressed={active}
                className={cn(
                  'inline-flex min-h-[44px] items-center rounded-pill border px-3 text-body-sm font-medium transition-colors duration-1 sm:min-h-[36px]',
                  active
                    ? 'border-monash-navy bg-monash-navy text-white'
                    : 'border-line-300 bg-surface text-ink-700 hover:bg-monash-mist',
                )}
              >
                {label}
              </button>
            )
          })}
        </div>

        <div className="mt-6 flex flex-col gap-2 border-t border-line-200 pt-4 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-body-sm text-ink-500" aria-live="polite">
            {loading
              ? 'Loading courses…'
              : refreshing
                ? 'Updating results…'
                : `${visibleCourses.length} course${visibleCourses.length === 1 ? '' : 's'} found`}
          </p>
          <p className="text-body-sm text-ink-400">
            Sort: Code A→Z
            {semestersFailed ? ' · Semester list unavailable' : ''}
          </p>
        </div>

        <div className="mt-6">
          {loading ? (
            <CourseGridSkeleton count={6} />
          ) : error && courses.length === 0 ? (
            <div className="rounded-lg border border-danger-50 bg-danger-50/60 p-6">
              <div className="flex items-start gap-2.5">
                <AlertTriangle
                  className="mt-0.5 h-4 w-4 shrink-0 text-danger-600"
                  aria-hidden="true"
                />
                <div>
                  <p className="text-body font-semibold text-ink-900">
                    We couldn't load courses.
                  </p>
                  <p className="mt-1 text-body-sm text-ink-500">
                    Check your connection and try again.
                  </p>
                  <Button variant="secondary" size="sm" className="mt-3" onClick={() => reload()}>
                    Retry
                  </Button>
                </div>
              </div>
            </div>
          ) : noResults ? (
            <div className="rounded-lg border border-line-200 bg-surface shadow-e1">
              <EmptyState
                icon={<SearchX className="h-7 w-7" aria-hidden="true" />}
                title={
                  hasQuery
                    ? `No courses match "${search.trim()}".`
                    : faculty !== 'All'
                      ? 'No courses in this faculty.'
                      : `No courses in ${semesterName(selectedSemester)}.`
                }
                description={
                  hasQuery
                    ? 'Try a different code or clear the search.'
                    : faculty !== 'All'
                      ? 'Clear the faculty filter to see the whole catalogue.'
                      : `There are no courses in ${semesterName(selectedSemester)}.`
                }
                action={
                  hasQuery || faculty !== 'All' ? (
                    <Button variant="secondary" onClick={clearFilters}>
                      {hasQuery && faculty === 'All' ? 'Clear search' : 'Clear filters'}
                    </Button>
                  ) : undefined
                }
              />
            </div>
          ) : (
            <>
              {error ? (
                <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-md border border-danger-50 bg-danger-50/60 px-4 py-3">
                  <p className="text-body-sm text-danger-600">
                    We couldn't refresh courses. Showing the last results.
                  </p>
                  <Button variant="secondary" size="sm" onClick={() => reload()}>
                    Retry
                  </Button>
                </div>
              ) : null}

              <h2 className="sr-only">Courses</h2>
              <div
                className={cn(
                  'grid gap-4 transition-opacity duration-2 md:grid-cols-2 md:gap-6 xl:grid-cols-3',
                  refreshing && 'opacity-60',
                )}
                aria-busy={refreshing || undefined}
              >
                {visibleCourses.map((course, index) => (
                  <CourseCard
                    key={course.id}
                    course={course}
                    index={index}
                    registered={isRegistered(course.id)}
                    periodOpen={periodOpen}
                    totalCredits={totalCredits}
                    activeSemester={activeSemester}
                    onRegister={setSelectedCourse}
                  />
                ))}
              </div>
            </>
          )}
        </div>

        {!loading && !error && visibleCourses.length > 0 ? (
          <div className="mt-8 flex items-center justify-between border-t border-line-200 pt-4">
            <p className="text-body-sm text-ink-400">
              {visibleCourses.length} of {courses.length} shown
            </p>
            <p className="text-body-sm text-ink-400">{semesterName(selectedSemester)}</p>
          </div>
        ) : null}
      </div>

      <RegisterDialog
        course={selectedCourse}
        open={selectedCourse !== null}
        onClose={() => setSelectedCourse(null)}
        onSettled={() => reload()}
      />
    </>
  )
}
