import client from './client'
import type { NotificationReadResponse, NotificationsResponse } from '../types/api'

export async function getNotifications(signal?: AbortSignal): Promise<NotificationsResponse> {
  const { data } = await client.get<NotificationsResponse>('/notifications', { signal })
  return data
}

export async function markNotificationRead(
  notificationId: number,
): Promise<NotificationReadResponse> {
  const { data } = await client.post<NotificationReadResponse>(
    `/notifications/${encodeURIComponent(String(notificationId))}/read`,
  )
  return data
}
