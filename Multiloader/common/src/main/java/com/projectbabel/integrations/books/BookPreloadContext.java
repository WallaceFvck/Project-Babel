package com.projectbabel.integrations.books;

/** Context passed to a specific book integration while the shared coordinator runs it. */
public final class BookPreloadContext {
    private final String integrationId;
    private final String preloadKey;
    private final long generation;
    private final BookPreloadCoordinator.StateHandle state;

    BookPreloadContext(String integrationId, String preloadKey, long generation, BookPreloadCoordinator.StateHandle state) {
        this.integrationId = integrationId;
        this.preloadKey = preloadKey;
        this.generation = generation;
        this.state = state;
    }

    public String integrationId() {
        return integrationId;
    }

    public String preloadKey() {
        return preloadKey;
    }

    public long generation() {
        return generation;
    }

    public boolean isCurrentGeneration() {
        return state.isCurrentGeneration(generation);
    }

    public void markReady() {
        state.markReady(preloadKey, generation);
    }
}
