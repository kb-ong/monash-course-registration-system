import client from './client'
import type {
  WaitlistJoinResponse,
  WaitlistLeaveResponse,
  WaitlistResponse,
} from '../types/api'

export async function getWaitlist(signal?: AbortSignal): Promise<WaitlistResponse> {
  const { data } = await client.get<WaitlistResponse>('/waitlist', { signal })
  return data
}

export async function joinWaitlist(courseId: string): Promise<WaitlistJoinResponse> {
  const { data } = await client.post<WaitlistJoinResponse>('/waitlist', { courseId })
  return data
}

export async function leaveWaitlist(waitlistId: number): Promise<WaitlistLeaveResponse> {
  const { data } = await client.delete<WaitlistLeaveResponse>(
    `/waitlist/${encodeURIComponent(String(waitlistId))}`,
  )
  return data
}
