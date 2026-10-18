package com.example.engqa.data;

/** Uniform async result callback used by every repository. */
public interface Callback<T> {

    void onSuccess(T result);

    void onError(Exception e);
}
