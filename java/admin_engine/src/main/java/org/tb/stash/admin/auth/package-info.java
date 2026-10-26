/**
 * Admin authentication: static credential check, opaque session cookie, and the {@code
 * /admin/api/**} session interceptor.
 *
 * <p>{@code AdminAuthService} compares credentials in constant time ({@code
 * MessageDigest.isEqual}), mints a {@code SecureRandom} token, and keeps sessions in an in-process
 * {@code ConcurrentHashMap} with an absolute TTL — the same opaque-server-side-cookie
 * <i>pattern</i> as user_engine's {@code session} package while sharing no code or state. {@code
 * SessionInterceptor} (in {@code controller/}, like user_engine's {@code SessionCookieFilter})
 * enforces the session on {@code /admin/api/**}. The auth surface never touches OAuth, {@code
 * user_oauth_accounts}, or {@code sessions} (design §2.2).
 *
 * <p>Depends on {@code config}, {@code core}.
 */
package org.tb.stash.admin.auth;
