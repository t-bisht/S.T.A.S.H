package org.tb.stash.user.login.domain;

import org.tb.stash.user.core.domain.User;

/** Outcome of an identity upsert call. */
public record UpsertResult(User user, boolean isNewUser) {}
