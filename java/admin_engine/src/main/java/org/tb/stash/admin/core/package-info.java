/**
 * Cross-feature shared pieces: the flat {@code {code, message}} error body, the global
 * {@code @RestControllerAdvice} that maps admin exceptions to it, and the SPA deep-link fallback
 * controller.
 *
 * <p>Mirrors user_engine's {@code core} + {@code login/exception} split.
 */
package org.tb.stash.admin.core;
