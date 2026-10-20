package com.example.engqa.data;

import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.Locale;

/**
 * Maps Firebase and network exceptions to short Vietnamese UI messages.
 *
 * <p>Auth failures are resolved by {@link FirebaseAuthException#getErrorCode()} first, so callers
 * get a specific message ("Mật khẩu không đúng") instead of a generic one.
 */
public final class RepoErrors {

    private RepoErrors() {
    }

    public static String message(Exception e) {
        if (e == null) {
            return "Đã xảy ra lỗi không xác định";
        }
        if (e instanceof FirebaseAuthException) {
            return authMessage((FirebaseAuthException) e);
        }
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
            if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return "Bạn không có quyền thực hiện thao tác này";
            }
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE
                    || code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED) {
                return "Không có kết nối mạng";
            }
            if (code == FirebaseFirestoreException.Code.NOT_FOUND) {
                return "Không tìm thấy dữ liệu";
            }
        }
        if (isNetworkError(e)) {
            return "Không có kết nối mạng. Kiểm tra lại đường truyền.";
        }
        String local = e.getLocalizedMessage();
        return local != null && !local.isEmpty()
                ? "Đã xảy ra lỗi: " + local
                : "Đã xảy ra lỗi, vui lòng thử lại";
    }

    /** Firebase Auth specific messages, keyed on the stable error code string. */
    public static String authMessage(Exception e) {
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return "Mật khẩu quá yếu";
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return "Email này đã được đăng ký";
        }
        if (e instanceof FirebaseAuthInvalidUserException) {
            String code = ((FirebaseAuthInvalidUserException) e).getErrorCode();
            if ("ERROR_USER_DISABLED".equals(code)) {
                return "Tài khoản đã bị khoá";
            }
            return "Không tìm thấy tài khoản với email này";
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException) {
            String code = ((FirebaseAuthInvalidCredentialsException) e).getErrorCode();
            if ("ERROR_INVALID_EMAIL".equals(code)) {
                return "Email không hợp lệ";
            }
            return "Email hoặc mật khẩu không đúng";
        }
        if (e instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) e).getErrorCode();
            if (code == null) {
                code = "";
            }
            switch (code) {
                case "ERROR_INVALID_EMAIL":
                    return "Email không hợp lệ";
                case "ERROR_WRONG_PASSWORD":
                    return "Mật khẩu không đúng";
                case "ERROR_USER_NOT_FOUND":
                    return "Không tìm thấy tài khoản với email này";
                case "ERROR_USER_DISABLED":
                    return "Tài khoản đã bị khoá";
                case "ERROR_TOO_MANY_REQUESTS":
                    return "Bạn thao tác quá nhanh, vui lòng thử lại sau";
                case "ERROR_EMAIL_ALREADY_IN_USE":
                    return "Email này đã được đăng ký";
                case "ERROR_WEAK_PASSWORD":
                    return "Mật khẩu quá yếu";
                case "ERROR_OPERATION_NOT_ALLOWED":
                    return "Phương thức đăng nhập này chưa được bật trên Firebase Console.";
                case "ERROR_REQUIRES_RECENT_LOGIN":
                    return "Vui lòng đăng nhập lại để tiếp tục";
                case "ERROR_NETWORK_REQUEST_FAILED":
                    return "Không có kết nối mạng. Kiểm tra lại đường truyền.";
                default:
                    break;
            }
        }
        if (isNetworkError(e)) {
            return "Không có kết nối mạng. Kiểm tra lại đường truyền.";
        }
        return "Đã xảy ra lỗi, vui lòng thử lại";
    }

    private static boolean isNetworkError(Exception e) {
        if (e instanceof java.net.UnknownHostException) {
            return true;
        }
        String message = e.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("network")
                || lower.contains("unable to resolve host")
                || lower.contains("timeout")
                || lower.contains("connect");
    }
}
