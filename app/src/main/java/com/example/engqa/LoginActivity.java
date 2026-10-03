package com.example.engqa;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
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

public class LoginActivity extends AppCompatActivity {

    private EditText edtEmail;
    private EditText edtPassword;
    private MaterialButton btnLogin;
    private TextView tvGoRegister;
    private TextView tvForgot;
    private TextView tvTerms;
    private MaterialButton btnPhone;
    private MaterialButton btnGoogle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = getColor(R.color.bg_topbar);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_login);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvGoRegister = findViewById(R.id.tvGoRegister);
        tvForgot = findViewById(R.id.tvForgot);
        tvTerms = findViewById(R.id.tvTerms);
        btnPhone = findViewById(R.id.btnPhone);
        btnGoogle = findViewById(R.id.btnGoogle);

        // Setup Terms text with HTML and LinkMovementMethod
        tvTerms.setText(Html.fromHtml(getString(R.string.terms_text), Html.FROM_HTML_MODE_LEGACY));
        tvTerms.setMovementMethod(LinkMovementMethod.getInstance());

        // Setup Go to Register text
        tvGoRegister.setText(Html.fromHtml("Chưa có tài khoản? <font color='#1E90FF'><b>Đăng ký</b></font>", Html.FROM_HTML_MODE_LEGACY));
        tvGoRegister.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });

        // Login button click logic
        btnLogin.setOnClickListener(v -> {
            String email = edtEmail.getText().toString().trim();
            String password = edtPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập đầy đủ thông tin", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Đăng nhập thành công", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, HomeActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
            }
        });

        tvForgot.setOnClickListener(v -> Toast.makeText(this, "Chức năng quên mật khẩu (chưa triển khai)", Toast.LENGTH_SHORT).show());
        btnPhone.setOnClickListener(v -> Toast.makeText(this, "Đăng nhập số điện thoại (chưa triển khai)", Toast.LENGTH_SHORT).show());
        btnGoogle.setOnClickListener(v -> Toast.makeText(this, "Đăng nhập Google (chưa triển khai)", Toast.LENGTH_SHORT).show());
    }
}
