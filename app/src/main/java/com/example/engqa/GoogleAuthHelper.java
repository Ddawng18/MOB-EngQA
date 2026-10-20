package com.example.engqa;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.CancellationSignal;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.GoogleAuthProvider;

public class GoogleAuthHelper {

    public interface GoogleAuthCallback {
        void onSuccess(AuthCredential credential);
        void onError(String errorMessage);
    }

    public static void startGoogleSignIn(Activity activity, ActivityResultLauncher<Intent> launcher, GoogleAuthCallback callback) {
        String webClientId = getWebClientId(activity);

        GetGoogleIdOption googleIdOption = new GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(false)
                .build();

        GetCredentialRequest request = new GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build();

        CredentialManager credentialManager = CredentialManager.create(activity);
        CancellationSignal cancellationSignal = new CancellationSignal();

        credentialManager.getCredentialAsync(
                activity,
                request,
                cancellationSignal,
                ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse result) {
                        Credential credential = result.getCredential();
                        if (credential instanceof CustomCredential
                                && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                            try {
                                GoogleIdTokenCredential googleIdTokenCredential =
                                        GoogleIdTokenCredential.createFrom(credential.getData());
                                String idToken = googleIdTokenCredential.getIdToken();
                                AuthCredential authCredential = GoogleAuthProvider.getCredential(idToken, null);
                                callback.onSuccess(authCredential);
                            } catch (Exception e) {
                                launchGoogleSignInClient(activity, launcher);
                            }
                        } else {
                            launchGoogleSignInClient(activity, launcher);
                        }
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        // Fallback to GoogleSignInClient when CredentialManager fails or provider dependency is missing
                        launchGoogleSignInClient(activity, launcher);
                    }
                }
        );
    }

    public static void launchGoogleSignInClient(Activity activity, ActivityResultLauncher<Intent> launcher) {
        String webClientId = getWebClientId(activity);
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build();

        GoogleSignInClient googleSignInClient = GoogleSignIn.getClient(activity, gso);
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            launcher.launch(signInIntent);
        });
    }

    public static void handleActivityResult(Intent data, GoogleAuthCallback callback) {
        if (data == null) {
            callback.onError("Đăng nhập Google bị huỷ");
            return;
        }
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account != null && account.getIdToken() != null) {
                AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
                callback.onSuccess(credential);
            } else {
                callback.onError("Không lấy được ID token từ Google");
            }
        } catch (ApiException e) {
            if (e.getStatusCode() == 10) {
                callback.onError("Chưa cấu hình Google Sign-In trên Firebase Console (mã 10). Vui lòng thêm SHA-1 và cập nhật google-services.json.");
            } else {
                callback.onError("Đăng nhập Google thất bại (mã " + e.getStatusCode() + "): " + e.getMessage());
            }
        }
    }

    private static String getWebClientId(Context context) {
        try {
            return context.getString(R.string.default_web_client_id);
        } catch (Exception e) {
            return "13331358113-accountdata-dd29b.apps.googleusercontent.com";
        }
    }
}
