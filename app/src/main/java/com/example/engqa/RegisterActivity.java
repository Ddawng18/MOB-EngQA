package com.example.engqa;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

public class RegisterActivity extends AppCompatActivity {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private EditText edtEmail;
    private EditText edtPassword;
    private EditText edtConfirmPassword;
    private MaterialButton btnRegister;
    private TextView tvGoLogin;
    private TextView tvTerms;
    private MaterialButton btnPhone;
    private MaterialButton btnGoogle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = getColor(R.color.bg_topbar);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_register);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvGoLogin = findViewById(R.id.tvGoLogin);
        tvTerms = findViewById(R.id.tvTerms);
        btnPhone = findViewById(R.id.btnPhone);
        btnGoogle = findViewById(R.id.btnGoogle);

        // Email hint with red star
        edtEmail.setHint(Html.fromHtml("Email <font color='#FF0000'>*</font>", Html.FROM_HTML_MODE_LEGACY));

        // Setup Terms text
        tvTerms.setText(Html.fromHtml(getString(R.string.terms_text), Html.FROM_HTML_MODE_LEGACY));
        tvTerms.setMovementMethod(LinkMovementMethod.getInstance());

        // Setup Go to Login text
        tvGoLogin.setText(Html.fromHtml("Đã có tài khoản? <font color='#1E90FF'><b>đăng nhập</b></font>", Html.FROM_HTML_MODE_LEGACY));
        tvGoLogin.setOnClickListener(v -> finish());

        // Register button click logic
        btnRegister.setOnClickListener(v -> {
            String email = edtEmail.getText().toString().trim();
            String password = edtPassword.getText().toString();
            String confirmPassword = edtConfirmPassword.getText().toString();

            if (email.isEmpty()) {
                edtEmail.setError("Email là bắt buộc");
                edtEmail.requestFocus();
                return;
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                edtEmail.setError("Email không hợp lệ");
                edtEmail.requestFocus();
                return;
            }
            if (password.length() < MIN_PASSWORD_LENGTH) {
                edtPassword.setError("Mật khẩu tối thiểu " + MIN_PASSWORD_LENGTH + " ký tự");
                edtPassword.requestFocus();
                return;
            }
            if (!password.equals(confirmPassword)) {
                edtConfirmPassword.setError("Mật khẩu xác nhận không khớp");
                edtConfirmPassword.requestFocus();
                return;
            }

            UserDbHelper.User user = new UserDbHelper(this).register(email, password);
            if (user == null) {
                edtEmail.setError("Email này đã được đăng ký");
                edtEmail.requestFocus();
                return;
            }
            new SessionManager(this).login(user.id);
            Toast.makeText(this, "Đăng ký thành công", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, ChooseLevelActivity.class));
            finish();
        });

        btnPhone.setOnClickListener(v -> Toast.makeText(this, "Đăng ký số điện thoại (chưa triển khai)", Toast.LENGTH_SHORT).show());
        btnGoogle.setOnClickListener(v -> Toast.makeText(this, "Đăng ký Google (chưa triển khai)", Toast.LENGTH_SHORT).show());
    }
}