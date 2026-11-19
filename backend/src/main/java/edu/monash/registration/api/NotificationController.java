package edu.monash.registration.api;

import edu.monash.registration.api.dto.NotificationListDto;
import edu.monash.registration.api.dto.NotificationReadResponse;
import edu.monash.registration.error.NotificationNotFoundException;
import edu.monash.registration.error.UnauthorizedException;
import edu.monash.registration.security.StudentPrincipal;
import edu.monash.registration.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** FR11 in-app notification inbox (E14–E15). Students only see and mutate their own rows. */
@RestController
@RequestMapping("/api")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/notifications")
    public NotificationListDto notifications(
            @AuthenticationPrincipal StudentPrincipal principal) {
        return notificationService.list(requireStudent(principal));
    }

    @PostMapping("/notifications/{notificationId}/read")
    public NotificationReadResponse markRead(@AuthenticationPrincipal StudentPrincipal principal,
            @PathVariable("notificationId") String notificationId) {
        return notificationService.markRead(requireStudent(principal), parseId(notificationId));
    }

    private String requireStudent(StudentPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException();
        }
        return principal.studentId();
    }

    private Long parseId(String raw) {
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            throw new NotificationNotFoundException();
        }
    }
}
