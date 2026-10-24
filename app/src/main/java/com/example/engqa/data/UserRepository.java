package com.example.engqa.data;

import android.util.Log;

import com.example.engqa.model.User;
import com.example.engqa.util.Validators;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Transaction;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The single access point for Firebase Auth and the {@code users/{uid}} profile document.
 *
 * <p>Registration and profile updates keep the Auth account and the Firestore document in sync.
 * Display names are unique via an atomic {@code usernames/{displayName}} reservation, not a
 * collection query over private {@code users} documents (Firestore rules cannot authorize a query
 * that is only self-readable). If Auth succeeds but the profile transaction fails, the Auth account
 * is kept for recovery by the next sign-in; it is only removed when the chosen username was taken,
 * so the email can be used again.
 */
public class UserRepository {

    private static final String TAG = "UserRepository";

    /** Thrown when a display name is already used by another profile. */
    public static final class UsernameTakenException extends RuntimeException {
        public UsernameTakenException() {
            super("Tên hiển thị này đã được sử dụng");
        }
    }

    /** Auth succeeded but the Firestore profile could not be completed yet. */
    public static final class ProfileSetupException extends Exception {
        public ProfileSetupException(String message) {
            super(message);
        }
    }

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    // ------------------------------------------------------------------ helpers

    public static String normalizeEmail(String email) {
        return email != null ? email.trim().toLowerCase(Locale.ROOT) : "";
    }

    public static String defaultUsername(String email) {
        String normalized = normalizeEmail(email);
        int at = normalized.indexOf('@');
        String raw = at > 0 ? normalized.substring(0, at) : normalized;
        raw = raw.replaceAll("[^a-z0-9._-]", "");
        if (raw.length() < 3) {
            raw = (raw + "user").substring(0, Math.max(3, raw.length() + 4));
        }
        return raw.length() > 24 ? raw.substring(0, 24) : raw;
    }

    public FirebaseUser currentUser() {
        return auth.getCurrentUser();
    }

    public String currentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    public boolean isEmailVerified() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null && user.isEmailVerified();
    }

    public void logout() {
        auth.signOut();
    }

    public void addAuthStateListener(FirebaseAuth.AuthStateListener listener) {
        auth.addAuthStateListener(listener);
    }

    public void removeAuthStateListener(FirebaseAuth.AuthStateListener listener) {
        auth.removeAuthStateListener(listener);
    }

    // ------------------------------------------------------------------ register

    public void register(String email, String password, String username, Callback<User> callback) {
        final String normalizedEmail = normalizeEmail(email);
        final String name = username != null && !username.trim().isEmpty()
                ? username.trim() : defaultUsername(normalizedEmail);

        FirebaseUser existing = auth.getCurrentUser();
        if (existing != null && normalizedEmail.equals(normalizeEmail(existing.getEmail()))) {
            // Auth was created but a previous profile write was interrupted. Complete it instead
            // of trying (and failing) to create the same Auth user again.
            updateAuthDisplayName(existing, name);
            completeProfile(existing, name, normalizedEmail, false, false, callback);
            return;
        }
        if (existing != null) {
            // Do not mix a new registration with an unrelated signed-in account.
            auth.signOut();
        }

        auth.createUserWithEmailAndPassword(normalizedEmail, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onError(new IllegalStateException("Không tạo được tài khoản"));
                        return;
                    }
                    // Set this before Firestore so a later login can recover the chosen name if
                    // the profile transaction is interrupted.
                    updateAuthDisplayName(firebaseUser, name);
                    completeProfile(firebaseUser, name, normalizedEmail, true, false, callback);
                })
                .addOnFailureListener(e -> {
                    logSafe("register/auth", e);
                    callback.onError(e);
                });
    }

    private void completeProfile(FirebaseUser firebaseUser, String desiredName, String email,
                                 boolean rollbackOnTaken, boolean allowFallback,
                                 Callback<User> callback) {
        String name = safeUsername(desiredName, email, firebaseUser.getUid());
        attemptProfile(firebaseUser, name, email, rollbackOnTaken, allowFallback, 0, callback);
    }

    /**
     * Atomically reserves {@code usernames/{name}} and creates {@code users/{uid}}. A transaction
     * makes concurrent duplicate registrations safe; the previous implementation used a collection
     * query that Firestore rules correctly deny because profiles are self-readable only.
     */
    private void attemptProfile(FirebaseUser firebaseUser, String name, String email,
                                boolean rollbackOnTaken, boolean allowFallback, int attempt,
                                Callback<User> callback) {
        final String uid = firebaseUser.getUid();
        DocumentReference userRef = db.collection(FirestoreRefs.USERS).document(uid);
        DocumentReference nameRef = db.collection(FirestoreRefs.USERNAMES).document(name);

        db.runTransaction((Transaction.Function<ProfileResult>) transaction -> {
            DocumentSnapshot profile = transaction.get(userRef);
            if (profile.exists()) {
                User existing = toUser(profile);
                if (existing == null) {
                    throw new IllegalStateException("Hồ sơ hiện có không hợp lệ");
                }
                return new ProfileResult(existing, false, name);
            }

            DocumentSnapshot nameDoc = transaction.get(nameRef);
            if (nameDoc.exists()) {
                String owner = nameDoc.getString("uid");
                if (!uid.equals(owner)) {
                    throw new UsernameTakenException();
                }
            }

            transaction.set(userRef, profileData(name, email));
            transaction.set(nameRef, usernameData(uid, name));
            return new ProfileResult(new User(uid, name, email, null), true, name);
        }).addOnSuccessListener(result -> {
            if (result.user == null) {
                callback.onError(new IllegalStateException("Không tạo được hồ sơ"));
                return;
            }
            if (result.created) {
                updateAuthDisplayName(firebaseUser, result.username);
                // Best effort: a failure to send the verification email must not block sign-up.
                firebaseUser.sendEmailVerification();
            }
            callback.onSuccess(result.user);
        }).addOnFailureListener(e -> {
            logSafe("register/profile", e);
            if (e instanceof UsernameTakenException) {
                if (allowFallback && attempt < 3) {
                    String fallback = fallbackUsername(name, uid, attempt);
                    attemptProfile(firebaseUser, fallback, email, false, true, attempt + 1,
                            callback);
                    return;
                }
                if (rollbackOnTaken) {
                    rollbackAuthAccount(firebaseUser, callback);
                } else {
                    callback.onError(new UsernameTakenException());
                }
                return;
            }
            // Keep the Auth account: a retry (or the next login) can finish the profile.
            callback.onError(new ProfileSetupException(
                    "Tài khoản đã được tạo nhưng chưa lưu được hồ sơ. "
                            + "Vui lòng thử lại hoặc đăng nhập để hoàn tất."));
        });
    }

    private void rollbackAuthAccount(FirebaseUser firebaseUser, Callback<User> callback) {
        firebaseUser.delete().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                callback.onError(new UsernameTakenException());
                return;
            }
            logSafe("register/rollback", task.getException());
            callback.onError(new ProfileSetupException(
                    "Tên hiển thị đã được sử dụng. Tài khoản đăng nhập đã được tạo nhưng "
                            + "chưa có hồ sơ; hãy đăng nhập bằng email này để hoàn tất."));
        });
    }

    private static Map<String, Object> profileData(String name, String email) {
        Map<String, Object> data = new HashMap<>();
        data.put("username", name);
        data.put("email", email);
        data.put("level", null);
        data.put("avatarUrl", null);
        data.put("questionCount", 0);
        data.put("answerCount", 0);
        data.put("acceptedCount", 0);
        data.put("createdAt", FieldValue.serverTimestamp());
        return data;
    }

    private static Map<String, Object> usernameData(String uid, String name) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        data.put("username", name);
        data.put("createdAt", FieldValue.serverTimestamp());
        return data;
    }

    /** Google display names may contain spaces/Unicode; fall back to a valid email-based name. */
    private static String safeUsername(String desired, String email, String uid) {
        String name = desired != null ? desired.trim() : "";
        if (Validators.isValidUsername(name)) {
            return name;
        }
        name = defaultUsername(email);
        if (Validators.isValidUsername(name)) {
            return name;
        }
        String suffix = uid != null && uid.length() >= 4 ? uid.substring(0, 4) : "user";
        return "user." + suffix;
    }

    private static String fallbackUsername(String base, String uid, int attempt) {
        String uidPart = uid != null && uid.length() >= 4 ? uid.substring(0, 4) : "user";
        String suffix = (attempt == 0 ? "." : "." + attempt + ".") + uidPart;
        int maxBase = Math.max(Validators.MIN_USERNAME_LENGTH,
                Validators.MAX_USERNAME_LENGTH - suffix.length());
        String prefix = base != null ? base : "user";
        if (prefix.length() > maxBase) {
            prefix = prefix.substring(0, maxBase);
        }
        String candidate = prefix + suffix;
        if (candidate.length() > Validators.MAX_USERNAME_LENGTH) {
            candidate = candidate.substring(0, Validators.MAX_USERNAME_LENGTH);
        }
        return candidate;
    }

    private void updateAuthDisplayName(FirebaseUser firebaseUser, String name) {
        if (firebaseUser == null || name == null || name.isEmpty()) {
            return;
        }
        UserProfileChangeRequest request = new UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build();
        firebaseUser.updateProfile(request)
                .addOnFailureListener(e -> logSafe("auth/displayName", e));
    }

    private static void logSafe(String stage, Exception e) {
        if (e == null) {
            Log.w(TAG, stage + " unknown");
            return;
        }
        if (e instanceof FirebaseAuthException) {
            Log.w(TAG, stage + " auth/" + ((FirebaseAuthException) e).getErrorCode(), e);
        } else if (e instanceof FirebaseFirestoreException) {
            Log.w(TAG, stage + " firestore/"
                    + ((FirebaseFirestoreException) e).getCode(), e);
        } else {
            Log.w(TAG, stage + " " + e.getClass().getSimpleName(), e);
        }
    }

    private static final class ProfileResult {
        final User user;
        final boolean created;
        final String username;

        ProfileResult(User user, boolean created, String username) {
            this.user = user;
            this.created = created;
            this.username = username;
        }
    }

    // ------------------------------------------------------------------ login

    public void login(String email, String password, Callback<User> callback) {
        auth.signInWithEmailAndPassword(normalizeEmail(email), password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onError(new IllegalStateException("Đăng nhập thất bại"));
                        return;
                    }
                    ensureProfile(firebaseUser, callback);
                })
                .addOnFailureListener(callback::onError);
    }

    public void loginWithCredential(com.google.firebase.auth.AuthCredential credential,
                                    Callback<User> callback) {
        auth.signInWithCredential(credential)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onError(new IllegalStateException("Đăng nhập thất bại"));
                        return;
                    }
                    ensureProfile(firebaseUser, callback);
                })
                .addOnFailureListener(callback::onError);
    }

    /**
     * Loads the profile, creating a minimal one when the Auth account exists without a document
     * (for example an account created from the Firebase Console).
     */
    private void ensureProfile(FirebaseUser firebaseUser, Callback<User> callback) {
        String uid = firebaseUser.getUid();
        db.collection(FirestoreRefs.USERS).document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    User existing = toUser(documentSnapshot);
                    if (existing != null) {
                        callback.onSuccess(existing);
                        return;
                    }
                    String email = firebaseUser.getEmail() != null ? firebaseUser.getEmail() : "";
                    // Sanitisation and uniqueness happen in completeProfile. allowFallback=true
                    // lets a Google display name or a partially registered name be repaired.
                    completeProfile(firebaseUser, firebaseUser.getDisplayName(), email,
                            false, true, callback);
                })
                .addOnFailureListener(e -> {
                    logSafe("ensureProfile/read", e);
                    callback.onError(e);
                });
    }

    // ------------------------------------------------------------------ profile reads

    public void getUser(String uid, Callback<User> callback) {
        db.collection(FirestoreRefs.USERS).document(uid).get()
                .addOnSuccessListener(documentSnapshot -> callback.onSuccess(toUser(documentSnapshot)))
                .addOnFailureListener(callback::onError);
    }

    /** Realtime view of a single profile, used by the profile screen. */
    public ListenerRegistration observeUser(String uid, Callback<User> callback) {
        return db.collection(FirestoreRefs.USERS).document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        callback.onError(error);
                        return;
                    }
                    callback.onSuccess(toUser(snapshot));
                });
    }

    /** {@code true} when another profile already uses this display name. */
    public void isUsernameTaken(String username, Callback<Boolean> callback) {
        if (username == null || username.trim().isEmpty()) {
            callback.onSuccess(false);
            return;
        }
        String value = username.trim();
        final String uid = currentUid();
        // A document read is authorized by the usernames rule; a collection query over private
        // users documents is not (rules are not filters).
        db.collection(FirestoreRefs.USERNAMES).document(value).get()
                .addOnSuccessListener(snapshot -> {
                    String owner = snapshot.exists() ? snapshot.getString("uid") : null;
                    callback.onSuccess(snapshot.exists() && (owner == null || !owner.equals(uid)));
                })
                .addOnFailureListener(callback::onError);
    }

    // ------------------------------------------------------------------ profile writes

    public void setLevel(String uid, String level, Callback<Void> callback) {
        db.collection(FirestoreRefs.USERS).document(uid)
                .update("level", level)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    /**
     * Updates the editable parts of a profile. When the display name changes it is checked for
     * uniqueness first.
     */
    public void updateProfile(String uid, String username, String avatarUrl, String level,
                              Callback<Void> callback) {
        String name = username != null ? username.trim() : null;
        if (name == null || name.isEmpty()) {
            applyProfileUpdate(uid, null, avatarUrl, level, callback);
            return;
        }

        final DocumentReference userRef = db.collection(FirestoreRefs.USERS).document(uid);
        final DocumentReference nameRef = db.collection(FirestoreRefs.USERNAMES).document(name);
        db.runTransaction((Transaction.Function<String>) transaction -> {
            DocumentSnapshot userDoc = transaction.get(userRef);
            if (!userDoc.exists()) {
                throw new IllegalStateException("Không tìm thấy hồ sơ");
            }
            String current = userDoc.getString("username");
            Map<String, Object> data = new HashMap<>();
            if (!name.equals(current)) {
                DocumentSnapshot nameDoc = transaction.get(nameRef);
                if (nameDoc.exists()) {
                    String owner = nameDoc.getString("uid");
                    if (!uid.equals(owner)) {
                        throw new UsernameTakenException();
                    }
                }
                data.put("username", name);
                // Reserve the new name atomically with the profile update. The old name stays
                // reserved (no delete) to avoid a reuse race; a trusted backfill can clean it later.
                transaction.set(nameRef, usernameData(uid, name));
            }
            if (avatarUrl != null) {
                data.put("avatarUrl", avatarUrl);
            }
            if (level != null) {
                data.put("level", level);
            }
            if (!data.isEmpty()) {
                transaction.update(userRef, data);
            }
            return name;
        }).addOnSuccessListener(finalName -> {
            updateAuthDisplayName(auth.getCurrentUser(), finalName);
            callback.onSuccess(null);
        }).addOnFailureListener(e -> {
            logSafe("updateProfile", e);
            callback.onError(e);
        });
    }

    private void applyProfileUpdate(String uid, String username, String avatarUrl, String level,
                                    Callback<Void> callback) {
        Map<String, Object> data = new HashMap<>();
        if (username != null) {
            data.put("username", username);
        }
        if (avatarUrl != null) {
            data.put("avatarUrl", avatarUrl);
        }
        if (level != null) {
            data.put("level", level);
        }
        if (data.isEmpty()) {
            callback.onSuccess(null);
            return;
        }
        db.collection(FirestoreRefs.USERS).document(uid).update(data)
                .addOnSuccessListener(aVoid -> {
                    FirebaseUser firebaseUser = auth.getCurrentUser();
                    if (firebaseUser != null && username != null) {
                        updateAuthDisplayName(firebaseUser, username);
                    }
                    callback.onSuccess(null);
                })
                .addOnFailureListener(callback::onError);
    }

    // ------------------------------------------------------------------ password / email

    public void sendPasswordReset(String email, Callback<Void> callback) {
        auth.sendPasswordResetEmail(normalizeEmail(email))
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    /** Re-sends the verification email for the signed-in account. */
    public void sendEmailVerification(Callback<Void> callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError(new IllegalStateException("Chưa đăng nhập"));
            return;
        }
        user.sendEmailVerification()
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    /** Reloads the Auth user and reports whether the email is verified now. */
    public void refreshEmailVerified(Callback<Boolean> callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onSuccess(false);
            return;
        }
        user.reload().addOnCompleteListener((Task<Void> task) -> {
            if (!task.isSuccessful()) {
                callback.onError(task.getException() != null
                        ? task.getException() : new IllegalStateException("Không làm mới được"));
                return;
            }
            callback.onSuccess(user.isEmailVerified());
        });
    }

    /** Changes the password of the signed-in user after re-authenticating with the current one. */
    public void changePassword(String currentPassword, String newPassword,
                               Callback<Void> callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getEmail() == null) {
            callback.onError(new IllegalStateException("Chưa đăng nhập"));
            return;
        }
        com.google.firebase.auth.AuthCredential credential =
                com.google.firebase.auth.EmailAuthProvider
                        .getCredential(user.getEmail(), currentPassword);
        user.reauthenticate(credential)
                .addOnSuccessListener(aVoid -> user.updatePassword(newPassword)
                        .addOnSuccessListener(unused -> callback.onSuccess(null))
                        .addOnFailureListener(callback::onError))
                .addOnFailureListener(callback::onError);
    }

    // ------------------------------------------------------------------ mapping

    private static User toUser(DocumentSnapshot snapshot) {
        if (snapshot == null || !snapshot.exists()) {
            return null;
        }
        return snapshot.toObject(User.class);
    }
}
