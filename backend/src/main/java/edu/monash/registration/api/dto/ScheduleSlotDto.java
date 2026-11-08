package edu.monash.registration.api.dto;

/** One weekly meeting slot, times rendered as HH:mm (spec §9.6). */
public record ScheduleSlotDto(String day, String startTime, String endTime, String location) {
}
