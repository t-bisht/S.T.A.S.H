package org.tb.stash.admin.catalog.exception;

/** The target subcategory id for an item update is not a real subcategory. → 400. */
public class InvalidSubcategoryException extends RuntimeException {
    public InvalidSubcategoryException(String message) {
        super(message);
    }
}
