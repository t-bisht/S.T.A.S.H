package org.tb.stash.user.hiemdall;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Verified Google-identity claims as returned to STASH by Hiemdall.
 *
 * <p>Hiemdall extracts these from the Google {@code id_token} after verifying it against Google's
 * JWKS. STASH does not re-verify (parent spec Q5); it consumes this record as given and
 * denormalises the fields into {@code users} + {@code user_oauth_accounts}.
 *
 * @param sub stable Google-side account identifier; the only field STASH treats as a provider
 *     primary key.
 * @param email latest email from the provider; mutable over time.
 * @param name display name.
 * @param picture profile picture URL.
 * @param emailVerified Google-side verification bit; informational for v1.
 */
public record IdentityClaims(
        String sub,
        String email,
        String name,
        String picture,
        @JsonProperty("email_verified") Boolean emailVerified) {}
