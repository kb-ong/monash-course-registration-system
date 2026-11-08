import type { CourseSchedule, DayCode } from '../types/api'

export const TZ = 'Asia/Kuala_Lumpur'

export const DAY_ORDER: DayCode[] = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN']

export const DAY_SHORT: Record<DayCode, string> = {
  MON: 'Mon',
  TUE: 'Tue',
  WED: 'Wed',
  THU: 'Thu',
  FRI: 'Fri',
  SAT: 'Sat',
  SUN: 'Sun',
}

export const DAY_UPPER: Record<DayCode, string> = {
  MON: 'MON',
  TUE: 'TUE',
  WED: 'WED',
  THU: 'THU',
  FRI: 'FRI',
  SAT: 'SAT',
  SUN: 'SUN',
}

export function dayRank(day: DayCode): number {
  return DAY_ORDER.indexOf(day)
}

export function dayShort(day: DayCode): string {
  return DAY_SHORT[day]
}

export function timeToMinutes(value: string): number {
  const [rawHours, rawMinutes] = value.split(':')
  const hours = Number(rawHours)
  const minutes = Number(rawMinutes)
  if (!Number.isFinite(hours) || !Number.isFinite(minutes)) return 0
  return hours * 60 + minutes
}

export function minutesToTime(total: number): string {
  const hours = Math.floor(total / 60)
  const minutes = total % 60
  return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`
}

export function timeRange(start: string, end: string): string {
  return `${start}\u2013${end}`
}

export function scheduleLine(slot: CourseSchedule): string {
  return `${dayShort(slot.day)} ${timeRange(slot.startTime, slot.endTime)}`
}

export function firstSchedule(course: { schedule: CourseSchedule[] }): CourseSchedule | null {
  return course.schedule.length > 0 ? course.schedule[0] : null
}

function parseDate(value: string): Date {
  return new Date(value)
}

export function formatDate(value: string): string {
  return new Intl.DateTimeFormat('en-AU', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    timeZone: TZ,
  }).format(parseDate(value))
}

export function formatDateLong(value: string): string {
  return new Intl.DateTimeFormat('en-AU', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    timeZone: TZ,
  }).format(parseDate(value))
}

export function formatTimeOfDay(value: string): string {
  return new Intl.DateTimeFormat('en-AU', {
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
    timeZone: TZ,
  })
    .format(parseDate(value))
    .replace(/\s/g, ' ')
    .toLowerCase()
}

export function formatDateTime(value: string): string {
  return `${formatDate(value)}, ${formatTimeOfDay(value)}`
}

export function formatPrintedDate(date: Date): string {
  return new Intl.DateTimeFormat('en-AU', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
    timeZone: TZ,
  }).format(date)
}

export function semesterName(code: string | undefined | null): string {
  if (!code) return 'Semester'
  const match = /^(\d{4})-S([12])$/.exec(code)
  if (!match) return code
  return `Semester ${match[2]} ${match[1]}`
}

export function semesterShort(code: string | undefined | null): string {
  return semesterName(code).replace('Semester ', 'S')
}

/** Difference between a future timestamp and the server time, in milliseconds. */
export function remainingMs(target: string, serverTime: string): number {
  return parseDate(target).getTime() - parseDate(serverTime).getTime()
}

export function relativeTime(target: string, serverTime: string): string {
  const diff = remainingMs(target, serverTime)
  const abs = Math.abs(diff)
  const minutes = Math.round(abs / 60000)
  const hours = Math.round(abs / 3600000)
  const days = Math.round(abs / 86400000)
  const prefix = diff >= 0 ? 'in ' : ''
  const suffix = diff >= 0 ? '' : ' ago'

  if (minutes < 1) return diff >= 0 ? 'now' : 'just now'
  if (minutes < 60) return `${prefix}${minutes} min${suffix}`
  if (hours < 24) return `${prefix}${hours} hour${hours === 1 ? '' : 's'}${suffix}`
  return `${prefix}${days} day${days === 1 ? '' : 's'}${suffix}`
}

export function countdownParts(target: string, now: number): string {
  const diff = Math.max(0, parseDate(target).getTime() - now)
  const totalSeconds = Math.floor(diff / 1000)
  const days = Math.floor(totalSeconds / 86400)
  const hours = Math.floor((totalSeconds % 86400) / 3600)
  const minutes = Math.floor((totalSeconds % 3600) / 60)
  const seconds = totalSeconds % 60
  return `${days}d ${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(
    seconds,
  ).padStart(2, '0')}`
}

export function greeting(serverTime: string | undefined): string {
  if (!serverTime) return 'Welcome back'
  const hour = Number(
    new Intl.DateTimeFormat('en-GB', {
      hour: 'numeric',
      hourCycle: 'h23',
      timeZone: TZ,
    })
      .formatToParts(parseDate(serverTime))
      .find((part) => part.type === 'hour')?.value ?? '9',
  )
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}

export function firstName(name: string): string {
  return name.trim().split(/\s+/)[0] ?? name
}

export function initials(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part.charAt(0).toUpperCase())
    .join('')
}

export function creditPoints(value: number): string {
  return `${value} credit point${value === 1 ? '' : 's'}`
}

export function creditBadge(value: number): string {
  return `${value} CP`
}

export function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

export function greetingWithName(serverTime: string | undefined, name: string | undefined): string {
  return name ? `${greeting(serverTime)}, ${firstName(name)}.` : `${greeting(serverTime)}.`
}

export function facultyShort(faculty: string): string {
  switch (faculty) {
    case 'Information Technology':
      return 'IT'
    case 'Business':
      return 'Business'
    case 'Engineering':
      return 'Engineering'
    case 'Law':
      return 'Law'
    case 'Science':
      return 'Science'
    default:
      return faculty
  }
}
