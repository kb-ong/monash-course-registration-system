package edu.monash.registration.api.dto;

import java.util.List;

public record NotificationListDto(int unreadCount, List<NotificationItemDto> items) {
}
