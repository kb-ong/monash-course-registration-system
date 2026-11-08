import client from './client'
import type {
  DropResponse,
  RegisterResponse,
  RegistrationPeriod,
  RegistrationsResponse,
} from '../types/api'

export async function getRegistrationPeriod(signal?: AbortSignal): Promise<RegistrationPeriod> {
  const { data } = await client.get<RegistrationPeriod>('/registration-period', { signal })
  return data
}

export async function getRegistrations(signal?: AbortSignal): Promise<RegistrationsResponse> {
  const { data } = await client.get<RegistrationsResponse>('/registrations', { signal })
  return data
}

export async function registerCourse(courseId: string): Promise<RegisterResponse> {
  const { data } = await client.post<RegisterResponse>('/registrations', { courseId })
  return data
}

export async function dropRegistration(registrationId: string): Promise<DropResponse> {
  const { data } = await client.delete<DropResponse>(
    `/registrations/${encodeURIComponent(registrationId)}`,
  )
  return data
}

export async function exportRegistrationsCsv(): Promise<Blob> {
  const { data } = await client.get<Blob>('/registrations/export', {
    responseType: 'blob',
    headers: { Accept: 'text/csv' },
  })
  return data
}
