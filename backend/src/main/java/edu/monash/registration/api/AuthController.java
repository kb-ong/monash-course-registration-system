package edu.monash.registration.api;

import edu.monash.registration.api.dto.LoginRequest;
import edu.monash.registration.api.dto.LoginResponse;
import edu.monash.registration.api.dto.StudentDto;
import edu.monash.registration.error.UnauthorizedException;
import edu.monash.registration.security.StudentPrincipal;
import edu.monash.registration.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** E1 and E2. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public StudentDto me(@AuthenticationPrincipal StudentPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException();
        }
        return authService.profile(principal.studentId());
    }
}
