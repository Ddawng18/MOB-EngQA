package com.example.engqa.data;

import com.example.engqa.model.User;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The single access point for Firebase Auth and the {@code users/{uid}} profile document.
 *
 * <p>Registration and profile updates keep the Auth account and the Firestore document in sync:
 * if the Firestore write fails after the Auth account was created, the account is rolled back so a
 * user never ends up with a half-created login. Display names are unique, enforced with a query
 * before the profile is written (the user is signed in at that point, which the rules require).
 */
public class UserRepository {

    /** Thrown when a display name is already used by another profile. */
    public static final class UsernameTakenException extends Exception {
        public UsernameTakenException() {
            super("Tên hiển thị này đã được sử dụng");
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

        auth.createUserWithEmailAndPassword(normalizedEmail, password)
                .addOnSuccessListener(authResult -> {
                    final FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onError(new IllegalStateException("Không tạo được tài khoản"));
                        return;
                    }
                    final String uid = firebaseUser.getUid();

                    // The user is signed in now, so the uniqueness query is allowed by the rules.
                    isUsernameTaken(name, new Callback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean taken) {
                            if (Boolean.TRUE.equals(taken)) {
                                rollbackAuthAccount(firebaseUser,
                                        new UsernameTakenException(), callback);
                                return;
                            }
                            writeProfile(uid, name, normalizedEmail, firebaseUser, callback);
                        }

                        @Override
                        public void onError(Exception e) {
                            // Treat a failed check as "not available" rather than creating a dup.
                            rollbackAuthAccount(firebaseUser, e, callback);
                        }
                    });
                })
                .addOnFailureListener(callback::onError);
    }

    private void writeProfile(String uid, String name, String email, FirebaseUser firebaseUser,
                              Callback<User> callback) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        Map<String, Object> data = new HashMap<>();
        data.put("username", name);
        data.put("usernameLower", lowerName);
        data.put("email", email);
        data.put("level", null);
        data.put("avatarUrl", null);
        data.put("questionCount", 0);
        data.put("answerCount", 0);
        data.put("acceptedCount", 0);
        data.put("createdAt", FieldValue.serverTimestamp());

        Map<String, Object> usernameData = new HashMap<>();
        usernameData.put("uid", uid);
        usernameData.put("username", name);

        WriteBatch batch = db.batch();
        batch.set(db.collection(FirestoreRefs.USERS).document(uid), data);
        batch.set(db.collection("usernames").document(lowerName), usernameData);

        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    updateAuthDisplayName(firebaseUser, name);
                    firebaseUser.sendEmailVerification();
                    callback.onSuccess(new User(uid, name, email, null));
                })
                .addOnFailureListener(e -> rollbackAuthAccount(firebaseUser, e, callback));
    }

    private void rollbackAuthAccount(FirebaseUser firebaseUser, Exception cause,
                                     Callback<User> callback) {
        firebaseUser.delete().addOnCompleteListener(task -> callback.onError(cause));
    }

    private void updateAuthDisplayName(FirebaseUser firebaseUser, String name) {
        UserProfileChangeRequest request = new UserProfileChangeRequest.Builder()
                .setDisplayName(name)
                .build();
        firebaseUser.updateProfile(request);
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
                    String name = firebaseUser.getDisplayName() != null && !firebaseUser.getDisplayName().trim().isEmpty()
                            ? firebaseUser.getDisplayName().trim()
                            : (!email.isEmpty() ? defaultUsername(email)
                            : "user_" + (System.currentTimeMillis() % 100000));
                    writeProfile(uid, name, email, firebaseUser, callback);
                })
                .addOnFailureListener(callback::onError);
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

    /** {@code true} when another profile already uses this display name (case-insensitive). */
    public void isUsernameTaken(String username, Callback<Boolean> callback) {
        isUsernameTaken(username, null, callback);
    }

    public void isUsernameTaken(String username, String currentUid, Callback<Boolean> callback) {
        if (username == null || username.trim().isEmpty()) {
            callback.onSuccess(false);
            return;
        }
        String value = username.trim();
        String lowerValue = value.toLowerCase(Locale.ROOT);

        db.collection("usernames").document(lowerValue).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        String docUid = doc.getString("uid");
                        if (currentUid != null && currentUid.equals(docUid)) {
                            callback.onSuccess(false);
                        } else {
                            callback.onSuccess(true);
                        }
                    } else {
                        db.collection(FirestoreRefs.USERS)
                                .whereEqualTo("usernameLower", lowerValue)
                                .limit(2)
                                .get()
                                .addOnSuccessListener(querySnapshot -> {
                                    boolean taken = false;
                                    for (DocumentSnapshot userDoc : querySnapshot.getDocuments()) {
                                        if (currentUid == null || !userDoc.getId().equals(currentUid)) {
                                            taken = true;
                                            break;
                                        }
                                    }
                                    if (taken) {
                                        callback.onSuccess(true);
                                    } else {
                                        db.collection(FirestoreRefs.USERS)
                                                .whereEqualTo("username", value)
                                                .limit(2)
                                                .get()
                                                .addOnSuccessListener(qs -> {
                                                    boolean t = false;
                                                    for (DocumentSnapshot ud : qs.getDocuments()) {
                                                        if (currentUid == null || !ud.getId().equals(currentUid)) {
                                                            t = true;
                                                            break;
                                                        }
                                                    }
                                                    callback.onSuccess(t);
                                                })
                                                .addOnFailureListener(callback::onError);
                                    }
                                })
                                .addOnFailureListener(callback::onError);
                    }
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
        if (name != null && !name.isEmpty()) {
            isUsernameTaken(name, uid, new Callback<Boolean>() {
                @Override
                public void onSuccess(Boolean taken) {
                    if (Boolean.TRUE.equals(taken)) {
                        callback.onError(new UsernameTakenException());
                        return;
                    }
                    db.collection(FirestoreRefs.USERS).document(uid).get()
                            .addOnSuccessListener(snapshot -> {
                                String oldLower = snapshot != null ? snapshot.getString("usernameLower") : null;
                                if (oldLower == null && snapshot != null && snapshot.getString("username") != null) {
                                    oldLower = snapshot.getString("username").toLowerCase(Locale.ROOT);
                                }
                                applyProfileUpdate(uid, name, oldLower, avatarUrl, level, callback);
                            })
                            .addOnFailureListener(e -> applyProfileUpdate(uid, name, null, avatarUrl, level, callback));
                }

                @Override
                public void onError(Exception e) {
                    callback.onError(e);
                }
            });
            return;
        }
        applyProfileUpdate(uid, null, null, avatarUrl, level, callback);
    }

    private void applyProfileUpdate(String uid, String username, String oldLower, String avatarUrl, String level,
                                    Callback<Void> callback) {
        Map<String, Object> data = new HashMap<>();
        String newLower = null;
        if (username != null) {
            data.put("username", username);
            newLower = username.toLowerCase(Locale.ROOT);
            data.put("usernameLower", newLower);
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

        final String finalNewLower = newLower;
        final String finalOldLower = oldLower;

        WriteBatch batch = db.batch();
        batch.update(db.collection(FirestoreRefs.USERS).document(uid), data);

        if (finalNewLower != null) {
            Map<String, Object> uData = new HashMap<>();
            uData.put("uid", uid);
            uData.put("username", username);
            batch.set(db.collection("usernames").document(finalNewLower), uData);

            if (finalOldLower != null && !finalOldLower.equals(finalNewLower)) {
                batch.delete(db.collection("usernames").document(finalOldLower));
            }
        }

        batch.commit()
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
