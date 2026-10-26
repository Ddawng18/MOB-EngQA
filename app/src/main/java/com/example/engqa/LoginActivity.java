package com.example.engqa;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.InputType;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.engqa.data.Callback;
import com.example.engqa.model.UiState;
import com.example.engqa.model.User;
import com.example.engqa.util.Validators;
import com.example.engqa.viewmodel.AuthViewModel;
import com.google.android.material.button.MaterialButton;

/**
 * Email/password sign-in.
 *
 * <p>Covers the complete login flow: field validation, password visibility, loading state,
 * specific error messages, password reset, and the "email not verified" case (with resend and a
 * "continue anyway" escape hatch). Session persistence is handled by Firebase Auth, so a signed-in
 * user skips this screen entirely.
 */
public class LoginActivity extends AppCompatActivity {

    private EditText edtEmail;
    private EditText edtPassword;
    private MaterialButton btnLogin;
    private ProgressBar progressAuth;
    private TextView tvAuthError;
    private View verifyBox;
    private ImageView btnTogglePassword;

    private AuthViewModel viewModel;
    private boolean navigating;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        if (viewModel.isLoggedIn()) {
            // Repair an Auth session with no profile document before entering Home.
            viewModel.ensureCurrentProfile(new Callback<User>() {
                @Override
                public void onSuccess(User user) {
                    goHome();
                }

                @Override
                public void onError(Exception e) {
                    goHome();
                }
            });
            return;
        }

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getData() != null) {
                        GoogleAuthHelper.handleActivityResult(result.getData(), new GoogleAuthHelper.GoogleAuthCallback() {
                            @Override
                            public void onSuccess(com.google.firebase.auth.AuthCredential credential) {
                                viewModel.loginWithCredential(credential);
                            }

                            @Override
                            public void onError(String errorMessage) {
                                showError(errorMessage);
                            }
                        });
                    }
                }
        );

        int barColor = getColor(R.color.bg_topbar);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_login);

        View loginRoot = findViewById(R.id.loginRoot);
        int baseStart = loginRoot.getPaddingStart();
        int baseTop = loginRoot.getPaddingTop();
        int baseEnd = loginRoot.getPaddingEnd();
        int baseBottom = loginRoot.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(loginRoot, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            // Keep the XML landscape gutters instead of replacing them with the bar insets.
            v.setPaddingRelative(baseStart + bars.left, baseTop + bars.top,
                    baseEnd + bars.right, baseBottom + bars.bottom);
            return insets;
        });

        bindViews();
        bindActions();
        observe();
    }

    private void bindViews() {
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);
        progressAuth = findViewById(R.id.progressAuth);
        tvAuthError = findViewById(R.id.tvAuthError);
        verifyBox = findViewById(R.id.verifyBox);
        // Only signed-in, unverified accounts see this box.
        verifyBox.setVisibility(View.GONE);
        btnTogglePassword = findViewById(R.id.btnTogglePassword);

        TextView tvTerms = findViewById(R.id.tvTerms);
        tvTerms.setText(Html.fromHtml(getString(R.string.terms_text), Html.FROM_HTML_MODE_LEGACY));
        tvTerms.setMovementMethod(LinkMovementMethod.getInstance());

        TextView tvGoRegister = findViewById(R.id.tvGoRegister);
        tvGoRegister.setText(Html.fromHtml(
                getString(R.string.auth_switch_register,
                        String.format("#%06X", 0xFFFFFF & getColor(R.color.accent_orange))),
                Html.FROM_HTML_MODE_LEGACY));
    }

    private void bindActions() {
        btnLogin.setOnClickListener(v -> attemptLogin());
        edtPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_SEND) {
                attemptLogin();
                return true;
            }
            return false;
        });
        btnTogglePassword.setOnClickListener(v -> togglePasswordVisibility(edtPassword,
                btnTogglePassword));
        findViewById(R.id.tvForgot).setOnClickListener(v -> showResetPasswordDialog());
        findViewById(R.id.tvGoRegister).setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
            overridePendingTransition(0, 0);
        });
        findViewById(R.id.tvResendVerification).setOnClickListener(
                v -> viewModel.resendVerificationEmail());
        findViewById(R.id.tvVerifyNow).setOnClickListener(v -> viewModel.refreshEmailVerified());
        findViewById(R.id.btnGoogle).setOnClickListener(v -> startGoogleSignIn());
    }

    private void startGoogleSignIn() {
        GoogleAuthHelper.startGoogleSignIn(this, googleSignInLauncher, new GoogleAuthHelper.GoogleAuthCallback() {
            @Override
            public void onSuccess(com.google.firebase.auth.AuthCredential credential) {
                viewModel.loginWithCredential(credential);
            }

            @Override
            public void onError(String errorMessage) {
                showError(errorMessage);
            }
        });
    }

    private void observe() {
        viewModel.getLoading().observe(this, loading -> {
            boolean busy = Boolean.TRUE.equals(loading);
            progressAuth.setVisibility(busy ? View.VISIBLE : View.GONE);
            btnLogin.setEnabled(!busy);
            btnLogin.setText(busy ? getString(R.string.signing_in) : getString(R.string.btn_login));
            edtEmail.setEnabled(!busy);
            edtPassword.setEnabled(!busy);
        });

        viewModel.getErrorEvent().observe(this, message -> {
            if (message != null) {
                showError(message);
            }
        });

        viewModel.getInfoEvent().observe(this, message -> {
            if (message != null) {
                hideError();
                toast(message);
            }
        });

        viewModel.getEmailVerified().observe(this, verified -> {
            boolean signedIn = new SessionManager(this).isLoggedIn();
            boolean ok = Boolean.TRUE.equals(verified);
            verifyBox.setVisibility(signedIn && !ok ? View.VISIBLE : View.GONE);
        });

        viewModel.getAuthState().observe(this, state -> {
            if (state == null || state.getStatus() != UiState.Status.SUCCESS) {
                return;
            }
            new SessionManager(this).onLoginSuccess();
            if (viewModel.getEmailVerified().getValue() != null
                    && !Boolean.TRUE.equals(viewModel.getEmailVerified().getValue())) {
                showUnverifiedDialog();
            } else {
                goHome();
            }
        });
    }

    private void attemptLogin() {
        hideError();
        String email = edtEmail.getText().toString();
        String password = edtPassword.getText().toString();

        AuthViewModel.FormErrors errors = viewModel.validateLoginForm(email, password);
        if (errors.email != null) {
            edtEmail.setError(errors.email);
            edtEmail.requestFocus();
            showError(errors.email);
            return;
        }
        if (errors.password != null) {
            edtPassword.setError(errors.password);
            edtPassword.requestFocus();
            showError(errors.password);
            return;
        }
        viewModel.login(email, password);
    }

    private void showResetPasswordDialog() {
        EditText input = new EditText(this);
        input.setHint(R.string.hint_email);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        String current = edtEmail.getText().toString().trim();
        if (!current.isEmpty()) {
            input.setText(current);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.forgot_password)
                .setMessage(R.string.reset_password_message)
                .setView(input)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    String email = input.getText().toString().trim();
                    if (!Validators.isValidEmail(email)) {
                        showError(getString(R.string.err_invalid_email));
                        return;
                    }
                    viewModel.sendPasswordReset(email);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showUnverifiedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Email chưa được xác minh")
                .setMessage("Bạn vẫn có thể tiếp tục, nhưng nên xác minh email để bảo vệ tài khoản.")
                .setPositiveButton("Gửi lại email", (dialog, which) -> {
                    viewModel.resendVerificationEmail();
                    goHome();
                })
                .setNeutralButton("Tiếp tục", (dialog, which) -> goHome())
                .setCancelable(false)
                .show();
    }

    private void togglePasswordVisibility(EditText field, ImageView toggle) {
        boolean visible = field.getInputType()
                == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        if (visible) {
            field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            toggle.setImageResource(R.drawable.ic_visibility_off);
        } else {
            field.setInputType(
                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            toggle.setImageResource(R.drawable.ic_visibility);
        }
        field.setSelection(field.getText().length());
    }

    private void showError(String message) {
        tvAuthError.setText(message);
        tvAuthError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        tvAuthError.setVisibility(View.GONE);
    }

    private void goHome() {
        if (navigating) {
            return;
        }
        navigating = true;
        Intent intent = new Intent(this, HomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
