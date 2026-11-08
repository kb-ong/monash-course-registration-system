import { AlertCircle } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { normalizeApiError } from '../api/client'
import { creditPoints, scheduleLine } from '../lib/format'
import { useRegistrations } from '../registration/RegistrationsContext'
import { useRegistrationPeriod } from '../registration/RegistrationPeriodContext'
import type { RegistrationItem } from '../types/api'
import Button from './ui/Button'
import Modal from './ui/Modal'
import { useToast } from './ui/Toast'

interface DropDialogProps {
  registration: RegistrationItem | null
  open: boolean
  onClose: () => void
  onSettled?: () => void
}

export default function DropDialog({
  registration,
  open,
  onClose,
  onSettled,
}: DropDialogProps) {
  const { drop, refresh } = useRegistrations()
  const { refresh: refreshPeriod } = useRegistrationPeriod()
  const { pushToast } = useToast()
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const keepRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (open) {
      setError(null)
      setSubmitting(false)
    }
  }, [open, registration?.registrationId])

  if (!registration) return null

  const course = registration.course
  const slot = course.schedule[0]

  const handleConfirm = async () => {
    if (submitting) return
    setSubmitting(true)
    setError(null)
    try {
      const result = await drop(registration.registrationId)
      pushToast({
        variant: 'success',
        title: `Dropped ${course.code}`,
        description: `Seat ${result.availableSeats} of ${course.capacity} available`,
      })
      onSettled?.()
      onClose()
    } catch (caught) {
      const apiError = normalizeApiError(caught)
      switch (apiError.code) {
        case 'NOT_OWNER':
          void refresh()
          pushToast({ variant: 'error', title: "You can't drop that registration." })
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
        case 'REGISTRATION_NOT_FOUND':
          void refresh()
          pushToast({ variant: 'info', title: 'That registration no longer exists.' })
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
      title={`Drop ${course.code}?`}
      variant="destructive"
      pending={submitting}
      initialFocusRef={keepRef}
      footer={
        <>
          <Button
            ref={keepRef}
            variant="secondary"
            size="md"
            onClick={onClose}
            disabled={submitting}
          >
            Keep it
          </Button>
          <Button variant="danger" size="md" onClick={() => void handleConfirm()} loading={submitting}>
            Drop course
          </Button>
        </>
      }
    >
      <div className="space-y-1">
        <p className="text-body-lg text-ink-900">{course.name}</p>
        <p className="text-body-sm text-ink-500">
          {slot ? `${scheduleLine(slot)} · ` : ''}
          {creditPoints(course.creditHours)}
        </p>
      </div>

      <p className="mt-4 text-body text-ink-700">
        Your seat will be released immediately and may be taken by another student.
      </p>

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
