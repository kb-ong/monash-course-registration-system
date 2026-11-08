package edu.monash.registration.api.dto;

import java.util.List;
import java.util.UUID;

/** The single course shape used by the catalogue, detail, registration and schedule responses. */
public record CourseDto(
        UUID id,
        String code,
        String name,
        int creditHours,
        String instructor,
        int capacity,
        int availableSeats,
        String semester,
        String faculty,
        String description,
        List<ScheduleSlotDto> schedule) {
}
