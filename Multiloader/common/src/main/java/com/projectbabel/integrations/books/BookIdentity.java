package com.projectbabel.integrations.books;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Stable identity for a translated book/guide scope.
 * namespace/path are used for resource filtering; id is used for cache/preload state keys.
 */
public record BookIdentity(String namespace, String path, String id) {
    public BookIdentity {
        namespace = namespace == null ? "" : namespace;
        path = path == null ? "" : path;
        id = id == null ? namespace + ':' + path : id;
    }

    public Set<String> pathCandidates() {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addCandidate(candidates, path);

        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < path.length()) {
            addCandidate(candidates, path.substring(slash + 1));
        }

        return candidates;
    }

    private static void addCandidate(Set<String> candidates, String value) {
        if (value == null || value.isBlank()) return;
        candidates.add(value);
    }
}
