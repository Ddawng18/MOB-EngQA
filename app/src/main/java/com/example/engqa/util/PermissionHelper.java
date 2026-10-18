package com.example.engqa.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/** Builds the runtime-permission requests needed by the media and notification features. */
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

    /** Permissions still required to read images/audio from the device gallery or picker. */
    public static String[] missingMediaPermissions(Context context) {
        List<String> missing = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!granted(context, Manifest.permission.READ_MEDIA_IMAGES)) {
                missing.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (!granted(context, Manifest.permission.READ_MEDIA_AUDIO)) {
                missing.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
        }
        return missing.toArray(new String[0]);
    }

    private static boolean granted(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission)
                == PackageManager.PERMISSION_GRANTED;
    }
}
