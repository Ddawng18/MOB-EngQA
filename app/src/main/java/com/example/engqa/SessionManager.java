package com.example.engqa;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Remembers which user is logged in via Firebase Auth. */
public class SessionManager {

    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    public SessionManager(android.content.Context context) {
    }

    public void logout() {
        auth.signOut();
    }

    public String getUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }
}
