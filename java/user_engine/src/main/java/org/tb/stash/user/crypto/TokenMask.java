package org.tb.stash.user.crypto;

/**
 * Renders secrets for {@code toString()} / logs without revealing them.
 *
 * <p>Shows only presence and length — never any character of the token — so a masked value is safe
 * in every log level.
 */
public final class TokenMask {

    private TokenMask() {}

    /**
     * Masks {@code secret}.
     *
     * @param secret token or other credential; may be {@code null}
     * @return {@code "null"}, {@code "<empty>"}, or {@code "****(len=N)"}
     */
    public static String mask(String secret) {
        if (secret == null) {
            return "null";
        }
        if (secret.isEmpty()) {
            return "<empty>";
        }
        return "****(len=" + secret.length() + ")";
    }
}
