# Module 21: Interview Preparation Guide

---

## 1. Architecture & Internal Working Questions

### Q1: Explain the full flow of an HTTP request through Spring Security.
**Answer:**
1. HTTP request arrives at Tomcat (Servlet container).
2. Tomcat's filter chain passes the request to `DelegatingFilterProxy` — a standard `jakarta.servlet.Filter` registered with the container.
3. `DelegatingFilterProxy` looks up the Spring bean named `springSecurityFilterChain` from the `ApplicationContext` — this bean is `FilterChainProxy`.
4. `FilterChainProxy` applies `StrictHttpFirewall` checks (rejects malformed URIs), then iterates its registered `SecurityFilterChain` list, selecting the first chain whose `RequestMatcher` matches the request URL.
5. The request flows sequentially through the selected chain's filters (e.g., `SecurityContextHolderFilter`, `CsrfFilter`, `UsernamePasswordAuthenticationFilter`, `ExceptionTranslationFilter`, `AuthorizationFilter`).
6. `AuthorizationFilter` evaluates the user's authorities. If access is granted, the request passes to `DispatcherServlet`.
7. `DispatcherServlet` routes to the matching `@RestController`.

---

### Q2: What is `DelegatingFilterProxy` and why is it needed?
**Answer:**
Servlet containers like Tomcat initialize filters using the Jakarta Servlet specification and are unaware of Spring's `ApplicationContext`. `DelegatingFilterProxy` is a bridge: registered as a standard Servlet filter in Tomcat, it defers the actual security work to a Spring-managed bean (`FilterChainProxy`), enabling Spring's dependency injection and lifecycle management within the filter pipeline.

---

### Q3: What is `FilterChainProxy` and how does it select a `SecurityFilterChain`?
**Answer:**
`FilterChainProxy` is the Spring bean named `springSecurityFilterChain`. It holds a `List<SecurityFilterChain>`. When a request arrives, it evaluates each chain's `RequestMatcher` sequentially. The first chain that returns `true` for the request is selected and processes the request exclusively. Priority is controlled by `@Order` on `SecurityFilterChain` beans.

---

### Q4: What are the roles of `AuthenticationManager`, `ProviderManager`, and `AuthenticationProvider`?
**Answer:**
- `AuthenticationManager` is the central interface (`authenticate(Authentication)` method).
- `ProviderManager` is the standard implementation; it delegates to a list of `AuthenticationProvider` instances.
- `AuthenticationProvider` is the actual verification engine. It declares `supports(Class)` to indicate which token types it handles and `authenticate()` to verify credentials.
- Relationship: `Filter → AuthenticationManager (ProviderManager) → AuthenticationProvider → UserDetailsService + PasswordEncoder`.

---

### Q5: What is the threading model for `SecurityContextHolder`?
**Answer:**
Default strategy is `MODE_THREADLOCAL`: the `SecurityContext` is stored in a `ThreadLocal` and is scoped to the current executing thread. Because Tomcat uses a thread pool, the context must be explicitly cleared (`SecurityContextHolder.clearContext()`) at the end of every request to prevent identity leakage across recycled threads. Asynchronous operations require `DelegatingSecurityContextAsyncTaskExecutor` to propagate context to worker threads.

---

## 2. Authentication-Specific Questions

### Q6: What is the difference between `loadUserByUsername` returning `null` vs throwing `UsernameNotFoundException`?
**Answer:**
Returning `null` from `loadUserByUsername()` violates the contract of `UserDetailsService` and causes a `NullPointerException` inside `DaoAuthenticationProvider`. Always throw `UsernameNotFoundException` for absent users. The provider then converts this to `BadCredentialsException` (unless `hideUserNotFoundExceptions = false`) to prevent username enumeration.

### Q7: Why does `DaoAuthenticationProvider` run `PasswordEncoder.matches()` even when the username is not found?
**Answer:**
To prevent timing attacks. If the method returns immediately when a username is absent, attackers can measure faster response times to deduce which usernames exist in the database. By always executing the (expensive) password hashing comparison, response times become consistent regardless of whether the username is valid.

### Q8: What are the four account status flags in `UserDetails`?
**Answer:**

| Method | Throws if `false` |
|---|---|
| `isEnabled()` | `DisabledException` |
| `isAccountNonLocked()` | `LockedException` |
| `isAccountNonExpired()` | `AccountExpiredException` |
| `isCredentialsNonExpired()` | `CredentialsExpiredException` |

---

## 3. Authorization-Specific Questions

### Q9: What is the difference between `hasRole("ADMIN")` and `hasAuthority("ROLE_ADMIN")`?
**Answer:**
Functionally equivalent. `hasRole("ADMIN")` automatically prepends `ROLE_`, making it equivalent to `hasAuthority("ROLE_ADMIN")`. The key mistake is calling `hasRole("ROLE_ADMIN")` which checks for `"ROLE_ROLE_ADMIN"`.

### Q10: What is `RoleHierarchy` and what problem does it solve?
**Answer:**
Without role hierarchy, granting access to `ROLE_USER` on an endpoint forces listing all higher roles too: `.hasAnyRole("USER", "MANAGER", "ADMIN")`. `RoleHierarchyImpl` precomputes a directed graph of inheritance relationships (e.g., `ADMIN > MANAGER > USER`). When `AuthorizationFilter` evaluates `hasRole("USER")`, it expands the user's granted authorities to include all reachable lower roles, eliminating boilerplate.

---

## 4. JWT-Specific Questions

### Q11: How does a Resource Server validate a JWT without contacting the Authorization Server on every request?
**Answer:**
The Authorization Server publishes its RSA/EC public signing keys at a JWKS URI. The Resource Server downloads and caches these keys at startup. For each incoming JWT, it verifies the signature locally using the cached public key — no network call is needed. Keys are periodically refreshed in the background.

### Q12: How can you revoke a stateless JWT before its expiration?
**Answer:**
Three strategies: (1) **Short expiry** — keep access tokens at 5–15 minutes; (2) **Redis blacklist** — on logout, store the JWT `jti` claim in Redis with TTL = remaining token lifetime; (3) **Token versioning** — embed a `token_version` claim in the JWT and increment it in the database on logout/password change, rejecting tokens with stale versions.

---

## 5. CSRF & CORS Questions

### Q13: Why is CSRF protection safe to disable for stateless JWT REST APIs?
**Answer:**
CSRF exploits the browser's automatic attachment of ambient cookies to cross-origin requests. Stateless APIs authenticated via `Authorization: Bearer <token>` headers are immune because browsers never automatically attach custom headers across origins. JavaScript on `evil.com` cannot read a JWT stored in `app.com`'s memory or attach the authorization header to cross-origin requests.

### Q14: Why must CORS be configured in Spring Security and not just in `WebMvcConfigurer`?
**Answer:**
Spring Security filters execute before `DispatcherServlet`. Browser preflight `OPTIONS` requests contain no credentials, so security filters reject them with a 401/403 before `DispatcherServlet` processes the `WebMvcConfigurer` CORS rules. Configuring `http.cors(...)` places `CorsFilter` early in the security chain, ensuring preflight requests are handled before authentication checks.

---

## 6. Exception Handling Questions

### Q15: Why can't `@ControllerAdvice` catch `AccessDeniedException` from `AuthorizationFilter`?
**Answer:**
`@ControllerAdvice` intercepts exceptions thrown inside `DispatcherServlet`. Security filters run before `DispatcherServlet`. If `AuthorizationFilter` rejects a request, the exception is caught by `ExceptionTranslationFilter` within the security filter chain itself — `DispatcherServlet` is never entered, so `@ControllerAdvice` never executes. Custom 401/403 responses require `AuthenticationEntryPoint` and `AccessDeniedHandler`.

---

## 7. Method Security Questions

### Q16: Why does `@PreAuthorize` have no effect when a method calls another method in the same class?
**Answer:**
Spring method security operates via AOP proxies. External callers invoke the proxy, which intercepts and evaluates the annotation. Internal calls (`this.method()`) bypass the proxy entirely — they are direct JVM method calls to `this` reference. Solution: inject the bean into itself via `ApplicationContext`, use a separate service class, or use AspectJ weaving instead of Spring proxies.

---

## 8. Session Questions

### Q17: What is the difference between `SessionCreationPolicy.NEVER` and `STATELESS`?
**Answer:**
- `NEVER`: Spring Security won't create sessions, but will read and use an existing session created by another component.
- `STATELESS`: Spring Security will never create or use any `HttpSession`. Every request must carry complete credentials. Required for JWT and OAuth2 Resource Server configurations.

---

## 9. Quick-Fire Glossary Reference

| Term | Definition |
|---|---|
| `Authentication` | Token representing identity: principal, credentials, authorities. |
| `GrantedAuthority` | A single permission/role string stored in `Authentication`. |
| `SecurityContext` | Thread-scoped holder for the current `Authentication`. |
| `SecurityContextHolder` | Static utility accessing the thread's `SecurityContext`. |
| `UserDetails` | Interface representing a loaded user (username, password, flags, authorities). |
| `UserDetailsService` | Fetches `UserDetails` by username. |
| `DaoAuthenticationProvider` | Combines `UserDetailsService` + `PasswordEncoder` for validation. |
| `ProviderManager` | Iterates `AuthenticationProvider` list; standard `AuthenticationManager` impl. |
| `FilterChainProxy` | Spring's master security filter bean (`springSecurityFilterChain`). |
| `DelegatingFilterProxy` | Bridge from Tomcat filter chain to Spring `FilterChainProxy`. |
| `SecurityFilterChain` | An ordered list of filters + a request matcher. |
| `AuthorizationFilter` | Final filter evaluating URL-level access rules. |
| `ExceptionTranslationFilter` | Converts security exceptions to 401/403 HTTP responses. |
| `AuthenticationEntryPoint` | Handles 401 — challenges unauthenticated callers. |
| `AccessDeniedHandler` | Handles 403 — responds to authenticated callers lacking authority. |
