export type DayCode = 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN'

export interface CourseSchedule {
  day: DayCode
  startTime: string
  endTime: string
  location: string
}

export interface Course {
  id: string
  code: string
  name: string
  creditHours: number
  instructor: string
  capacity: number
  availableSeats: number
  semester: string
  faculty: string
  description: string
  schedule: CourseSchedule[]
}

export interface Student {
  studentId: string
  name: string
  email: string
  program: string
}

export interface Semester {
  code: string
  name: string
  active: boolean
}

export interface RegistrationPeriod {
  semester: string
  startAt: string
  endAt: string
  open: boolean
  serverTime: string
}

export interface RegistrationItem {
  registrationId: string
  registeredAt: string
  course: Course
}

export interface RegistrationsResponse {
  semester: string
  totalCredits: number
  maxCredits: number
  items: RegistrationItem[]
}

export interface LoginResponse {
  token: string
  student: Student
}

export interface RegisterResponse {
  registrationId: string
  message: string
  course: Course
  availableSeats: number
}

export interface DropResponse {
  message: string
  availableSeats: number
}

export type ErrorCode =
  | 'VALIDATION_ERROR'
  | 'INVALID_CREDENTIALS'
  | 'UNAUTHORIZED'
  | 'REGISTRATION_CLOSED'
  | 'NOT_OWNER'
  | 'COURSE_NOT_FOUND'
  | 'REGISTRATION_NOT_FOUND'
  | 'COURSE_FULL'
  | 'ALREADY_REGISTERED'
  | 'CREDIT_LIMIT_EXCEEDED'
  | 'INTERNAL_ERROR'
  | 'NETWORK_ERROR'
  | 'UNKNOWN_ERROR'
  | string

export interface ApiErrorPayload {
  error: {
    code: ErrorCode
    message: string
    timestamp: string
  }
}
