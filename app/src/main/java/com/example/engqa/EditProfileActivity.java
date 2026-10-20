package com.example.engqa;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.engqa.model.UiState;
import com.example.engqa.model.User;
import com.example.engqa.viewmodel.ProfileViewModel;

/** Edits the display name and level of the signed-in user. */
public class EditProfileActivity extends AppCompatActivity {

    private ProfileViewModel viewModel;
    private EditText etUsername;
    private Spinner spinnerLevel;
    private String uid;
    private User currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = getColor(R.color.bg_topbar);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_edit_profile);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.ime());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        uid = new SessionManager(this).getUserId();
        if (uid == null) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        etUsername = findViewById(R.id.etUsername);
        spinnerLevel = findViewById(R.id.spinnerLevel);

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, getResources().getStringArray(R.array.levels)) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(getColor(R.color.text_primary));
                tv.setTextSize(14);
                return tv;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLevel.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSaveProfile).setOnClickListener(v -> save());
        findViewById(R.id.btnChangePassword).setOnClickListener(v -> showChangePasswordDialog());

        viewModel.getUser().observe(this, state -> {
            if (state != null && state.getStatus() == UiState.Status.SUCCESS) {
                bind(state.getData());
            }
        });
        viewModel.getActionState().observe(this, state -> {
            if (state == null) {
                return;
            }
            if (state.getStatus() == UiState.Status.SUCCESS) {
                if ("password_changed".equals(state.getData())) {
                    Toast.makeText(this, R.string.password_changed, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Đã cập nhật hồ sơ", Toast.LENGTH_SHORT).show();
                    finish();
                }
            } else if (state.getStatus() == UiState.Status.ERROR) {
                Toast.makeText(this, state.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Three-field dialog: current password, new password, confirmation. */
    private void showChangePasswordDialog() {
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        androidx.appcompat.widget.LinearLayoutCompat container =
                new androidx.appcompat.widget.LinearLayoutCompat(this);
        container.setOrientation(androidx.appcompat.widget.LinearLayoutCompat.VERTICAL);
        container.setPadding(padding, padding / 2, padding, 0);

        EditText current = passwordField(R.string.hint_current_password);
        EditText newPassword = passwordField(R.string.hint_new_password);
        EditText confirm = passwordField(R.string.hint_confirm_password);
        container.addView(current);
        container.addView(newPassword);
        container.addView(confirm);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.change_password_title)
                .setView(container)
                .setPositiveButton(R.string.save, (dialog, which) -> viewModel.changePassword(
                        current.getText().toString(),
                        newPassword.getText().toString(),
                        confirm.getText().toString()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private EditText passwordField(int hintRes) {
        EditText field = new EditText(this);
        field.setHint(hintRes);
        field.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setMaxLines(1);
        return field;
    }

    @Override
    protected void onStart() {
        super.onStart();
        viewModel.start(uid);
    }

    @Override
    protected void onStop() {
        super.onStop();
        viewModel.stop();
    }

    private void bind(User user) {
        currentUser = user;
        if (user == null) {
            return;
        }
        etUsername.setText(user.getUsername());
        ((TextView) findViewById(R.id.tvAvatarLarge)).setText(
                user.getUsername() != null && !user.getUsername().isEmpty()
                        ? String.valueOf(user.getUsername().charAt(0)).toUpperCase() : "?");
        if (user.getLevel() != null) {
            String[] levels = getResources().getStringArray(R.array.levels);
            for (int i = 0; i < levels.length; i++) {
                if (levels[i].equals(user.getLevel())) {
                    spinnerLevel.setSelection(i);
                    break;
                }
            }
        }
    }

    private void save() {
        String username = etUsername.getText().toString().trim();
        if (username.isEmpty()) {
            etUsername.setError(getString(R.string.label_username));
            return;
        }
        String level = (String) spinnerLevel.getSelectedItem();
        String avatarUrl = currentUser != null ? currentUser.getAvatarUrl() : null;
        viewModel.updateProfile(username, avatarUrl, level);
    }
}
