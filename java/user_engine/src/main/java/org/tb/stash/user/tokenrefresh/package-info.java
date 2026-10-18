/**
 * Keeps stored Google access tokens valid between logins.
 *
 * <ul>
 *   <li>{@code service.TokenRefreshScheduler} runs {@code TokenRefreshSweeper.sweep()} every {@code
 *       token-refresh.interval}, refreshing tokens that expire within {@code
 *       token-refresh.refresh-window}.
 *   <li>{@code TokenRefreshService.refreshForUser} refreshes on demand for callers that saw a Gmail
 *       401.
 *   <li>Both delegate the single Hiemdall round-trip and its persistence to {@code TokenExchanger};
 *       callers own locking ({@code AccountLocks}) and the outcome metric.
 *   <li>A dead refresh token (Hiemdall {@code reauth_required}) forces a logout: the account is
 *       flagged {@code login_required} and every session of the user is deleted. The next login
 *       clears the flag.
 * </ul>
 *
 * <p>Hiemdall calls Google; user_engine never does. Hiemdall calls never run inside a DB
 * transaction; writes go through {@code crud.TokenRefreshPersistence}.
 *
 * <p>Depends on {@code hiemdall}, {@code crypto}, {@code core}, {@code config}.
 */
package org.tb.stash.user.tokenrefresh;
