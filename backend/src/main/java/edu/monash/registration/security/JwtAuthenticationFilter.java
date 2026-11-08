package edu.monash.registration.security;

import edu.monash.registration.error.ApiErrorWriter;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stateless bearer-token authentication. Public endpoints ignore the header entirely (a bad token
 * there is not an error, per FR7 T-FR7-10). Everywhere else an unusable token yields 401
 * UNAUTHORIZED in the frozen envelope, never a 500 and never a Spring default page.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/semesters",
            "/api/registration-period");

    private final JwtService jwtService;
    private final ApiErrorWriter errorWriter;

    public JwtAuthenticationFilter(JwtService jwtService, ApiErrorWriter errorWriter) {
        this.jwtService = jwtService;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (isPublic(path) || header == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!header.startsWith(BEARER_PREFIX)) {
            reject(request, response, "MALFORMED");
            return;
        }

        String token = header.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            reject(request, response, "MALFORMED");
            return;
        }

        try {
            String studentId = jwtService.parseSubject(token);
            if (studentId == null || studentId.isBlank()) {
                reject(request, response, "MALFORMED");
                return;
            }
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(new StudentPrincipal(studentId), null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException ex) {
            reject(request, response, "EXPIRED");
        } catch (SignatureException ex) {
            reject(request, response, "BAD_SIGNATURE");
        } catch (JwtException | IllegalArgumentException ex) {
            reject(request, response, "MALFORMED");
        }
    }

    private boolean isPublic(String path) {
        return PUBLIC_PATHS.contains(path);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String reason)
            throws IOException {
        SecurityContextHolder.clearContext();
        log.warn("auth.token.rejected reason={} path={}", reason, request.getRequestURI());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        errorWriter.write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required.");
    }
}
