package edu.monash.registration.api;

import edu.monash.registration.api.dto.DropResponse;
import edu.monash.registration.api.dto.RegisterRequest;
import edu.monash.registration.api.dto.RegisterResponse;
import edu.monash.registration.api.dto.RegistrationListDto;
import edu.monash.registration.api.dto.RegistrationPeriodDto;
import edu.monash.registration.error.MalformedRegistrationIdException;
import edu.monash.registration.error.UnauthorizedException;
import edu.monash.registration.error.ValidationException;
import edu.monash.registration.security.StudentPrincipal;
import edu.monash.registration.service.CsvExportService;
import edu.monash.registration.service.RegistrationPeriodService;
import edu.monash.registration.service.RegistrationService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** E6–E10. */
@RestController
@RequestMapping("/api")
public class RegistrationController {

    private static final MediaType TEXT_CSV_UTF8 =
            new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final RegistrationService registrationService;
    private final RegistrationPeriodService periodService;
    private final CsvExportService csvExportService;

    public RegistrationController(RegistrationService registrationService,
            RegistrationPeriodService periodService, CsvExportService csvExportService) {
        this.registrationService = registrationService;
        this.periodService = periodService;
        this.csvExportService = csvExportService;
    }

    @GetMapping("/registration-period")
    public RegistrationPeriodDto registrationPeriod() {
        return periodService.current();
    }

    @GetMapping("/registrations")
    public RegistrationListDto registrations(@AuthenticationPrincipal StudentPrincipal principal) {
        return registrationService.listRegistrations(requireStudent(principal));
    }

    @PostMapping("/registrations")
    public ResponseEntity<RegisterResponse> register(@AuthenticationPrincipal StudentPrincipal principal,
            @Valid @RequestBody RegisterRequest request) {
        UUID courseId = parseUuid(request.courseId(), "courseId");
        RegisterResponse response = registrationService.register(requireStudent(principal), courseId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/registrations/{registrationId}")
    public DropResponse drop(@AuthenticationPrincipal StudentPrincipal principal,
            @PathVariable("registrationId") String registrationId) {
        UUID id;
        try {
            id = UUID.fromString(registrationId);
        } catch (IllegalArgumentException ex) {
            // Opaque path identifiers are "not found", never 400 (FR4 validation rules). The
            // distinct type lets the error boundary audit the attempt as DROP_REJECTED.
            throw new MalformedRegistrationIdException();
        }
        return registrationService.drop(requireStudent(principal), id);
    }

    @GetMapping("/registrations/export")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal StudentPrincipal principal) {
        byte[] body = csvExportService.export(requireStudent(principal));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(TEXT_CSV_UTF8);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"course-list.csv\"");
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }

    private String requireStudent(StudentPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException();
        }
        return principal.studentId();
    }

    private UUID parseUuid(String raw, String field) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(field + " must be a valid UUID.");
        }
    }
}
