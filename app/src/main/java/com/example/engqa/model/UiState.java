package com.example.engqa.model;

/**
 * Generic UI state holder shared by every screen.
 *
 * <p>Activities observe a {@code LiveData<UiState<T>>} and render exactly one of the four states:
 * LOADING, SUCCESS, EMPTY or ERROR. The optional {@code fromCache} flag lets a screen show the
 * "viewing offline data" hint when Firestore serves a snapshot from its local cache.
 */
public final class UiState<T> {

    public enum Status {
        LOADING, SUCCESS, EMPTY, ERROR
    }

    private final Status status;
    private final T data;
    private final String message;
    private final boolean fromCache;

    private UiState(Status status, T data, String message, boolean fromCache) {
        this.status = status;
        this.data = data;
        this.message = message;
        this.fromCache = fromCache;
    }

    public static <T> UiState<T> loading() {
        return new UiState<>(Status.LOADING, null, null, false);
    }

    public static <T> UiState<T> success(T data) {
        return new UiState<>(Status.SUCCESS, data, null, false);
    }

    public static <T> UiState<T> success(T data, boolean fromCache) {
        return new UiState<>(Status.SUCCESS, data, null, fromCache);
    }

    public static <T> UiState<T> empty() {
        return new UiState<>(Status.EMPTY, null, null, false);
    }

    public static <T> UiState<T> error(String message) {
        return new UiState<>(Status.ERROR, null, message, false);
    }

    public Status getStatus() {
        return status;
    }

    public T getData() {
        return data;
    }

    public String getMessage() {
        return message;
    }

    public boolean isFromCache() {
        return fromCache;
    }

    public boolean isLoading() {
        return status == Status.LOADING;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
