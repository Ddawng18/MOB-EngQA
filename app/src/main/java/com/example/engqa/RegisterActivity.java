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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.engqa.model.UiState;
import com.example.engqa.viewmodel.AuthViewModel;
import com.google.firebase.auth.AuthCredential;
import com.google.android.material.button.MaterialButton;

/**
 * Account creation.
 *
 * <p>Validates a unique display name, email and password (with confirmation) before calling
 * {@link AuthViewModel#register}. On success the user is signed in already and is sent to level
 * selection; a verification email is sent in the background.
 */
public class RegisterActivity extends AppCompatActivity {

    private EditText edtUsername;
    private EditText edtEmail;
    private EditText edtPassword;
    private EditText edtConfirmPassword;
    private MaterialButton btnRegister;
    private ProgressBar progressAuth;
    private TextView tvAuthError;
    private TextView tvUsernameHint;

    private AuthViewModel viewModel;
    private boolean navigating;
    private ActivityResultLauncher<Intent> googleSignInLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getData() == null) {
                        progressAuth.setVisibility(View.GONE);
                        return;
                    }
                    GoogleAuthHelper.handleActivityResult(result.getData(),
                            new GoogleAuthHelper.GoogleAuthCallback() {
                                @Override
                                public void onSuccess(AuthCredential credential) {
                                    viewModel.loginWithCredential(credential);
                                }
                                @Override
                                public void onError(String errorMessage) {
                                    // Inline error only: do not duplicate it in a Toast.
                                    showError(errorMessage);
                                }
                            });
                });
        int barColor = getColor(R.color.bg_topbar);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_register);

        View registerRoot = findViewById(R.id.registerRoot);
        int baseStart = registerRoot.getPaddingStart();
        int baseTop = registerRoot.getPaddingTop();
        int baseEnd = registerRoot.getPaddingEnd();
        int baseBottom = registerRoot.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(registerRoot,
                (v, insets) -> {
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
        edtUsername = findViewById(R.id.edtUsername);
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        progressAuth = findViewById(R.id.progressAuth);
        tvAuthError = findViewById(R.id.tvAuthError);
        tvUsernameHint = findViewById(R.id.tvUsernameHint);

        TextView tvTerms = findViewById(R.id.tvTerms);
        tvTerms.setText(Html.fromHtml(getString(R.string.terms_text), Html.FROM_HTML_MODE_LEGACY));
        tvTerms.setMovementMethod(LinkMovementMethod.getInstance());

        TextView tvGoLogin = findViewById(R.id.tvGoLogin);
        tvGoLogin.setText(Html.fromHtml(
                getString(R.string.auth_switch_login,
                        String.format("#%06X", 0xFFFFFF & getColor(R.color.accent_orange))),
                Html.FROM_HTML_MODE_LEGACY));
    }

    private void bindActions() {
        btnRegister.setOnClickListener(v -> attemptRegister());
        findViewById(R.id.btnGoogle).setOnClickListener(v ->
                GoogleAuthHelper.startGoogleSignIn(this, googleSignInLauncher,
                        new GoogleAuthHelper.GoogleAuthCallback() {
                            @Override
                            public void onSuccess(AuthCredential credential) {
                                viewModel.loginWithCredential(credential);
                            }
                            @Override
                            public void onError(String errorMessage) {
                                showError(errorMessage);
                            }
                        }));
        edtUsername.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                updateUsernameHint(s == null ? "" : s.toString());
            }
        });
        edtConfirmPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptRegister();
                return true;
            }
            return false;
        });
        findViewById(R.id.btnTogglePassword).setOnClickListener(v ->
                togglePasswordVisibility(edtPassword, findViewById(R.id.btnTogglePassword)));
        findViewById(R.id.btnToggleConfirmPassword).setOnClickListener(v ->
                togglePasswordVisibility(edtConfirmPassword,
                        findViewById(R.id.btnToggleConfirmPassword)));
        findViewById(R.id.tvGoLogin).setOnClickListener(v -> finish());
    }

    private void observe() {
        viewModel.getLoading().observe(this, loading -> {
            boolean busy = Boolean.TRUE.equals(loading);
            progressAuth.setVisibility(busy ? View.VISIBLE : View.GONE);
            btnRegister.setEnabled(!busy);
            findViewById(R.id.btnGoogle).setEnabled(!busy);
            btnRegister.setText(busy
                    ? getString(R.string.creating_account) : getString(R.string.btn_register));
            edtUsername.setEnabled(!busy);
            edtEmail.setEnabled(!busy);
            edtPassword.setEnabled(!busy);
            edtConfirmPassword.setEnabled(!busy);
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

        viewModel.getAuthState().observe(this, state -> {
            if (state == null || state.getStatus() != UiState.Status.SUCCESS) {
                return;
            }
            new SessionManager(this).onLoginSuccess();
            com.example.engqa.model.User user = state.getData();
            if (user == null || user.getLevel() == null || user.getLevel().trim().isEmpty()) {
                goToLevelSelection();
            } else {
                if (navigating) return;
                navigating = true;
                Intent intent = new Intent(this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });
    }

    /** Local (offline) check of the display-name format; uniqueness is verified on submit. */
    private void updateUsernameHint(String username) {
        if (username.trim().isEmpty()) {
            tvUsernameHint.setText(R.string.username_rules);
            tvUsernameHint.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.text_hint));
            return;
        }
        if (com.example.engqa.util.Validators.isValidUsername(username)) {
            tvUsernameHint.setText(R.string.username_available);
            tvUsernameHint.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.accepted_green));
        } else {
            tvUsernameHint.setText(com.example.engqa.util.Validators.usernameError(username));
            tvUsernameHint.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.error_red));
        }
    }

    private void attemptRegister() {
        hideError();
        String username = edtUsername.getText().toString();
        String email = edtEmail.getText().toString();
        String password = edtPassword.getText().toString();
        String confirmPassword = edtConfirmPassword.getText().toString();

        AuthViewModel.FormErrors errors =
                viewModel.validateRegisterForm(email, password, confirmPassword, username);

        edtUsername.setError(errors.username);
        edtEmail.setError(errors.email);
        edtPassword.setError(errors.password);
        edtConfirmPassword.setError(errors.confirmPassword);

        // Focus the first invalid field.
        if (errors.username != null) {
            edtUsername.requestFocus();
            showError(errors.username);
            return;
        }
        if (errors.email != null) {
            edtEmail.requestFocus();
            showError(errors.email);
            return;
        }
        if (errors.password != null) {
            edtPassword.requestFocus();
            showError(errors.password);
            return;
        }
        if (errors.confirmPassword != null) {
            edtConfirmPassword.requestFocus();
            showError(errors.confirmPassword);
            return;
        }
        viewModel.register(email, password, confirmPassword, username);
    }

    private void goToLevelSelection() {
        if (navigating) {
            return;
        }
        navigating = true;
        Intent intent = new Intent(this, ChooseLevelActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
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

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}