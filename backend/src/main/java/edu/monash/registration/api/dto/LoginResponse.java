package edu.monash.registration.api.dto;

public record LoginResponse(String token, StudentDto student) {
}
