import { Link } from 'react-router-dom'
import useCourses from '../hooks/useCourses'
import { semesterName } from '../lib/format'
import { useRegistrationPeriod } from '../registration/RegistrationPeriodContext'

export default function Footer() {
  const { period } = useRegistrationPeriod()
  const { courses, loading, error } = useCourses({ semester: period?.semester })

  const semester = period ? semesterName(period.semester) : 'Monash University'
  const courseCount =
    !loading && !error && courses.length > 0 ? `${courses.length} courses` : null

  return (
    <footer className="on-navy border-t-2 border-monash-gold bg-monash-navy text-white">
      <div className="main-container grid gap-10 py-12 md:grid-cols-3">
        <div>
          <div className="flex items-center gap-3">
            <span className="text-[14px] font-bold tracking-[0.18em]">MONASH</span>
            <span className="h-5 w-px bg-white/20" aria-hidden="true" />
            <span className="text-body-sm text-white/60">Course Registration</span>
          </div>
          <p className="mt-4 max-w-[34ch] text-body-sm leading-6 text-footer-muted">
            A calm, single-semester registration workspace for Monash students. Browse, secure a
            seat and keep your timetable in one place.
          </p>
        </div>

        <div className="md:justify-self-center">
          <p className="text-overline uppercase tracking-[0.08em] text-white/50">Semester</p>
          <p className="mt-3 text-body text-white">
            {semester}
            {courseCount ? <span className="text-white/60"> · {courseCount}</span> : null}
          </p>
          <p className="mt-2 text-body-sm text-footer-muted">Phase 1 · Open enrolment</p>
        </div>

        <div className="md:justify-self-end">
          <p className="text-overline uppercase tracking-[0.08em] text-white/50">Monash University</p>
          <ul className="mt-3 space-y-2 text-body-sm text-footer-muted">
            <li>
              <a className="transition-colors duration-1 hover:text-white" href="#privacy">
                Privacy
              </a>
            </li>
            <li>
              <a className="transition-colors duration-1 hover:text-white" href="#accessibility">
                Accessibility
              </a>
            </li>
            <li>
              <a className="transition-colors duration-1 hover:text-white" href="#contact">
                Contact
              </a>
            </li>
          </ul>
          <Link
            to="/catalog"
            className="mt-4 inline-block text-body-sm text-white/70 underline underline-offset-[3px] hover:text-white"
          >
            Browse the catalogue
          </Link>
        </div>
      </div>

      <div className="border-t border-white/10">
        <div className="main-container flex flex-col gap-2 py-5 text-overline uppercase tracking-[0.08em] text-footer-faint sm:flex-row sm:items-center sm:justify-between">
          <span>© 2026 Monash University</span>
          <span>Phase 1 demonstration build</span>
        </div>
      </div>
    </footer>
  )
}
