package org.tb.stash.admin.catalog.domain;

/** A category id + name — the echo returned after a rename. */
public record CategoryRef(int categoryId, String categoryName) {}
