# Module 10: Form Login & HTTP Basic Authentication

---

## 1. Definition

**Form Login** and **HTTP Basic** are the two foundational browser and client authentication mechanisms built into Spring Security.

* **HTTP Basic (`httpBasic()`):** A protocol-level authentication mechanism where credentials (username and password) are sent in every request inside the `Authorization: Basic <base64-encoded-string>` header.
* **Form-Based Login (`formLogin()`):** An interactive browser flow where unauthenticated users are redirected to an HTML login form (`/login`), submit their credentials via `POST`, receive a session cookie (`JSESSIONID`), and navigate within an authenticated HTTP session.

---

## 2. Why It Exists

* **HTTP Basic:** Ideal for simple server-to-server calls, testing tools (cURL, Postman), and developer tooling where interactive forms are unnecessary.
* **Form Login:** The standard pattern for server-side rendered web applications (Thymeleaf, JSP) where users need human-friendly web login pages, error notifications, and automatic post-login redirection.

---

## 3. Internal Working

### Internal Mechanics of Form Login
When form login is enabled:
1. `DefaultLoginPageGeneratingFilter` generates a default HTML login page at `/login` (unless a custom login page is configured).
2. The user submits a `POST /login` with parameters `username` and `password`.
3. **`UsernamePasswordAuthenticationFilter`** intercepts this request.
4. It extracts `username` and `password` from the request body.
5. It instantiates an unauthenticated `UsernamePasswordAuthenticationToken` and calls `AuthenticationManager.authenticate()`.
6. **On Success:** 
   * It stores the authenticated token in `SecurityContextHolder`.
   * It persists the context into the user's `HttpSession` via `SecurityContextRepository`.
   * It triggers the **`AuthenticationSuccessHandler`** (by default, redirecting the user to the URL they originally requested via `SavedRequestAwareAuthenticationSuccessHandler`).
7. **On Failure:**
   * It clears the context.
   * It triggers the **`AuthenticationFailureHandler`** (redirecting to `/login?error`).

### Internal Mechanics of HTTP Basic
1. Incoming request reaches **`BasicAuthenticationFilter`**.
2. It looks for the header: `Authorization: Basic dXNlcjpwYXNzd29yZDEyMw==`.
3. If absent, it lets the request continue. (If downstream authorization fails, `BasicAuthenticationEntryPoint` sends back a `401 Unauthorized` with `WWW-Authenticate: Basic realm="Realm"`).
4. If present, it base64-decodes the string into `user` and `password123`.
5. It calls `AuthenticationManager.authenticate()`.
6. If valid, it binds the `Authentication` to `SecurityContextHolder` for the duration of the request.

---

## 4. Architecture and Flow Diagram

### Form-Based Authentication Request Lifecycle

```mermaid
sequenceDiagram
    autonumber
    actor Browser
    participant Filter as UsernamePasswordAuthenticationFilter
    participant AM as AuthenticationManager
    participant Session as HttpSession
    participant Success as SavedRequestAwareSuccessHandler
    participant Failure as SimpleUrlAuthFailureHandler

    Browser->>Filter: POST /login (username, password)
    Note over Filter: Intercepts POST /login
    Filter->>AM: authenticate(unauthenticatedToken)
    
    alt Invalid Credentials
        AM-->>Filter: Throw BadCredentialsException
        Filter->>Failure: onAuthenticationFailure()
        Failure-->>Browser: Redirect to /login?error
    else Valid Credentials
        AM-->>Filter: Return authenticated Authentication token
        Filter->>Session: Save SecurityContext into HttpSession
        Filter->>Success: onAuthenticationSuccess()
        Success-->>Browser: Redirect to /dashboard (Set-Cookie: JSESSIONID=...)
    end
```

---

## 5. Important Classes and Interfaces

| Class / Interface | Responsibility |
|---|---|
| `UsernamePasswordAuthenticationFilter` | Filter processing form-based POST requests to `/login`. |
| `BasicAuthenticationFilter` | Filter parsing and authenticating HTTP Basic authorization headers. |
| `AuthenticationSuccessHandler` | Callback executed immediately after successful authentication. |
| `AuthenticationFailureHandler` | Callback executed when an authentication attempt fails. |
| `LogoutFilter` | Intercepts `/logout`, invalidates `HttpSession`, clears `SecurityContextHolder`, and deletes cookies. |

---

## 6. Practical Code Examples

### Custom Form Login & Handlers Configuration
```java
package com.example.security.config;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity
public class FormAndBasicSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(FormAndBasicSecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/custom-login", "/login-error", "/public/**").permitAll()
                .anyRequest().authenticated()
            )
            // Enable HTTP Basic for API testing
            .httpBasic(Customizer.withDefaults())

            // Configure Form Login with custom handlers
            .formLogin(form -> form
                .loginPage("/custom-login")
                .loginProcessingUrl("/perform-login") // Endpoint where form POSTs
                .successHandler(customSuccessHandler())
                .failureHandler(customFailureHandler())
                .permitAll()
            )

            // Configure Logout
            .logout(logout -> logout
                .logoutUrl("/perform-logout")
                .logoutSuccessUrl("/custom-login?logout")
                .deleteCookies("JSESSIONID")
                .invalidateHttpSession(true)
                .permitAll()
            );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler customSuccessHandler() {
        return (request, response, authentication) -> {
            log.info("User {} successfully logged in!", authentication.getName());
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\": \"SUCCESS\", \"user\": \"" + authentication.getName() + "\"}");
        };
    }

    @Bean
    public AuthenticationFailureHandler customFailureHandler() {
        return (request, response, exception) -> {
            log.warn("Login failed: {}", exception.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\": \"Authentication failed\", \"message\": \"" + exception.getMessage() + "\"}");
        };
    }
}
```

---

## 7. Line-by-Line Explanation

1. `.loginPage("/custom-login")`:
   * Disables Spring Security's default HTML login page and redirects unauthenticated users to your custom controller or frontend route.
2. `.loginProcessingUrl("/perform-login")`:
   * Changes the URL that `UsernamePasswordAuthenticationFilter` listens to. Your HTML login form must use `<form action="/perform-login" method="POST">`.
3. `customSuccessHandler()` / `customFailureHandler()`:
   * Replaces browser redirection with customized JSON responses (useful when building AJAX or Single Page Application login endpoints).
4. `logout.deleteCookies("JSESSIONID")`:
   * Explicitly instructs the browser to erase the session identifier cookie upon logout.

---

## 8. Real-World Use Cases

* **Server-Rendered Portals:** Enterprise internal portals with single-sign-on dashboards using session-based form login and remember-me tokens.
* **Developer Sandboxes:** Exposing test endpoints with HTTP Basic authentication for rapid integration testing without needing token refreshes.

---

## 9. Common Mistakes

1. **Forgetting to Permit Custom Login Page:** If `/custom-login` is not marked `.permitAll()`, users accessing the login page are redirected to the login page itself, triggering an infinite redirect loop (`ERR_TOO_MANY_REDIRECTS`).
2. **Sending Passwords over Plain HTTP:** Using HTTP Basic without TLS/HTTPS. Base64 is an encoding, **not encryption**. Anyone on the network can decode the authorization header into plain text.

---

## 10. Interview Questions & Detailed Answers

### Q1: Is HTTP Basic authentication secure?
**Answer:**
HTTP Basic authentication sends credentials as base64-encoded plain text (`Authorization: Basic base64(user:pass)`). It provides **zero confidentiality** on its own. It is secure **only when transmitted over HTTPS (TLS)**, which encrypts the entire HTTP transport stream including headers. Additionally, browsers cache HTTP Basic credentials and re-send them automatically on every request until the browser is closed.

### Q2: How does Spring Security remember where to redirect a user after they log in?
**Answer:**
Spring Security uses the `RequestCache` (implemented by `HttpSessionRequestCache`). When an unauthenticated user attempts to access a protected URL (e.g., `/reports`), `ExceptionTranslationFilter` captures this attempt, packages it into a `SavedRequest` object, and saves it in the `HttpSession`. After successful form login, `SavedRequestAwareAuthenticationSuccessHandler` retrieves the `SavedRequest` and redirects the user back to `/reports`.

---

## 11. Quick Revision Notes

* **HTTP Basic:** Credentials sent in header on every request; stateless unless a session is initiated.
* **Form Login:** State stored in `HttpSession`; identified by `JSESSIONID` cookie.
* **Infinite Redirect Hazard:** Always ensure `.loginPage()` and its assets are marked `.permitAll()`.
* **Handlers:** Implement `AuthenticationSuccessHandler` and `AuthenticationFailureHandler` for custom post-login behavior.
