import client from './client'
import type { LoginResponse, Student } from '../types/api'

export async function login(studentId: string, password: string): Promise<LoginResponse> {
  const { data } = await client.post<LoginResponse>('/auth/login', { studentId, password })
  return data
}

export async function getMe(): Promise<Student> {
  const { data } = await client.get<Student>('/auth/me')
  return data
}
