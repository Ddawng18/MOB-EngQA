# Media storage: Firestore-only (no Blaze billing)

## Why

Cloud Storage for Firebase requires the **Blaze** plan. This project keeps everything on the free
Spark plan, so images and audio are compressed and stored **directly in Firestore** as Base64
documents. The app, the security rules and the offline worker all use this path.

## Data layout

```
questions/{qid}
  title, body, ...            ← feed document stays light
  thumbBase64                 ← ≤ ~40 KB thumbnail (first image) for the feed card
  imageCount                  ← number of images (badge "+N")
  hasAudio                    ← shows the audio icon without an extra read
  imageUrls / audioUrl        ← legacy fields for pre-migration posts

questions/{qid}/media/{mid}
  type: "image" | "audio"
  mime: "image/jpeg" | "audio/m4a" | ...
  order: 0..N
  data: Base64 payload
  sizeBytes
  createdAt
```

The full media is **not** in the feed document: the feed reads only `thumbBase64`, while the
detail screen loads the `media` documents on demand.

## Size budget (hard limits)

| Limit | Value | Reason |
|---|---|---|
| Firestore document | 1 MiB | Firestore hard limit |
| Base64 overhead | +33% | Encoding cost |
| `MAX_BASE64_CHARS` | **600 000** chars | safety margin under 1 MiB |
| `MAX_BINARY_BYTES` | **450 000** bytes | maps to 600 000 Base64 chars |
| Images | **max 5** per question | media docs / reads |
| Image size | 1024 px longest side, JPEG 65 | auto-retried at 1024/55, 800/60, 800/50, 640/55 until it fits |
| Thumbnail | 320 px, JPEG 60 | ~15–40 KB |
| Audio | AAC mono **24 kbps**, 22.05 kHz, **120 s** | ~360 KB, under the budget |

Selected audio files larger than 450 KB are rejected with a clear message; recording uses the
low-bitrate encoder and stops automatically after 120 seconds.

## Flow

```
MainActivity (pick/capture image, record/pick audio)
  └─ CreateQuestionViewModel
       ├─ online  → QuestionPublisher
       │             1. MediaCompressor: compress images + thumbnail, read audio
       │             2. create questions/{qid} (with thumbBase64/imageCount/hasAudio)
       │             3. batch-write questions/{qid}/media/*
       │             4. rollback the question if the media batch fails
       └─ offline → PendingDraftStore (copy files + draft.json) → WorkManager
                     └─ PendingQuestionWorker uses the same QuestionPublisher
```

Reading:

- Feed: `ThumbnailLoader` (LruCache) decodes `thumbBase64`.
- Detail: `FirestoreMediaRepository.observeMedia(qid)` → `MediaImageAdapter` (Bitmap from Base64)
  and `MediaCompressor.writeToCache` for audio (MediaPlayer plays the cache file).
- Full-screen image: `ImageViewerActivity` loads the media document by id.

## Security rules

`questions/{qid}/media/{mid}` in `firestore.rules`:

- read: any signed-in user;
- create/update: only the question author (or an admin), `data` is a string with
  `size() <= 600000`, `mime` in the allow-list;
- delete: author/admin.

Verified against the live project: **author create 200**, **non-author 403**, **600 001-char
payload 403**.

## Trade-offs (document these in the report)

- Firestore Spark gives **1 GiB** storage; compressed images are small, but many audio clips will
  fill it. Audio is limited to ~2 minutes.
- No encryption is applied: Base64/encryption would inflate the payload without adding real
  protection (Firebase already encrypts at rest and in transit). If privacy is required, encrypt
  **after** compression and manage keys on a server (KMS/Cloud Function), which needs Blaze.
- Media reads are charged per document; the feed never downloads full media.
- EXIF orientation is not corrected; very tall/rotated photos may appear sideways.

## Switching to Firebase Storage later

1. Enable Blaze and Firebase Storage.
2. In `CreateQuestionViewModel`, replace `QuestionPublisher` with the Firebase `StorageRepository`
   (already kept in the repo) and deploy `storage.rules`.
3. Keep `thumbBase64` for feed thumbnails or upload a resized image and store its URL.

## Manual test checklist

- [ ] Post an image (≤5 MB input) → feed shows the thumbnail, detail shows the full image.
- [ ] Add 2 images → feed shows `+1`, detail shows both.
- [ ] Record 10–30 s audio → detail plays it after reopening.
- [ ] Select an audio file > 450 KB → rejected with the budget message.
- [ ] Airplane mode → post queues; reconnect → the worker publishes, notification + feed refresh.
- [ ] Another account cannot write `questions/{qid}/media/*` (rules 403).
