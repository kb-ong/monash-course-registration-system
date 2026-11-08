package edu.monash.registration.api.dto;

import java.util.List;

public record RegistrationListDto(String semester, int totalCredits, int maxCredits,
        List<RegistrationItemDto> items) {
}
