package com.example.engqa;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class UserRepository {

    public static class User {
        public final String uid;
        public final String username;
        public final String email;
        public final String level;

        public User(String uid, String username, String email, String level) {
            this.uid = uid;
            this.username = username;
            this.email = email;
            this.level = level;
        }
    }

    public interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public static String normalizeEmail(String email) {
        return email != null ? email.trim().toLowerCase(Locale.ROOT) : "";
    }

    public void register(String email, String password, Callback<User> callback) {
        String normalizedEmail = normalizeEmail(email);
        auth.createUserWithEmailAndPassword(normalizedEmail, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onError(new IllegalStateException("User creation failed"));
                        return;
                    }
                    String uid = firebaseUser.getUid();
                    String rawUsername = normalizedEmail.contains("@")
                            ? normalizedEmail.substring(0, normalizedEmail.indexOf('@'))
                            : "user";
                    final String username = rawUsername.isEmpty() ? "user" : rawUsername;

                    Map<String, Object> data = new HashMap<>();
                    data.put("username", username);
                    data.put("email", normalizedEmail);
                    data.put("level", null);
                    data.put("createdAt", FieldValue.serverTimestamp());

                    db.collection("users").document(uid).set(data)
                            .addOnSuccessListener(aVoid -> callback.onSuccess(new User(uid, username, normalizedEmail, null)))
                            .addOnFailureListener(e -> {
                                firebaseUser.delete();
                                callback.onError(e);
                            });
                })
                .addOnFailureListener(callback::onError);
    }

    public void login(String email, String password, Callback<User> callback) {
        String normalizedEmail = normalizeEmail(email);
        auth.signInWithEmailAndPassword(normalizedEmail, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser firebaseUser = authResult.getUser();
                    if (firebaseUser == null) {
                        callback.onError(new IllegalStateException("Login failed"));
                        return;
                    }
                    getUser(firebaseUser.getUid(), callback);
                })
                .addOnFailureListener(callback::onError);
    }

    public void getUser(String uid, Callback<User> callback) {
        db.collection("users").document(uid).get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        callback.onSuccess(null);
                        return;
                    }
                    String username = documentSnapshot.getString("username");
                    String email = documentSnapshot.getString("email");
                    String level = documentSnapshot.getString("level");
                    callback.onSuccess(new User(uid, username, email, level));
                })
                .addOnFailureListener(callback::onError);
    }

    public void setLevel(String uid, String level, Callback<Void> callback) {
        db.collection("users").document(uid).update("level", level)
                .addOnSuccessListener(aVoid -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    public static String messageFor(Exception e) {
        if (e instanceof FirebaseAuthInvalidUserException || e instanceof FirebaseAuthInvalidCredentialsException) {
            return "Email hoặc mật khẩu không đúng";
        } else if (e instanceof FirebaseAuthUserCollisionException) {
            return "Email này đã được đăng ký";
        } else if (e instanceof FirebaseAuthWeakPasswordException) {
            return "Mật khẩu quá yếu";
        } else if (e.getMessage() != null && e.getMessage().toLowerCase().contains("network")) {
            return "Không có kết nối mạng";
        }
        return "Đã xảy ra lỗi: " + e.getLocalizedMessage();
    }
}
