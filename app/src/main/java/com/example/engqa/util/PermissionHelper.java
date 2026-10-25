package com.example.engqa.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

/** Builds the runtime-permission requests needed by the camera, audio and notification features. */
public final class PermissionHelper {

    private PermissionHelper() {
    }

    public static boolean hasCamera(Context context) {
        return granted(context, Manifest.permission.CAMERA);
    }

    public static boolean hasRecordAudio(Context context) {
        return granted(context, Manifest.permission.RECORD_AUDIO);
    }

    public static boolean hasNotifications(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return granted(context, Manifest.permission.POST_NOTIFICATIONS);
    }

    private static boolean granted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED;
    }
}
