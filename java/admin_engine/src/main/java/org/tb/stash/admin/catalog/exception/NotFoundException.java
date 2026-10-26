package org.tb.stash.admin.catalog.exception;

/** A referenced row (category or catalog item) does not exist. → 404. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
