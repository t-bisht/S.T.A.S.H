package org.tb.stash.admin.catalog.exception;

/** A category or subcategory name collides with an existing one (both names are UNIQUE). → 409. */
public class DuplicateNameException extends RuntimeException {
    public DuplicateNameException(String message) {
        super(message);
    }
}
