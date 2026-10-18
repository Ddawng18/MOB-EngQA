package com.example.engqa.data;

/**
 * A value together with the Firestore metadata flag that says whether it came from the offline
 * cache. Screens use {@link #fromCache} to show the "viewing saved data" hint.
 */
public final class Snapshot<T> {

    private final T data;
    private final boolean fromCache;

    public Snapshot(T data, boolean fromCache) {
        this.data = data;
        this.fromCache = fromCache;
    }

    public T getData() {
        return data;
    }

    public boolean isFromCache() {
        return fromCache;
    }
}
