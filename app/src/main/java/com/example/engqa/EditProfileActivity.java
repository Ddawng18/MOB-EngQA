package com.example.engqa;

import java.util.Locale;

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
import com.example.engqa.util.Navigation;
import com.example.engqa.util.Validators;
import com.example.engqa.viewmodel.ProfileViewModel;

/**
 * Edits the display name and level of the signed-in user.
 *
 * <p>Reached from Settings (the profile screen no longer owns an Edit action). Password changes
 * live in {@link AccountSecurityActivity} so the flows stay separate.
 */
public class EditProfileActivity extends AppCompatActivity {

    private ProfileViewModel viewModel;
    private EditText etUsername;
    private Spinner spinnerLevel;
    private String uid;
    private User currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = getColor(R.color.bg_screen);
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
                tv.setTextSize(15);
                return tv;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLevel.setAdapter(adapter);

        Navigation.wireBack(this);
        findViewById(R.id.btnSaveProfile).setOnClickListener(v -> save());

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
                Toast.makeText(this, R.string.profile_updated, Toast.LENGTH_SHORT).show();
                finish();
            } else if (state.getStatus() == UiState.Status.ERROR) {
                Toast.makeText(this, state.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
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
                        ? String.valueOf(user.getUsername().charAt(0)).toUpperCase(Locale.getDefault())
                        : "?");
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
        String usernameError = Validators.usernameError(username);
        if (usernameError != null) {
            etUsername.setError(usernameError);
            etUsername.requestFocus();
            return;
        }
        String level = (String) spinnerLevel.getSelectedItem();
        String avatarUrl = currentUser != null ? currentUser.getAvatarUrl() : null;
        viewModel.updateProfile(username, avatarUrl, level);
    }
}
