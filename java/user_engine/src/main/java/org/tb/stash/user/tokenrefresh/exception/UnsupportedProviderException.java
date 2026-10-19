package org.tb.stash.user.tokenrefresh.exception;

/**
 * The {@code {provider}} path segment was not {@code google}. v1 serves Google only; the segment
 * exists for forward compatibility (spec 02, E3). Mapped to 400 {@code unsupported_provider}.
 */
@SuppressWarnings("serial")
public class UnsupportedProviderException extends RuntimeException {

    private final String provider;

    public UnsupportedProviderException(String provider) {
        super("unsupported provider: " + provider);
        this.provider = provider;
    }

    /** The rejected provider value, as received on the path. */
    public String provider() {
        return provider;
    }
}
