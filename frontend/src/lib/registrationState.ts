import type { Course } from '../types/api'

export const MAX_CREDITS = 24

export type RegisterBlockKind = 'none' | 'registered' | 'semester' | 'full' | 'period' | 'credit'

export interface RegisterActionState {
  /** True when the primary Register action must be disabled. */
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
}

/**
 * Single source of truth for the catalogue/detail Register action state.
 *
 * Precedence: already registered → different term → full → period closed → credit limit.
 * A course from a different term is blocked even while the window is open, so the UI never
 * offers a registration the backend would reject (FR3 B1: active semester only).
 */
export function registerActionState({
  course,
  registered,
  periodOpen,
  totalCredits,
  activeSemester,
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

  if (course.availableSeats <= 0) {
    return {
      disabled: true,
      label: 'Full',
      reason: 'No seats available',
      kind: 'full',
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

  return { disabled: false, label: 'Register', kind: 'none', nextCredits }
}
