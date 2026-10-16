package com.example.engqa;

import android.app.Activity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.engqa.data.RepoErrors;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

public class PhoneAuthHelper {

    public interface PhoneAuthCallback {
        void onSuccess(AuthCredential credential);
        void onError(String errorMessage);
    }

    private final Activity activity;
    private final PhoneAuthCallback callback;
    private BottomSheetDialog bottomSheetDialog;

    private EditText edtPhoneInput;
    private EditText edtOtpInput;
    private TextView tvPhoneError;
    private MaterialButton btnPhoneAction;
    private ProgressBar progressPhone;
    private TextView tvPhoneSubtitle;

    private String verificationId;
    private PhoneAuthProvider.ForceResendingToken resendToken;
    private boolean isOtpSent = false;

    public PhoneAuthHelper(Activity activity, PhoneAuthCallback callback) {
        this.activity = activity;
        this.callback = callback;
    }

    public void show() {
        bottomSheetDialog = new BottomSheetDialog(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_phone_auth, null);
        bottomSheetDialog.setContentView(view);

        edtPhoneInput = view.findViewById(R.id.edtPhoneInput);
        edtOtpInput = view.findViewById(R.id.edtOtpInput);
        tvPhoneError = view.findViewById(R.id.tvPhoneError);
        btnPhoneAction = view.findViewById(R.id.btnPhoneAction);
        progressPhone = view.findViewById(R.id.progressPhone);
        tvPhoneSubtitle = view.findViewById(R.id.tvPhoneSubtitle);

        btnPhoneAction.setOnClickListener(v -> {
            if (!isOtpSent) {
                sendOtp();
            } else {
                verifyOtp();
            }
        });

        bottomSheetDialog.show();
    }

    private void sendOtp() {
        hideError();
        String rawPhone = edtPhoneInput.getText().toString().trim();
        if (TextUtils.isEmpty(rawPhone)) {
            showError("Vui lòng nhập số điện thoại");
            return;
        }

        String formattedPhone = formatPhoneNumber(rawPhone);
        setLoading(true);

        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                .setPhoneNumber(formattedPhone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                    @Override
                    public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                        setLoading(false);
                        dismissDialog();
                        callback.onSuccess(credential);
                    }

                    @Override
                    public void onVerificationFailed(@NonNull FirebaseException e) {
                        setLoading(false);
                        showError(RepoErrors.message(e));
                    }

                    @Override
                    public void onCodeSent(@NonNull String verId,
                                           @NonNull PhoneAuthProvider.ForceResendingToken token) {
                        setLoading(false);
                        verificationId = verId;
                        resendToken = token;
                        isOtpSent = true;

                        edtPhoneInput.setEnabled(false);
                        edtOtpInput.setVisibility(View.VISIBLE);
                        edtOtpInput.requestFocus();
                        btnPhoneAction.setText("Xác nhận OTP");
                        tvPhoneSubtitle.setText("Nhập mã OTP 6 số đã được gửi đến số điện thoại");
                    }
                })
                .build();

        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private void verifyOtp() {
        hideError();
        String otp = edtOtpInput.getText().toString().trim();
        if (TextUtils.isEmpty(otp) || otp.length() < 6) {
            showError("Vui lòng nhập mã OTP 6 số");
            return;
        }

        setLoading(true);
        try {
            PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, otp);
            setLoading(false);
            dismissDialog();
            callback.onSuccess(credential);
        } catch (Exception e) {
            setLoading(false);
            showError("Mã OTP không hợp lệ");
        }
    }

    private void dismissDialog() {
        if (bottomSheetDialog != null && bottomSheetDialog.isShowing()) {
            bottomSheetDialog.dismiss();
        }
    }

    private String formatPhoneNumber(String rawPhone) {
        String phone = rawPhone.trim().replaceAll("\\s+", "");
        if (phone.startsWith("0")) {
            phone = "+84" + phone.substring(1);
        } else if (!phone.startsWith("+")) {
            phone = "+84" + phone;
        }
        return phone;
    }

    private void setLoading(boolean loading) {
        if (progressPhone != null) {
            progressPhone.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
        if (btnPhoneAction != null) {
            btnPhoneAction.setEnabled(!loading);
            btnPhoneAction.setText(loading ? "" : (isOtpSent ? "Xác nhận OTP" : "Gửi mã OTP"));
        }
        if (edtPhoneInput != null && !isOtpSent) {
            edtPhoneInput.setEnabled(!loading);
        }
        if (edtOtpInput != null && isOtpSent) {
            edtOtpInput.setEnabled(!loading);
        }
    }

    private void showError(String message) {
        if (tvPhoneError != null) {
            tvPhoneError.setText(message);
            tvPhoneError.setVisibility(View.VISIBLE);
        }
    }

    private void hideError() {
        if (tvPhoneError != null) {
            tvPhoneError.setVisibility(View.GONE);
        }
    }
}
