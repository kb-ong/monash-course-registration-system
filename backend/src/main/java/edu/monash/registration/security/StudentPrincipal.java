package edu.monash.registration.security;

/** The authenticated caller identity: the JWT subject, nothing else (no roles in Phase 1). */
public record StudentPrincipal(String studentId) {
}
