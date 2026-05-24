package com.projectbabel.integrations.books;

/**
 * Adapter implemented by each book/guide integration.
 * The coordinator owns concurrency/state; the adapter owns mod-specific resource scanning and text extraction.
 */
public interface BookPreloadAdapter {
    String integrationId();

    BookIdentity resolveIdentity(Object source);

    boolean runPreload(BookPreloadContext context, BookIdentity identity, String targetLanguage, Object source);
}
