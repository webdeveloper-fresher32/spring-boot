# Module 20: Production Security Best Practices

---

## 1. Overview

Deploying Spring Security in production requires hardening beyond defaults. This module covers the OWASP Top 10 mitigations, HTTP security header configuration, and operational best practices aligned with enterprise security baselines.

---

## 2. OWASP Top 10 and Spring Security Mitigations

| OWASP Risk | Spring Security Mitigation |
|---|---|
| A01 Broken Access Control | URL rules (`authorizeHttpRequests`), `@PreAuthorize`, `RoleHierarchy` |
| A02 Cryptographic Failures | `BCryptPasswordEncoder`, HTTPS enforcement, `HttpOnly`/`Secure` cookies |
| A03 Injection | Input validation with Bean Validation; Spring Security does not handle SQL injection directly |
| A05 Security Misconfiguration | Audit `SecurityFilterChain`; disable default endpoints; remove unused filters |
| A07 Identification & Auth Failures | Brute-force rate limiting; account lockout; MFA via custom `AuthenticationProvider` |
| A08 Integrity Failures | JWT signature validation; CSRF tokens; content integrity checks |
| A09 Logging & Monitoring | `AuthenticationEventPublisher`; `SecurityEventListener` for audit trails |

---

## 3. Security Headers via `HeaderWriterFilter`

Spring Security's `HeaderWriterFilter` automatically adds security response headers. Configure them explicitly:

```java
http.headers(headers -> headers
    .frameOptions(frame -> frame.deny())                          // Prevents Clickjacking via iframes
    .xssProtection(xss -> xss.disable())                          // Obsolete; rely on CSP instead
    .contentTypeOptions(Customizer.withDefaults())                 // X-Content-Type-Options: nosniff
    .httpStrictTransportSecurity(hsts -> hsts                     // HSTS: forces HTTPS
        .includeSubDomains(true)
        .maxAgeInSeconds(31536000)
    )
    .contentSecurityPolicy(csp -> csp                             // Content Security Policy
        .policyDirectives("default-src 'self'; frame-ancestors 'none'")
    )
);
```

### Expected Response Headers
```
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Strict-Transport-Security: max-age=31536000; includeSubDomains
Content-Security-Policy: default-src 'self'; frame-ancestors 'none'
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
```

---

## 4. Brute-Force Protection via Event Listeners

Spring Security publishes authentication events that you can listen to for lockout enforcement:

```java
package com.example.security.events;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LoginAttemptListener {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptListener.class);
    private static final int MAX_ATTEMPTS = 5;

    private final ConcurrentHashMap<String, AtomicInteger> failureCache = new ConcurrentHashMap<>();

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        failureCache.computeIfAbsent(username, k -> new AtomicInteger(0)).incrementAndGet();
        int failures = failureCache.get(username).get();

        log.warn("Login failed for user '{}'. Attempts: {}", username, failures);

        if (failures >= MAX_ATTEMPTS) {
            log.error("ACCOUNT LOCKOUT: User '{}' exceeded {} failed attempts!", username, MAX_ATTEMPTS);
            // Trigger account lock in your UserService here
        }
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        failureCache.remove(username); // Reset counter on success
        log.info("Login success for user: {}", username);
    }
}
```

---

## 5. Requiring HTTPS (HTTP to HTTPS Redirect)

```java
http.requiresChannel(channel -> channel
    .anyRequest().requiresSecure() // Redirect all HTTP requests to HTTPS
);
```

For cookie security, configure `application.properties`:
```properties
server.servlet.session.cookie.secure=true
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=strict
```

---

## 6. Sensitive Information Leakage Prevention

```java
// Hide framework details from HTTP responses
server.error.include-stacktrace=never
server.error.include-message=never
server.error.include-exception=false
server.error.include-binding-errors=never
```

Disable Spring Boot Actuator exposure in production:
```properties
# Only expose health and info; nothing else
management.endpoints.web.exposure.include=health,info
management.endpoint.health.show-details=never
```

---

## 7. Security Audit Logging Pattern

```java
package com.example.security.audit;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SecurityAuditLogger {

    private static final Logger audit = LoggerFactory.getLogger("SECURITY_AUDIT");

    @EventListener
    public void logSuccessfulLogins(AuthenticationSuccessEvent event) {
        audit.info("AUTH_SUCCESS user={} type={}", 
            event.getAuthentication().getName(),
            event.getAuthentication().getClass().getSimpleName());
    }

    @EventListener
    public void logAccessDenied(AuthorizationDeniedEvent<?> event) {
        audit.warn("ACCESS_DENIED principal={} resource={}",
            event.getAuthentication().get().getName(),
            event.getAuthorizationResult());
    }
}
```

---

## 8. Production Checklist

```
Security Hardening Checklist:
─────────────────────────────
[ ] All endpoints explicitly mapped — no implicit openings.
[ ] `anyRequest().denyAll()` or `anyRequest().authenticated()` as catch-all.
[ ] Passwords stored with BCrypt strength >= 10.
[ ] All REST APIs behind HTTPS only.
[ ] CSRF disabled ONLY on verified stateless token endpoints.
[ ] Session cookies configured with Secure, HttpOnly, SameSite=Strict.
[ ] Security headers: CSP, HSTS, X-Frame-Options: DENY.
[ ] Sensitive error details hidden from HTTP responses.
[ ] Actuator endpoints access-restricted by IP or dedicated security chain.
[ ] Authentication event publishing enabled for audit logging.
[ ] Account lockout mechanism for repeated failures.
[ ] JWT access token lifespan <= 15 minutes.
[ ] Refresh tokens stored server-side and revocable.
```

---

## 9. Interview Questions

**Q: What is the difference between `HttpOnly` and `Secure` cookie flags?**
- `HttpOnly`: The cookie cannot be accessed by JavaScript (`document.cookie`), mitigating XSS-based session theft.
- `Secure`: The cookie is only transmitted over HTTPS connections, preventing cleartext session ID exposure over HTTP.

**Q: What is a timing attack in the context of authentication?**
If your login logic returns faster when a username does not exist vs when the password is wrong, an attacker can measure response latencies to enumerate valid usernames. Constant-time operations (like always running `PasswordEncoder.matches()` even on unknown usernames) are used by `DaoAuthenticationProvider` to prevent this.

---

## 10. Quick Revision Notes

- **Security Headers:** Always configure explicitly; `HeaderWriterFilter` is active by default but CSP needs manual policy strings.
- **Audit Events:** Use `@EventListener` on `AuthenticationSuccessEvent` and `AuthorizationDeniedEvent`.
- **Brute Force:** Spring Security does not rate-limit by default; implement via event listeners and service-layer lockout.
- **HTTPS:** `requiresSecure()` redirects HTTP → HTTPS at the filter chain level.
- **Actuator:** Always restrict Actuator exposure in production — it's a common attack vector.
