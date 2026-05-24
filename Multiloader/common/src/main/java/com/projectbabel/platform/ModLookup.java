package com.projectbabel.platform;

/** Loader-neutral mod lookup. */
public interface ModLookup {
    boolean isLoaded(String modId);
}
