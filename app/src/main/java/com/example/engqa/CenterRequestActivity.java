package com.example.engqa;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.engqa.model.Center;
import com.example.engqa.model.UiState;
import com.example.engqa.viewmodel.CenterViewModel;

import java.util.Collections;

/** Sends a centre registration request that an admin must approve. */
public class CenterRequestActivity extends AppCompatActivity {

    private CenterViewModel viewModel;
    private EditText etName;
    private EditText etDescription;
    private EditText etAddress;
    private EditText etPhone;
    private EditText etEmail;
    private String uid;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = getColor(R.color.bg_topbar);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_center_request);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.ime());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        uid = new SessionManager(this).getUserId();
        if (uid == null) {
            startActivity(new android.content.Intent(this, LoginActivity.class));
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(CenterViewModel.class);
        etName = findViewById(R.id.etCenterName);
        etDescription = findViewById(R.id.etCenterDescription);
        etAddress = findViewById(R.id.etCenterAddress);
        etPhone = findViewById(R.id.etCenterPhone);
        etEmail = findViewById(R.id.etCenterEmail);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnSubmitRequest).setOnClickListener(v -> submit());

        viewModel.getActionState().observe(this, state -> {
            if (state == null) {
                return;
            }
            if (state.getStatus() == UiState.Status.SUCCESS) {
                Toast.makeText(this, R.string.center_request_sent, Toast.LENGTH_LONG).show();
                finish();
            } else if (state.getStatus() == UiState.Status.ERROR) {
                Toast.makeText(this, state.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void submit() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            etName.setError(getString(R.string.center_name));
            return;
        }
        Center center = new Center();
        center.setName(name);
        center.setDescription(etDescription.getText().toString().trim());
        center.setAddress(etAddress.getText().toString().trim());
        center.setPhone(etPhone.getText().toString().trim());
        center.setEmail(etEmail.getText().toString().trim());
        center.setManagerUids(Collections.singletonList(uid));
        viewModel.requestCenter(uid, center);
    }
}
