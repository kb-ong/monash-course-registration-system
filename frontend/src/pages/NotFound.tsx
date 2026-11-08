import { ArrowLeft } from 'lucide-react'
import ButtonLink from '../components/ui/ButtonLink'

export default function NotFound() {
  return (
    <div className="flex min-h-screen flex-col bg-canvas">
      <a
        href="#main"
        className="skip-link sr-only rounded-md bg-surface px-4 py-2 text-body font-semibold text-monash-navy focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-30"
      >
        Skip to main content
      </a>
      <header className="border-b border-line-200 bg-surface">
        <div className="main-container flex h-16 items-center gap-3 lg:h-[72px]">
          <span className="text-[14px] font-bold tracking-[0.18em] text-monash-navy">MONASH</span>
          <span className="h-5 w-px bg-line-300" aria-hidden="true" />
          <span className="text-body-sm text-ink-500">Course Registration</span>
        </div>
      </header>

      <main id="main" className="flex flex-1 items-center justify-center px-6 py-16">
        <div className="w-full max-w-[560px] text-center">
          <p className="font-display text-[80px] font-semibold leading-none tracking-[-0.03em] text-monash-navy sm:text-[96px]">
            404
          </p>
          <span className="gold-rule mx-auto mt-6" aria-hidden="true" />
          <h1 className="mt-6 font-display text-h2 text-ink-900">This page doesn't exist.</h1>
          <p className="mx-auto mt-3 max-w-[46ch] text-body text-ink-500">
            The link may be out of date, or the page may have moved. Your catalogue and schedule are
            still where you left them.
          </p>
          <div className="mt-7 flex flex-wrap items-center justify-center gap-3">
            <ButtonLink to="/catalog" variant="primary">
              <ArrowLeft className="h-4 w-4" aria-hidden="true" />
              Back to catalogue
            </ButtonLink>
            <ButtonLink to="/" variant="secondary">
              Go to dashboard
            </ButtonLink>
          </div>
        </div>
      </main>

      <footer className="on-navy border-t-2 border-monash-gold bg-monash-navy py-6">
        <div className="main-container flex flex-col gap-1 text-overline uppercase tracking-[0.08em] text-footer-faint sm:flex-row sm:items-center sm:justify-between">
          <span>© 2026 Monash University</span>
          <span>Phase 1 demonstration build</span>
        </div>
      </footer>
    </div>
  )
}
