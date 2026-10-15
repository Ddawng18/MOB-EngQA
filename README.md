# EngQA

EngQA is an English-language discussion forum (a Reddit-style Q&A app) where learners post
questions, the community answers them, and answers can be voted on and accepted. This repository
contains the native Android client.

## Tech stack

| Layer      | Choice                                                                 |
|------------|------------------------------------------------------------------------|
| Language   | Java 11                                                                |
| UI         | XML layouts, `RecyclerView`, `ConstraintLayout`, Material 3            |
| Architecture | `model` / `data` (Repository) / `viewmodel` (ViewModel + LiveData) / `ui` |
| Backend    | Firebase Auth + Cloud Firestore + Cloud Storage                        |
| Media      | Glide (images), `MediaPlayer` (audio playback), `MediaRecorder` (record) |
| Build      | Gradle (Kotlin DSL), `compileSdk 37`, `minSdk 29`                       |
| Theme      | Dark-only (`Theme.Material3.Dark.NoActionBar`)                         |

## Package layout

```
com.example.engqa
├── model      POJOs mapped to Firestore documents (User, Question, Answer, Vote,
│              Center, Course, CenterPost, Report, AppNotification, Category, UiState)
├── data       The only layer that talks to Firebase (UserRepository, QuestionRepository,
│              AnswerRepository, VoteRepository, StorageRepository, CategoryRepository,
│              CenterRepository, NotificationRepository, ReportRepository, ...)
├── viewmodel  Screen state, LiveData and listener lifecycle (AuthViewModel, HomeViewModel,
│              CreateQuestionViewModel, QuestionDetailViewModel, ProfileViewModel,
│              CenterViewModel, NotificationViewModel)
├── ui         RecyclerView adapters, the shared StateView and AudioPlayerView
├── media      ImageUtils, AudioPlayerManager, AudioRecorder
├── notification NotificationHelper (channel) and NotificationWatcher
├── util       Validators, TextUtils2 (search keywords), TimeUtils, PermissionHelper
└── *.java     Activities (one per screen) plus BaseNavActivity, SessionManager, NetworkUtils
```

The rule of thumb: **Activities never touch the Firebase SDK.** They observe `LiveData<UiState<T>>`
from a ViewModel and forward user input back through it. A screen renders exactly one of four
states — Loading, Success, Empty, Error(+Retry) — through the shared `StateView`.

## Firebase setup

> Step-by-step checklist for this project (`accountdata-dd29b`), including Firestore creation with
> the right region, rules/index deployment, seeding and acceptance checks:
> **[docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md)**.

1. Create a Firebase project and add an Android app with package name `com.example.engqa`.
2. Drop the generated `google-services.json` into `app/`.
3. Enable **Email/Password** sign-in, **Cloud Firestore** and **Cloud Storage**.
4. Deploy the security rules and indexes in this repository:

   ```bash
   firebase deploy --only firestore:rules,firestore:indexes,storage
   ```

5. Seed categories, demo accounts and sample content from the JSON datasets in
   `tools/seed/data/`. Two writers produce the same data — pick by credential:

   ```bash
   cd tools/seed

   # A) no key needed: reuses the `firebase login` session (Firestore REST + Identity Toolkit)
   node seed-rest.js --dry-run
   node seed-rest.js
   node seed-rest.js --no-auth   # Firestore only, when identitytoolkit is unreachable

   # B) Admin SDK, needs a service-account key
   npm install
   set GOOGLE_APPLICATION_CREDENTIALS=C:\path\to\serviceAccount.json
   npm run seed:dry
   npm run seed
   ```

   The datasets create 9 categories, 7 demo accounts (password `Engqa@1234`), 8 questions with
   answers and votes, and 2 centres. Every seeded document is flagged `isSample: true`. See
   `tools/seed/README.md` for the full data map.

6. To make an account an admin, create a document `admins/{uid}` in the Firebase Console. Client
   code can never write to `admins`, so admin status cannot be self-granted.

## Data model

A **question** is authored by one user and belongs to one category; it fans out to many **answers**.
Answers carry **votes** (one document per user per answer, so a user can only ever hold a single
vote) and the question owner can accept one answer. Denormalised counters (`answerCount`, `score`,
`upCount`, `downCount`) are updated inside transactions/batches so lists never need to join.

Regarding **search**: Firestore has no full-text search, so each question stores a
`searchKeywords` array (lower-case, accent-free, ≥ 2 character tokens) that is queried with
`array-contains`. The same tokenizer is used by the app (`TextUtils2`) and the seed script.

### ERD

```mermaid
erDiagram
    USER ||--o{ QUESTION      : authors
    USER ||--o{ ANSWER        : writes
    USER ||--o{ VOTE          : casts
    USER ||--o{ NOTIFICATION  : receives
    USER ||--o{ REPORT        : files
    USER ||--o{ CENTER        : manages

    TOPIC    ||--o{ QUESTION  : categorizes
    QUESTION ||--o{ ANSWER    : has
    QUESTION ||--o{ REPORT    : "reported as"
    ANSWER   ||--o{ VOTE      : "voted on"
    CENTER   ||--o{ COURSE    : offers
    CENTER   ||--o{ POST      : publishes

    USER {
        string   uid PK
        string   username
        string   email
        string   level "Beginner | Intermediate | Advanced"
        string   avatarUrl
        int      questionCount
        int      answerCount
        int      acceptedCount
        datetime createdAt
    }

    TOPIC {
        string   slug PK
        string   name
        int      order
    }

    QUESTION {
        string   id PK
        string   authorId FK
        string   authorName
        string   title
        string   body
        string   categoryId FK
        string   categoryName
        array    tags
        string   level
        string   type
        array    imageUrls
        string   audioUrl
        string   passageText
        array    searchKeywords
        int      answerCount
        int      score
        bool     isSolved
        string   acceptedAnswerId
        datetime createdAt
        datetime updatedAt
    }

    ANSWER {
        string   id PK
        string   questionId FK
        string   authorId FK
        string   authorName
        string   body
        int      upCount
        int      downCount
        bool     isAccepted
        datetime createdAt
    }

    VOTE {
        string   uid PK "document id = voter uid"
        int      value "-1 | +1"
    }

    CENTER {
        string   id PK
        string   name
        string   logoUrl
        string   description
        string   address
        string   phone
        string   email
        array    managerUids
        string   status "pending | approved"
        datetime createdAt
    }

    REPORT {
        string   id PK
        string   targetPath
        string   reporterId FK
        string   reason
        string   status "pending | resolved"
        datetime createdAt
    }

    NOTIFICATION {
        string   id PK
        string   recipientId FK
        string   actorId FK
        string   type "answer | vote | accepted"
        string   questionId FK
        bool     read
        datetime createdAt
    }
```

Firestore collection paths:

```
users/{uid}
categories/{slug}
questions/{qid}
questions/{qid}/answers/{aid}
questions/{qid}/answers/{aid}/votes/{uid}
notifications/{id}
reports/{id}
centers/{cid}
centers/{cid}/courses/{id}
centers/{cid}/posts/{id}
admins/{uid}
```

## Authentication

Email/password sign-up and sign-in are fully implemented: unique display names, per-field
validation, password visibility toggles, loading states, specific Vietnamese error messages,
password reset, email verification (non-blocking, with resend), change-password and logout.
Registration keeps the Auth account and the `users/{uid}` profile in sync — if the profile write
fails the Auth account is rolled back, so no half-created login is left behind.

See **[docs/AUTH.md](docs/AUTH.md)** for the flow diagrams, validation rules, error-code mapping
and the manual test checklist. Auth depends on `firestore.rules`; see
[docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md) to deploy it.

## Screens

| Screen                     | Layout                        | Activity                  |
|----------------------------|-------------------------------|---------------------------|
| Login / register / level   | `activity_login.xml`, `activity_register.xml`, `activity_choose_level.xml` | `LoginActivity`, `RegisterActivity`, `ChooseLevelActivity` |
| Home / question feed       | `activity_home.xml` + `layout-land/` | `HomeActivity`     |
| Ask a question             | `activity_main.xml` + `layout-land/` | `MainActivity`     |
| Question detail            | `activity_question_detail.xml`| `QuestionDetailActivity`  |
| Full-screen image          | `activity_image_viewer.xml`   | `ImageViewerActivity`     |
| Profile                    | `activity_menu_screen.xml`    | `MenuScreenActivity`      |
| Edit profile               | `activity_edit_profile.xml`   | `EditProfileActivity`     |
| Notifications              | `activity_notification.xml`   | `NotificationActivity`    |
| Centres                    | `activity_center_list.xml`, `activity_center_detail.xml`, `activity_center_edit.xml`, `activity_center_request.xml` | `CenterListActivity`, `CenterDetailActivity`, `CenterEditActivity`, `CenterRequestActivity` |
| Can't connect to the server| `activity_server_error.xml`   | `ServerErrorActivity`     |

The bottom navigation bar is shared (`view_bottom_nav.xml`, `BaseNavActivity`) and has three tabs:
Home, Ask, Profile.

## Rotation and state

Every screen keeps its state in a `ViewModel`, so a configuration change does not reload data or
create a duplicate Firestore listener (the listener is attached in `onStart` and detached in
`onStop`, and `onCleared` guarantees cleanup). The draft on the "Đặt câu hỏi" screen is kept in its
ViewModel and mirrored into `onSaveInstanceState` so it also survives process death.

## Build and test

```bash
./gradlew assembleDebug      # build the APK
./gradlew installDebug       # install on a connected device
./gradlew testDebugUnitTest  # run the JVM unit tests
```

Unit tests cover the pure-Java helpers: `TextUtils2` (accent-free tokenizer and search keywords),
`Validators` (email/password/title and media limits) and `TimeUtils` (relative-time labels).

## Security rules

`firestore.rules` and `storage.rules` are deployed from this repository. Highlights:

- A question may only be created with `authorId == request.auth.uid`.
- An answer starts with zero counters and `isAccepted == false`; only the question owner may accept.
- Votes are one document per user per answer (`votes/{uid}`) with `value` in `{+1, -1}`.
- Notifications can only be created for someone else and read only by their recipient.
- `admins` is read-only from clients; admins are added from the Console.

## Known limitations

- **Notifications** are delivered by a Firestore listener (`NotificationWatcher`) while the app
  process is alive. A fully reliable push (background/killed app) needs Firebase Cloud Messaging,
  which is out of scope for this phase.
- **Vote integrity** is enforced by rules only at a coarse level (document id + allowed values).
  A production build would move the counter updates into a Cloud Function or add `getAfter()`
  delta checks.
- **Search** matches a single normalised keyword (`array-contains`), not arbitrary substrings.
- Cloud Storage cleanup is not automated when a question is deleted; orphaned objects require a
  manual sweep or a scheduled Function.
