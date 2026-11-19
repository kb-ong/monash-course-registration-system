import { AlertCircle, Clock } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { normalizeApiError } from '../api/client'
import { creditPoints, scheduleLine } from '../lib/format'
import { useRegistrations } from '../registration/RegistrationsContext'
import { useRegistrationPeriod } from '../registration/RegistrationPeriodContext'
import { useWaitlist } from '../registration/WaitlistContext'
import type { Course } from '../types/api'
import Button from './ui/Button'
import Modal from './ui/Modal'
import { useToast } from './ui/Toast'

interface WaitlistDialogProps {
  course: Course | null
  open: boolean
  onClose: () => void
  onSettled?: () => void
}

/**
 * FR11: confirm joining the queue for a full course. The backend re-checks prerequisites, the
 * credit cap and whether a seat appeared in the meantime; the dialog shows those rejections inline
 * and only closes on a real queue place.
 */
export default function WaitlistDialog({
  course,
  open,
  onClose,
  onSettled,
}: WaitlistDialogProps) {
  const { refresh: refreshRegistrations } = useRegistrations()
  const { refresh: refreshPeriod } = useRegistrationPeriod()
  const { join } = useWaitlist()
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

  const slot = course.schedule[0]

  const handleConfirm = async () => {
    if (submitting) return
    setSubmitting(true)
    setError(null)
    try {
      const result = await join(course.id)
      pushToast({
        variant: 'success',
        title: `You're #${result.position} on the waitlist for ${course.code}`,
        description:
          result.position === 1
            ? "You're first in line. We'll enroll you automatically when a seat opens."
            : `${result.waitlistSize} student${result.waitlistSize === 1 ? '' : 's'} waiting. We'll enroll you automatically when your turn comes.`,
      })
      onSettled?.()
      onClose()
    } catch (caught) {
      const apiError = normalizeApiError(caught)
      switch (apiError.code) {
        case 'SEAT_AVAILABLE':
          void refreshRegistrations()
          pushToast({
            variant: 'info',
            title: 'A seat just opened.',
            description: `Register for ${course.code} instead — no waitlist needed.`,
          })
          onSettled?.()
          onClose()
          break
        case 'ALREADY_WAITLISTED':
          pushToast({ variant: 'info', title: "You're already on this waitlist." })
          onSettled?.()
          onClose()
          break
        case 'CREDIT_LIMIT_EXCEEDED':
          setError(
            "Joining this waitlist would put you over the 24-credit limit. Drop a course first.",
          )
          break
        case 'PREREQUISITE_NOT_MET':
          setError(apiError.message)
          break
        case 'REGISTRATION_CLOSED':
          void refreshPeriod()
          pushToast({ variant: 'info', title: 'Registration has closed.' })
          onSettled?.()
          onClose()
          break
        case 'COURSE_NOT_FOUND':
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
      title={`Join the waitlist for ${course.code}?`}
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
          <Button variant="gold" size="md" onClick={() => void handleConfirm()} loading={submitting}>
            Join waitlist
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
        {course.prerequisites.length > 0 ? (
          <p className="pt-1 text-body-sm text-ink-500">
            Requires {course.prerequisites.map((prerequisite) => prerequisite.code).join(', ')}
          </p>
        ) : null}
      </div>

      <div className="mt-4 flex items-start gap-2 rounded-md bg-warning-50 px-3 py-2 text-body-sm text-warning-700">
        <Clock className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
        <p>
          This course is full. Joining the waitlist does not reserve credit points or a seat; you
          are placed at the back of the queue and enrolled automatically when a seat opens.
        </p>
      </div>

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
