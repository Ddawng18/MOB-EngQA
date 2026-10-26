package com.example.engqa;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.engqa.model.UiState;
import com.example.engqa.util.Navigation;
import com.example.engqa.viewmodel.ProfileViewModel;

/**
 * Account security hub.
 *
 * <p>Two distinct workflows:
 * <ul>
 *   <li><b>Native (email + password)</b> – re-authenticate with the current password, then
 *       {@code updatePassword}. A recovery email is available if the current password is lost.</li>
 *   <li><b>Google</b> – the app never stores a password for these accounts; the screen explains
 *       that Google owns the credential and deep-links to Google's security page.</li>
 * </ul>
 */
public class AccountSecurityActivity extends AppCompatActivity {

    private static final String GOOGLE_SECURITY_URL = "https://myaccount.google.com/security";

    private ProfileViewModel viewModel;
    private EditText etCurrentPassword;
    private EditText etNewPassword;
    private EditText etConfirmPassword;
    private TextView tvSecurityError;
    private View cardPassword;
    private View progress;
    private boolean hasPassword;
    private boolean hasGoogle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = getColor(R.color.bg_screen);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_account_security);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.ime());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        String uid = new SessionManager(this).getUserId();
        if (uid == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        etCurrentPassword = findViewById(R.id.etCurrentPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        tvSecurityError = findViewById(R.id.tvSecurityError);
        cardPassword = findViewById(R.id.cardPassword);
        progress = findViewById(R.id.progressSecurity);

        Navigation.wireBack(this);
        configureProvider();

        findViewById(R.id.btnUpdatePassword).setOnClickListener(v -> submitPassword());
        findViewById(R.id.btnResetEmail).setOnClickListener(v -> confirmResetEmail());
        findViewById(R.id.btnGoogleSecurity).setOnClickListener(v -> openGoogleSecurity());

        viewModel.getActionState().observe(this, this::renderAction);
    }

    /** Chooses which workflow to show; both can be present when accounts are linked. */
    private void configureProvider() {
        hasPassword = viewModel.hasPasswordProvider();
        hasGoogle = viewModel.hasGoogleProvider();

        cardPassword.setVisibility(hasPassword ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnGoogleSecurity).setVisibility(hasGoogle ? View.VISIBLE : View.GONE);
        findViewById(R.id.tvLinkedNote).setVisibility(
                hasPassword && hasGoogle ? View.VISIBLE : View.GONE);

        TextView body = findViewById(R.id.tvSecurityBody);
        if (hasPassword) {
            body.setText(R.string.security_native_body);
        } else if (hasGoogle) {
            body.setText(R.string.security_google_body);
        } else {
            body.setText(R.string.password_change_unavailable);
        }
    }

    private void submitPassword() {
        tvSecurityError.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
        viewModel.changePassword(
                etCurrentPassword.getText().toString(),
                etNewPassword.getText().toString(),
                etConfirmPassword.getText().toString());
    }

    private void confirmResetEmail() {
        String email = new SessionManager(this).getEmail();
        new AlertDialog.Builder(this)
                .setTitle(R.string.security_reset_confirm_title)
                .setMessage(getString(R.string.security_reset_confirm_message,
                        email != null ? email : getString(R.string.hint_email)))
                .setPositiveButton(R.string.security_reset_button,
                        (dialog, which) -> viewModel.sendPasswordReset())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openGoogleSecurity() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(GOOGLE_SECURITY_URL)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.google_security_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void renderAction(UiState<String> state) {
        if (state == null) {
            return;
        }
        if (state.getStatus() == UiState.Status.SUCCESS) {
            progress.setVisibility(View.GONE);
            String action = state.getData();
            if ("password_changed".equals(action)) {
                etCurrentPassword.setText("");
                etNewPassword.setText("");
                etConfirmPassword.setText("");
                tvSecurityError.setVisibility(View.GONE);
                Toast.makeText(this, R.string.password_changed, Toast.LENGTH_SHORT).show();
            } else if ("password_reset_sent".equals(action)) {
                Toast.makeText(this, R.string.security_reset_sent, Toast.LENGTH_LONG).show();
            }
        } else if (state.getStatus() == UiState.Status.ERROR) {
            progress.setVisibility(View.GONE);
            tvSecurityError.setText(state.getMessage());
            tvSecurityError.setVisibility(View.VISIBLE);
        }
    }
}
