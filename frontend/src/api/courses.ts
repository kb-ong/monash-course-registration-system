import client from './client'
import type { Course, Semester } from '../types/api'

export interface CourseQuery {
  semester?: string
  q?: string
  signal?: AbortSignal
}

export async function getSemesters(signal?: AbortSignal): Promise<Semester[]> {
  const { data } = await client.get<Semester[]>('/semesters', { signal })
  return data
}

export async function getCourses(query: CourseQuery = {}): Promise<Course[]> {
  const params: Record<string, string> = {}
  if (query.semester) params.semester = query.semester
  if (query.q) params.q = query.q
  const { data } = await client.get<Course[]>('/courses', {
    params,
    signal: query.signal,
  })
  return data
}

export async function getCourse(id: string, signal?: AbortSignal): Promise<Course> {
  const { data } = await client.get<Course>(`/courses/${encodeURIComponent(id)}`, { signal })
  return data
}
