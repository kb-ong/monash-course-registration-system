import type { Course } from '../types/api'

export const MAX_CREDITS = 24

export type RegisterBlockKind =
  | 'none'
  | 'registered'
  | 'semester'
  | 'waitlist'
  | 'waitlisted'
  | 'period'
  | 'credit'

export interface RegisterActionState {
  /** True when the primary action must be disabled. */
  disabled: boolean
  label: string
  reason?: string
  kind: RegisterBlockKind
  /** Total credits if this course were added. */
  nextCredits: number
}

interface RegisterActionArgs {
  course: Course
  registered: boolean
  periodOpen: boolean
  totalCredits: number
  /** The semester registrations are currently accepted for. Empty when unknown. */
  activeSemester?: string
  /** FR11: the student's 1-based queue position when already waiting for this course. */
  waitlistPosition?: number
}

/**
 * Single source of truth for the catalogue/detail primary action state.
 *
 * Precedence: already registered → different term → already waitlisted → period closed →
 * credit limit → full course (waitlist) → register.
 *
 * A course from a different term is blocked even while the window is open, so the UI never offers
 * a registration or a queue place the backend would reject (FR3 B1). A full course offers the
 * waitlist, but the client still treats that as advisory: the server can answer SEAT_AVAILABLE,
 * ALREADY_WAITLISTED or an eligibility rejection (FR11).
 */
export function registerActionState({
  course,
  registered,
  periodOpen,
  totalCredits,
  activeSemester,
  waitlistPosition,
}: RegisterActionArgs): RegisterActionState {
  const nextCredits = totalCredits + course.creditHours

  if (registered) {
    return { disabled: false, label: 'View in schedule', kind: 'registered', nextCredits }
  }

  if (activeSemester && course.semester !== activeSemester) {
    return {
      disabled: true,
      label: 'Not open for registration',
      reason: 'Not offered in the current registration period.',
      kind: 'semester',
      nextCredits,
    }
  }

  if (waitlistPosition !== undefined) {
    return {
      disabled: true,
      label: `On waitlist · #${waitlistPosition}`,
      reason: `You are #${waitlistPosition} on the waitlist for this course.`,
      kind: 'waitlisted',
      nextCredits,
    }
  }

  if (!periodOpen) {
    return {
      disabled: true,
      label: 'Registration closed',
      reason: 'Registration is closed',
      kind: 'period',
      nextCredits,
    }
  }

  if (nextCredits > MAX_CREDITS) {
    return {
      disabled: true,
      label: 'Credit limit reached',
      reason: `That would take you to ${nextCredits} credits. Your limit is ${MAX_CREDITS}.`,
      kind: 'credit',
      nextCredits,
    }
  }

  if (course.availableSeats <= 0) {
    return {
      disabled: false,
      label: 'Join waitlist',
      reason: 'This course is full — join the waitlist',
      kind: 'waitlist',
      nextCredits,
    }
  }

  return { disabled: false, label: 'Register', kind: 'none', nextCredits }
}
