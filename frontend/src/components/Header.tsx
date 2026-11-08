import { AnimatePresence, motion, useReducedMotion } from 'framer-motion'
import { LogOut, Menu, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { cn } from '../lib/cn'
import { initials } from '../lib/format'

const NAV_ITEMS = [
  { to: '/catalog', label: 'Catalogue' },
  { to: '/schedule', label: 'My schedule' },
]

export default function Header() {
  const { student, logout } = useAuth()
  const location = useLocation()
  const reduceMotion = useReducedMotion()
  const [scrolled, setScrolled] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)
  const mobileButtonRef = useRef<HTMLButtonElement>(null)
  const mobileCloseRef = useRef<HTMLButtonElement>(null)
  const mobilePanelRef = useRef<HTMLDivElement>(null)

  const isHeroRoute = location.pathname === '/' || location.pathname === '/dashboard'
  const transparent = isHeroRoute && !scrolled
  const isNavActive = (to: string) =>
    to === '/catalog'
      ? location.pathname.startsWith('/catalog') || location.pathname.startsWith('/courses')
      : location.pathname.startsWith(to)

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 12)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [location.pathname])

  useEffect(() => {
    setMenuOpen(false)
    setMobileOpen(false)
  }, [location.pathname])

  useEffect(() => {
    if (!menuOpen) return
    const onPointerDown = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) setMenuOpen(false)
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setMenuOpen(false)
    }
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [menuOpen])

  useEffect(() => {
    if (!mobileOpen) return
    const previousFocus = document.activeElement
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setMobileOpen(false)
        return
      }
      if (event.key !== 'Tab') return
      const panel = mobilePanelRef.current
      if (!panel) return
      const focusable = Array.from(
        panel.querySelectorAll<HTMLElement>(
          'a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])',
        ),
      ).filter((element) => element.offsetParent !== null)
      if (focusable.length === 0) return
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    document.addEventListener('keydown', onKeyDown)
    const focusTimer = window.setTimeout(() => mobileCloseRef.current?.focus(), 0)
    return () => {
      window.clearTimeout(focusTimer)
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', onKeyDown)
      if (previousFocus instanceof HTMLElement) previousFocus.focus()
    }
  }, [mobileOpen])

  const studentName = student?.name ?? 'Student'
  const studentInitials = initials(studentName)
  const wordmarkColour = transparent ? 'text-white' : 'text-monash-navy'
  const subtitleColour = transparent ? 'text-white/70' : 'text-ink-500'
  const dividerColour = transparent ? 'bg-white/25' : 'bg-line-300'
  const navActiveColour = transparent ? 'text-white' : 'text-ink-900'
  const navIdleColour = transparent
    ? 'text-white/70 hover:text-white'
    : 'text-ink-500 hover:text-ink-900'
  const iconButtonColour = transparent
    ? 'text-white/80 hover:bg-white/10 hover:text-white'
    : 'text-ink-500 hover:bg-monash-mist hover:text-ink-900'

  return (
    <header
      className={cn(
        'sticky top-0 z-40 h-16 border-b transition-colors duration-2 lg:h-[72px]',
        transparent
          ? 'on-navy border-transparent bg-transparent text-white'
          : 'border-line-200 bg-white/[.86] text-ink-900 backdrop-blur-[12px]',
      )}
    >
      <div className="main-container flex h-full items-center justify-between gap-4">
        <div className="flex min-w-0 items-center gap-4">
          <Link to="/" className="flex items-center gap-3" aria-label="Monash Course Registration home">
            <span className={cn('text-[14px] font-bold tracking-[0.18em]', wordmarkColour)}>
              MONASH
            </span>
            <span className={cn('hidden h-5 w-px sm:block', dividerColour)} aria-hidden="true" />
            <span className={cn('hidden text-body-sm sm:block', subtitleColour)}>
              Course Registration
            </span>
          </Link>
        </div>

        <nav className="hidden items-center gap-8 lg:flex" aria-label="Primary">
          {NAV_ITEMS.map((item) => {
            const active = isNavActive(item.to)
            return (
              <Link
                key={item.to}
                to={item.to}
                aria-current={active ? 'page' : undefined}
                className={cn(
                  'relative text-body font-medium transition-colors duration-1',
                  active ? navActiveColour : navIdleColour,
                )}
              >
                {item.label}
                {active ? (
                  <span
                    className="absolute -bottom-[22px] left-0 h-0.5 w-full bg-monash-gold"
                    aria-hidden="true"
                  />
                ) : null}
              </Link>
            )
          })}
        </nav>

        <div className="flex items-center gap-1 sm:gap-2">
          <div className="relative hidden lg:block" ref={menuRef}>
            <button
              type="button"
              onClick={() => setMenuOpen((open) => !open)}
              className="flex items-center gap-2 rounded-md px-1.5 py-1 transition-colors duration-1 hover:bg-white/10 lg:hover:bg-monash-mist"
              aria-haspopup="menu"
              aria-expanded={menuOpen}
            >
              <span
                className={cn(
                  'flex h-8 w-8 items-center justify-center rounded-full text-[12px] font-semibold',
                  transparent ? 'bg-white/15 text-white' : 'bg-monash-navy text-white',
                )}
                aria-hidden="true"
              >
                {studentInitials}
              </span>
              <span className={cn('max-w-[140px] truncate text-body-sm', transparent ? 'text-white' : 'text-ink-900')}>
                {studentName}
              </span>
            </button>

            <AnimatePresence>
              {menuOpen ? (
                <motion.div
                  role="menu"
                  initial={reduceMotion ? { opacity: 0 } : { opacity: 0, y: -4 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={reduceMotion ? { opacity: 0 } : { opacity: 0, y: -4 }}
                  transition={{ duration: reduceMotion ? 0 : 0.12 }}
                  className="absolute right-0 top-full z-50 mt-2 w-64 overflow-hidden rounded-lg border border-line-200 bg-surface text-ink-900 shadow-e3"
                >
                  <div className="border-b border-line-200 px-4 py-3">
                    <p className="text-body font-semibold">{studentName}</p>
                    <p className="tabular text-body-sm text-ink-500">
                      {student?.studentId} · {student?.program}
                    </p>
                    <p className="truncate text-body-sm text-ink-400">{student?.email}</p>
                  </div>
                  <button
                    type="button"
                    role="menuitem"
                    onClick={logout}
                    className="flex w-full items-center gap-2 px-4 py-3 text-left text-body text-ink-700 transition-colors duration-1 hover:bg-monash-mist hover:text-ink-900"
                  >
                    <LogOut className="h-4 w-4 text-ink-500" aria-hidden="true" />
                    Sign out
                  </button>
                </motion.div>
              ) : null}
            </AnimatePresence>
          </div>

          <button
            ref={mobileButtonRef}
            type="button"
            onClick={logout}
            className={cn(
              'hidden h-10 w-10 items-center justify-center rounded-md transition-colors duration-1 lg:flex',
              iconButtonColour,
            )}
            aria-label="Sign out"
          >
            <LogOut className="h-4 w-4" aria-hidden="true" />
          </button>

          <button
            type="button"
            onClick={() => setMobileOpen(true)}
            className={cn(
              'flex h-11 w-11 items-center justify-center rounded-md transition-colors duration-1 lg:hidden',
              iconButtonColour,
            )}
            aria-label="Open menu"
            aria-expanded={mobileOpen}
            aria-controls="mobile-navigation"
          >
            <Menu className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>
      </div>

      <AnimatePresence>
        {mobileOpen ? (
          <motion.div
            className="fixed inset-0 z-50 lg:hidden"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: reduceMotion ? 0 : 0.2 }}
          >
            <div
              className="absolute inset-0 scrim"
              onClick={() => setMobileOpen(false)}
              aria-hidden="true"
            />
            <motion.div
              ref={mobilePanelRef}
              id="mobile-navigation"
              role="dialog"
              aria-modal="true"
              aria-label="Navigation"
              className="on-navy absolute inset-y-0 right-0 flex w-[280px] flex-col bg-monash-navy p-5 text-white shadow-e4"
              initial={reduceMotion ? { x: 0, opacity: 0 } : { x: 280 }}
              animate={{ x: 0, opacity: 1 }}
              exit={reduceMotion ? { opacity: 0 } : { x: 280 }}
              transition={
                reduceMotion
                  ? { duration: 0 }
                  : { duration: 0.2, ease: [0.2, 0.8, 0.2, 1] }
              }
            >
              <div className="flex items-center justify-between">
                <span className="text-[14px] font-bold tracking-[0.18em]">MONASH</span>
                <button
                  ref={mobileCloseRef}
                  type="button"
                  onClick={() => setMobileOpen(false)}
                  className="flex h-11 w-11 items-center justify-center rounded-md text-white/80 hover:bg-white/10 hover:text-white"
                  aria-label="Close menu"
                >
                  <X className="h-5 w-5" aria-hidden="true" />
                </button>
              </div>

              <div className="mt-6 border-t border-white/15 pt-5">
                <div className="flex items-center gap-3">
                  <span className="flex h-10 w-10 items-center justify-center rounded-full bg-white/15 text-[13px] font-semibold">
                    {studentInitials}
                  </span>
                  <div className="min-w-0">
                    <p className="truncate text-body font-semibold">{studentName}</p>
                    <p className="tabular text-body-sm text-white/60">{student?.studentId}</p>
                  </div>
                </div>
              </div>

              <nav className="mt-6 flex flex-col" aria-label="Mobile">
                {NAV_ITEMS.map((item) => {
                  const active = isNavActive(item.to)
                  return (
                    <Link
                      key={item.to}
                      to={item.to}
                      onClick={() => setMobileOpen(false)}
                      aria-current={active ? 'page' : undefined}
                      className={cn(
                        'border-l-2 py-3 pl-3 text-body',
                        active
                          ? 'border-monash-gold text-white'
                          : 'border-transparent text-white/70 hover:text-white',
                      )}
                    >
                      {item.label}
                    </Link>
                  )
                })}
              </nav>

              <button
                type="button"
                onClick={logout}
                className="mt-auto flex min-h-[44px] items-center gap-2 border-t border-white/15 pt-4 text-left text-body text-white/80 hover:text-white"
              >
                <LogOut className="h-4 w-4" aria-hidden="true" />
                Sign out
              </button>
            </motion.div>
          </motion.div>
        ) : null}
      </AnimatePresence>
    </header>
  )
}
