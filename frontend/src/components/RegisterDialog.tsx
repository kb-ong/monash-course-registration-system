import { AlertCircle } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { normalizeApiError } from '../api/client'
import { cn } from '../lib/cn'
import { creditPoints, scheduleLine } from '../lib/format'
import { useRegistrations } from '../registration/RegistrationsContext'
import { useRegistrationPeriod } from '../registration/RegistrationPeriodContext'
import { useWaitlist } from '../registration/WaitlistContext'
import type { Course } from '../types/api'
import Button from './ui/Button'
import Modal from './ui/Modal'
import { useToast } from './ui/Toast'

interface RegisterDialogProps {
  course: Course | null
  open: boolean
  onClose: () => void
  onSettled?: () => void
}

export default function RegisterDialog({
  course,
  open,
  onClose,
  onSettled,
}: RegisterDialogProps) {
  const { data, register, refresh } = useRegistrations()
  const { refresh: refreshPeriod } = useRegistrationPeriod()
  const { refresh: refreshWaitlist } = useWaitlist()
  const { pushToast } = useToast()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const cancelRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (open) {
      setError(null)
      setSubmitting(false)
    }
  }, [open, course?.id])

  if (!course) return null

  const totalCredits = data?.totalCredits ?? 0
  const maxCredits = data?.maxCredits
  const nextCredits = totalCredits + course.creditHours
  const remainingCredits =
    maxCredits === undefined ? null : Math.max(0, maxCredits - totalCredits)
  const overLimit = maxCredits !== undefined && nextCredits > maxCredits
  const slot = course.schedule[0]

  const handleConfirm = async () => {
    if (submitting) return
    setSubmitting(true)
    setError(null)
    try {
      const result = await register(course.id)
      // FR11: a successful registration clears any queue place for this course.
      void refreshWaitlist()
      pushToast({
        variant: 'success',
        title: `Registered for ${course.code}`,
        description:
          result.availableSeats === 0
            ? 'You took the last seat.'
            : `Seat ${result.availableSeats} of ${result.course.capacity}`,
      })
      onSettled?.()
      onClose()
    } catch (caught) {
      const apiError = normalizeApiError(caught)
      switch (apiError.code) {
        case 'COURSE_FULL':
          void refresh()
          pushToast({
            variant: 'error',
            title: 'That course just filled up.',
            description: 'No seat was taken.',
          })
          onSettled?.()
          onClose()
          break
        case 'CREDIT_LIMIT_EXCEEDED':
          // The server message names the configured limit and the remaining credits (FR10).
          // Refresh so the dialog recomputes against the authoritative total if another
          // registration landed while it was open.
          void refresh()
          setError(apiError.message)
          break
        case 'PREREQUISITE_NOT_MET':
          // The server names every missing course in the message (FR8).
          setError(apiError.message)
          break
        case 'TIMETABLE_CLASH':
          // FR9: the server names every conflicting course and its overlapping time.
          setError(apiError.message)
          break
        case 'ALREADY_REGISTERED':
          void refresh()
          void refreshWaitlist()
          pushToast({ variant: 'info', title: "You're already registered in this course." })
          onSettled?.()
          onClose()
          break
        case 'REGISTRATION_CLOSED':
          void refresh()
          pushToast({ variant: 'info', title: 'Registration has closed.' })
          void refreshPeriod()
          onSettled?.()
          onClose()
          break
        case 'COURSE_NOT_FOUND':
          void refresh()
          pushToast({ variant: 'error', title: 'That course is no longer available.' })
          onSettled?.()
          onClose()
          break
        case 'NETWORK_ERROR':
          setError("Couldn't reach the server. Try again.")
          break
        case 'UNAUTHORIZED':
          break
        default:
          setError(apiError.message)
          break
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={`Register for ${course.code}?`}
      pending={submitting}
      initialFocusRef={cancelRef}
      footer={
        <>
          <Button
            ref={cancelRef}
            variant="secondary"
            size="md"
            onClick={onClose}
            disabled={submitting}
          >
            Cancel
          </Button>
          <Button
            variant="primary"
            size="md"
            onClick={() => void handleConfirm()}
            loading={submitting}
            disabled={overLimit}
          >
            Confirm register
          </Button>
        </>
      }
    >
      <div className="space-y-1">
        <p className="text-body-lg text-ink-900">{course.name}</p>
        <p className="text-body-sm text-ink-500">
          {creditPoints(course.creditHours)}
          {slot ? ` · ${scheduleLine(slot)}` : ''}
        </p>
        {slot ? <p className="text-body-sm text-ink-500">{slot.location}</p> : null}
        {course.prerequisites.length > 0 ? (
          <p className="pt-1 text-body-sm text-ink-500">
            Requires{' '}
            {course.prerequisites.map((prerequisite) => prerequisite.code).join(', ')}
          </p>
        ) : null}
      </div>

      {maxCredits === undefined || remainingCredits === null ? (
        <p className="mt-4 text-body text-ink-700">
          This will bring you to {nextCredits} credits.
        </p>
      ) : (
        <div className="mt-4 space-y-1" role="status" aria-live="polite">
          <p className="text-body text-ink-700">
            This will bring you to {nextCredits} of {maxCredits} credits.
          </p>
          <p
            className={cn(
              'text-body-sm',
              overLimit ? 'font-semibold text-danger-600' : 'text-ink-500',
            )}
          >
            {overLimit
              ? `You have ${remainingCredits} credit${
                  remainingCredits === 1 ? '' : 's'
                } remaining, so this course does not fit within your limit.`
              : `You would have ${maxCredits - nextCredits} credit${
                  maxCredits - nextCredits === 1 ? '' : 's'
                } remaining.`}
          </p>
        </div>
      )}

      {error ? (
        <p
          role="alert"
          className="mt-4 flex items-start gap-2 rounded-md bg-danger-50 px-3 py-2 text-body-sm text-danger-600"
        >
          <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
          <span>{error}</span>
        </p>
      ) : null}
    </Modal>
  )
}
