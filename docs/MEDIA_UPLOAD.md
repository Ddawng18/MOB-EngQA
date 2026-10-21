# Media Upload Architecture & Rollback Specification

## Overview

This module provides accurate MIME detection, Storage Security Rules compliance, and atomic transaction-like rollback for media uploads (images and audio) when creating questions in EngQA.

## Architecture & Components

1. `data/UploadedMedia.java`: POJO encapsulating uploaded file metadata (download URL, Storage path, MIME type, size in bytes, and media type flag).
2. `data/MediaUploadSession.java`: Tracks all uploaded files during a question creation session and performs clean rollback by calling `StorageReference.delete()` for each file if any error occurs.
3. `data/MediaStorageRepository.java`: Responsible for MIME type determination, size validation, proper extension mapping, setting `StorageMetadata.contentType`, and uploading files to Cloud Storage.
4. `viewmodel/CreateQuestionMediaViewModel.java`: Manages screen state, validates user input, orchestrates sequential image and audio uploads via `MediaStorageRepository`, and creates the Firestore question document. If an error occurs at any point, it triggers `MediaUploadSession.rollback()`.

## Storage Rules & MIME Mapping

Cloud Storage security rules enforce:
- Images: `request.resource.contentType.matches('image/(jpeg|jpg|png)')` up to 5 MB.
- Audio: `request.resource.contentType.matches('audio/.*')` up to 10 MB.

### MIME Normalization & Extension Mapping:
- `image/jpg` is normalized to `image/jpeg` with `.jpg` extension.
- `image/png` maps to `.png` extension.
- `audio/mpeg` / `audio/mp3` maps to `.mp3` extension.
- `audio/mp4` / `audio/m4a` / `audio/aac` / `audio/3gpp` maps to `.m4a` extension.
- `audio/wav` / `audio/x-wav` maps to `.wav` extension.
- For recorded audio files with `file://` URIs where ContentResolver returns no MIME, default MIME is `audio/mp4` with extension `.m4a`.

## Error Handling & Rollback Flow

```
[User Clicks Submit]
        |
        v
[Validate Title & Body] ---> (Invalid) ---> [Show Error Toast]
        | (Valid)
        v
[Create MediaUploadSession & Generate questionId]
        |
        v
[Upload Images Sequentially] ---> (Error) ---> [session.rollback()] ---> [Show Error Toast]
        | (All Images Success)
        v
[Upload Audio] ---> (Error) ---> [session.rollback()] ---> [Show Error Toast]
        | (Audio Success or None)
        v
[Create Firestore Question] ---> (Error) ---> [session.rollback()] ---> [Show Error Toast]
        | (Success)
        v
[Navigate to Question Detail]
```

## Code Example (No Comments)

```java
public void submit(String uid, String authorName) {
    if (isSubmitting) {
        return;
    }
    if (!Validators.isValidTitle(title)) {
        submitState.setValue(UiState.error("Tiêu đề cần ít nhất 10 ký tự"));
        return;
    }
    if (body == null || body.trim().isEmpty()) {
        submitState.setValue(UiState.error("Vui lòng nhập mô tả chi tiết"));
        return;
    }
    isSubmitting = true;
    submitState.setValue(UiState.loading());
    uploadProgress.setValue(0);

    final String questionId = questionRepository.newQuestionId();
    final MediaUploadSession session = new MediaUploadSession();
    final int totalAttachments = imageUris.size() + (audioUri != null ? 1 : 0);

    if (totalAttachments == 0) {
        createQuestionInFirestore(uid, authorName, questionId, session);
        return;
    }

    uploadImagesSequentially(uid, authorName, questionId, session, 0, totalAttachments);
}
```
