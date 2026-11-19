package edu.monash.registration.api.dto;

import java.util.List;

public record WaitlistListDto(String semester, List<WaitlistItemDto> items) {
}
