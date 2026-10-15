package com.example.engqa.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.engqa.data.Callback;
import com.example.engqa.data.RepoErrors;
import com.example.engqa.data.UserRepository;
import com.example.engqa.model.UiState;
import com.example.engqa.model.User;
import com.example.engqa.util.SingleLiveEvent;
import com.example.engqa.util.Validators;

/**
 * Backs the login / register / choose-level screens.
 *
 * <p>Form validation is synchronous and returns per-field messages ({@link FormErrors}) so the
 * Activity can highlight the exact field; network work is asynchronous and reported through
 * LiveData. One-shot messages use {@link SingleLiveEvent} so a rotation does not replay a toast.
 */
public class AuthViewModel extends ViewModel {

    /** Per-field validation messages; {@code null} means the field is valid. */
    public static final class FormErrors {
        public String email;
        public String password;
        public String confirmPassword;
        public String username;

        public boolean hasAny() {
            return email != null || password != null || confirmPassword != null || username != null;
        }

        public String firstMessage() {
            if (email != null) {
                return email;
            }
            if (password != null) {
                return password;
            }
            if (confirmPassword != null) {
                return confirmPassword;
            }
            return username;
        }
    }

    private final UserRepository userRepository = new UserRepository();

    private final MutableLiveData<UiState<User>> authState = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> emailVerified = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> usernameAvailable = new MutableLiveData<>();
    private final SingleLiveEvent<String> errorEvent = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> infoEvent = new SingleLiveEvent<>();

    // ------------------------------------------------------------------ state

    public LiveData<UiState<User>> getAuthState() {
        return authState;
    }

    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public LiveData<Boolean> getEmailVerified() {
        return emailVerified;
    }

    public LiveData<Boolean> getUsernameAvailable() {
        return usernameAvailable;
    }

    public LiveData<String> getErrorEvent() {
        return errorEvent;
    }

    public LiveData<String> getInfoEvent() {
        return infoEvent;
    }

    public boolean isLoggedIn() {
        return userRepository.isLoggedIn();
    }

    public String currentUid() {
        return userRepository.currentUid();
    }

    public String currentEmail() {
        return userRepository.currentUser() != null
                ? userRepository.currentUser().getEmail() : null;
    }

    // ------------------------------------------------------------------ validation

    public FormErrors validateLoginForm(String email, String password) {
        FormErrors errors = new FormErrors();
        if (email == null || email.trim().isEmpty()) {
            errors.email = "Vui lòng nhập email";
        } else if (!Validators.isValidEmail(email)) {
            errors.email = "Email không hợp lệ";
        }
        if (password == null || password.isEmpty()) {
            errors.password = "Vui lòng nhập mật khẩu";
        }
        return errors;
    }

    public FormErrors validateRegisterForm(String email, String password, String confirmPassword,
                                           String username) {
        FormErrors errors = new FormErrors();
        if (email == null || email.trim().isEmpty()) {
            errors.email = "Vui lòng nhập email";
        } else if (!Validators.isValidEmail(email)) {
            errors.email = "Email không hợp lệ";
        }
        errors.password = Validators.passwordError(password);
        errors.confirmPassword = Validators.confirmPasswordError(password, confirmPassword);
        errors.username = Validators.usernameError(username);
        return errors;
    }

    // ------------------------------------------------------------------ actions

    public void login(String email, String password) {
        FormErrors errors = validateLoginForm(email, password);
        if (errors.hasAny()) {
            errorEvent.setValue(errors.firstMessage());
            return;
        }
        loading.setValue(true);
        userRepository.login(email.trim(), password, new Callback<User>() {
            @Override
            public void onSuccess(User user) {
                loading.setValue(false);
                emailVerified.setValue(userRepository.isEmailVerified());
                authState.setValue(UiState.success(user));
            }

            @Override
            public void onError(Exception e) {
                loading.setValue(false);
                authState.setValue(UiState.error(RepoErrors.message(e)));
                errorEvent.setValue(RepoErrors.message(e));
            }
        });
    }

    public void register(String email, String password, String confirmPassword, String username) {
        FormErrors errors = validateRegisterForm(email, password, confirmPassword, username);
        if (errors.hasAny()) {
            errorEvent.setValue(errors.firstMessage());
            return;
        }
        loading.setValue(true);
        userRepository.register(email.trim(), password, username.trim(), new Callback<User>() {
            @Override
            public void onSuccess(User user) {
                loading.setValue(false);
                emailVerified.setValue(false);
                infoEvent.setValue("Tạo tài khoản thành công");
                authState.setValue(UiState.success(user));
            }

            @Override
            public void onError(Exception e) {
                loading.setValue(false);
                authState.setValue(UiState.error(RepoErrors.message(e)));
                errorEvent.setValue(RepoErrors.message(e));
            }
        });
    }

    /** Live display-name availability check (used once the user is signed in, e.g. edit profile). */
    public void checkUsernameAvailability(String username) {
        String error = Validators.usernameError(username);
        if (error != null) {
            usernameAvailable.setValue(null);
            return;
        }
        userRepository.isUsernameTaken(username, new Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean taken) {
                usernameAvailable.setValue(!Boolean.TRUE.equals(taken));
            }

            @Override
            public void onError(Exception e) {
                usernameAvailable.setValue(null);
            }
        });
    }

    public void sendPasswordReset(String email) {
        if (!Validators.isValidEmail(email)) {
            errorEvent.setValue("Email không hợp lệ");
            return;
        }
        userRepository.sendPasswordReset(email, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                infoEvent.setValue("Đã gửi email đặt lại mật khẩu");
            }

            @Override
            public void onError(Exception e) {
                errorEvent.setValue(RepoErrors.message(e));
            }
        });
    }

    public void resendVerificationEmail() {
        userRepository.sendEmailVerification(new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                infoEvent.setValue("Đã gửi email xác minh tới " + currentEmail());
            }

            @Override
            public void onError(Exception e) {
                errorEvent.setValue(RepoErrors.message(e));
            }
        });
    }

    /** Re-checks verification after the user says they clicked the link in their inbox. */
    public void refreshEmailVerified() {
        userRepository.refreshEmailVerified(new Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean verified) {
                emailVerified.setValue(Boolean.TRUE.equals(verified));
                infoEvent.setValue(Boolean.TRUE.equals(verified)
                        ? "Email đã được xác minh"
                        : "Email chưa được xác minh");
            }

            @Override
            public void onError(Exception e) {
                errorEvent.setValue(RepoErrors.message(e));
            }
        });
    }

    public void setLevel(String level, Callback<Void> callback) {
        String uid = userRepository.currentUid();
        if (uid == null) {
            callback.onSuccess(null);
            return;
        }
        userRepository.setLevel(uid, level, callback);
    }

    public void logout() {
        userRepository.logout();
    }
}
