export type DayCode = 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT' | 'SUN'

export interface CourseSchedule {
  day: DayCode
  startTime: string
  endTime: string
  location: string
}

export interface Prerequisite {
  id: string
  code: string
  name: string
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
  prerequisites: Prerequisite[]
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

export interface WaitlistItem {
  waitlistId: number
  joinedAt: string
  position: number
  course: Course
}

export interface WaitlistResponse {
  semester: string
  items: WaitlistItem[]
}

export interface WaitlistJoinResponse {
  waitlistId: number
  position: number
  waitlistSize: number
  message: string
  course: Course
}

export interface WaitlistLeaveResponse {
  message: string
}

export type NotificationType = 'WAITLIST_JOINED' | 'WAITLIST_PROMOTED'

export interface NotificationItem {
  id: number
  type: NotificationType
  message: string
  createdAt: string
  read: boolean
  courseId: string
  courseCode: string
  courseName: string
}

export interface NotificationsResponse {
  unreadCount: number
  items: NotificationItem[]
}

export interface NotificationReadResponse {
  unreadCount: number
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
  | 'PREREQUISITE_NOT_MET'
  | 'SEAT_AVAILABLE'
  | 'ALREADY_WAITLISTED'
  | 'WAITLIST_NOT_FOUND'
  | 'NOTIFICATION_NOT_FOUND'
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
