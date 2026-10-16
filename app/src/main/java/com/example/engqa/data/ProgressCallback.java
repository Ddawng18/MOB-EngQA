package com.example.engqa.data;

/** Upload progress callback (0..100) shared by the storage repository. */
public interface ProgressCallback {

    void onProgress(int percent);
}
