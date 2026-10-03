# EngQA

EngQA is an English-language discussion forum (a Reddit-style Q&A app) where learners
post questions, the community answers them, and answers can be voted, commented on and
accepted. This repository contains the native Android client.

## Tech stack

| Layer     | Choice                                                        |
|-----------|---------------------------------------------------------------|
| Language  | Java 11                                                       |
| UI        | XML layouts, `ConstraintLayout`, `LinearLayout`, Material 3   |
| Build     | Gradle (Kotlin DSL) `compileSdk 37`, `minSdk 29`              |
| Theme     | Dark-only (`Theme.Material3.Dark.NoActionBar`)                |

## Repository setup

Clone the project into `E:/` (Windows) or any local folder:

```bash
git clone https://github.com/Ddawng18/MOB-EngQA E:/MOB-EngQA
cd E:/MOB-EngQA
```

Open the folder in Android Studio, let Gradle sync, then run the `app` configuration
on a device/emulator. From the command line:

```bash
./gradlew assembleDebug          # build the APK
./gradlew installDebug           # install on a connected device
```

## Database design (ERD)

The client is a forum, so the backend data model is built around **users**, **questions**,
**answers** and **topics**, with supporting tables for votes, comments, bookmarks,
attachments, notifications and reports. A **question** (post) is authored by one user and
belongs to one topic; it fans out to many **answers**; answers and questions can both
receive **comments** and **attachments**. **Votes** and **reports** are polymorphic
(`target_type` + `target_id`) so a single table covers questions, answers and comments.

```mermaid
erDiagram
    USER ||--o{ QUESTION      : authors
    USER ||--o{ ANSWER        : writes
    USER ||--o{ COMMENT       : writes
    USER ||--o{ VOTE          : casts
    USER ||--o{ BOOKMARK      : saves
    USER ||--o{ USER_TOPIC    : follows
    USER ||--o{ NOTIFICATION  : receives
    USER ||--o{ REPORT        : files

    TOPIC ||--o{ QUESTION    : categorizes
    TOPIC ||--o{ USER_TOPIC  : "followed by"

    QUESTION ||--o{ ANSWER      : has
    QUESTION ||--o{ COMMENT     : has
    QUESTION ||--o{ BOOKMARK    : "saved in"
    QUESTION ||--o{ ATTACHMENT  : includes

    ANSWER ||--o{ COMMENT     : has
    ANSWER ||--o{ ATTACHMENT  : includes

    COMMENT ||--o{ COMMENT    : "replies to"

    USER {
        bigint   id PK
        varchar  username "unique"
        varchar  email "unique"
        varchar  password_hash
        varchar  display_name
        varchar  avatar_url
        text     bio
        int      reputation
        varchar  role "USER | MODERATOR | ADMIN"
        boolean  is_active
        datetime created_at
        datetime updated_at
    }

    TOPIC {
        bigint   id PK
        varchar  name
        varchar  slug "unique"
        varchar  description
        varchar  icon_url
        int      question_count
        datetime created_at
    }

    QUESTION {
        bigint   id PK
        bigint   author_id FK "-> USER.id"
        bigint   topic_id FK "-> TOPIC.id"
        varchar  title
        text     body
        varchar  status "OPEN | CLOSED | DELETED"
        int      view_count
        int      answer_count
        int      score
        boolean  has_accepted_answer
        datetime created_at
        datetime updated_at
    }

    ANSWER {
        bigint   id PK
        bigint   question_id FK "-> QUESTION.id"
        bigint   author_id FK "-> USER.id"
        text     body
        boolean  is_accepted
        int      score
        datetime created_at
        datetime updated_at
    }

    COMMENT {
        bigint   id PK
        bigint   author_id FK "-> USER.id"
        bigint   question_id FK "-> QUESTION.id (nullable)"
        bigint   answer_id FK "-> ANSWER.id (nullable)"
        bigint   parent_id FK "-> COMMENT.id (nullable)"
        text     body
        datetime created_at
    }

    VOTE {
        bigint   id PK
        bigint   user_id FK "-> USER.id"
        varchar  target_type "QUESTION | ANSWER | COMMENT"
        bigint   target_id
        smallint value "-1 | +1"
        datetime created_at
    }

    BOOKMARK {
        bigint   id PK
        bigint   user_id FK "-> USER.id"
        bigint   question_id FK "-> QUESTION.id"
        datetime created_at
    }

    ATTACHMENT {
        bigint   id PK
        bigint   question_id FK "-> QUESTION.id (nullable)"
        bigint   answer_id FK "-> ANSWER.id (nullable)"
        varchar  url
        varchar  mime_type
        int      size_bytes
        datetime created_at
    }

    NOTIFICATION {
        bigint   id PK
        bigint   recipient_id FK "-> USER.id"
        bigint   actor_id FK "-> USER.id"
        varchar  type "ANSWER | COMMENT | VOTE | ACCEPT"
        bigint   question_id FK "-> QUESTION.id (nullable)"
        bigint   answer_id FK "-> ANSWER.id (nullable)"
        boolean  is_read
        datetime created_at
    }

    REPORT {
        bigint   id PK
        bigint   reporter_id FK "-> USER.id"
        varchar  target_type "QUESTION | ANSWER | COMMENT"
        bigint   target_id
        varchar  reason
        varchar  status "PENDING | RESOLVED | REJECTED"
        datetime created_at
    }

    USER_TOPIC {
        bigint   id PK
        bigint   user_id FK "-> USER.id"
        bigint   topic_id FK "-> TOPIC.id"
        datetime created_at
    }
```

### Keys and constraints

- `USER.username`, `USER.email` and `TOPIC.slug` are unique.
- `QUESTION.author_id`, `ANSWER.author_id`, `COMMENT.author_id` → `USER.id`.
- `ANSWER (question_id, author_id)` and `VOTE (user_id, target_type, target_id)` are unique
  (one vote per user per target; an author cannot vote twice).
- `COMMENT` carries exactly one of `question_id`/`answer_id` (check constraint) and an
  optional `parent_id` for threaded replies.
- `ATTACHMENT` carries exactly one of `question_id`/`answer_id` (check constraint).
- `VOTE.target_id` and `REPORT.target_id` reference the row selected by `target_type`
  (polymorphic FK, enforced in the application layer or with a trigger), so they have no
  hard foreign-key line in the diagram.

### How to embed the ERD in `README.md`

1. Paste the ` ```mermaid ... ``` ` block above directly into `README.md`.
   GitHub, GitLab and Azure DevOps render Mermaid natively, so the diagram appears
   inline with no extra tooling.
2. To keep the diagram in a separate file, save it as `docs/erd.mmd` and reference it with
   an image link that uses GitHub's Mermaid proxy, for example:

   ```markdown
   ![EngQA ERD](https://mermaid.ink/img/<base64-of-erd.mmd>)
   ```

   Generate the base64 payload with `base64 -w0 docs/erd.mmd`.
3. For a static image in local editors or PDFs, install the CLI and export:

   ```bash
   npm install -g @mermaid-js/mermaid-cli
   mmdc -i docs/erd.mmd -o docs/erd.png
   ```

   Then embed the bitmap: `![EngQA ERD](docs/erd.png)`.
4. In VS Code, the "Markdown Preview Mermaid Support" extension renders the same block
   in the preview pane, and the "Mermaid Markdown Syntax Highlighting" extension
   highlights it in the editor.

## Screens

| Screen                          | Layout                          | Activity                |
|---------------------------------|---------------------------------|-------------------------|
| Home / question feed            | `activity_home.xml`             | `HomeActivity`          |
| Ask a question / login          | `activity_main.xml`             | `MainActivity`          |
| Question detail                 | `activity_question_detail.xml`  | `QuestionDetailActivity`|
| Can't connect to the server     | `activity_server_error.xml`     | `ServerErrorActivity`   |

The "Can't connect to the server" screen is a pixel-accurate rebuild of the Figma
prototype: `#0E1113` screen background, `#1A1A1B` app bars, a 277dp illustration, the
18sp/13sp headline and error code, and the home/ask/profile navigation bar.
