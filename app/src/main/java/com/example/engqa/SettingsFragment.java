package com.example.engqa;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.example.engqa.notification.NotificationWatcher;
import com.example.engqa.util.PermissionHelper;
import com.example.engqa.util.PrefsHelper;

import java.io.File;

/**
 * Settings built with {@link PreferenceFragmentCompat}: notification switch, cache cleanup, about
 * and logout. Values live in {@link PrefsHelper} (SharedPreferences).
 */
public class SettingsFragment extends PreferenceFragmentCompat {

    private ActivityResultLauncher<String> notificationPermissionLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (!granted) {
                        Toast.makeText(requireContext(),
                                R.string.settings_notifications_denied, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        Preference notifications = findPreference("notifications_enabled");
        if (notifications != null) {
            notifications.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = Boolean.TRUE.equals(newValue);
                PrefsHelper.setNotificationsEnabled(requireContext(), enabled);
                String uid = new SessionManager(requireContext()).getUserId();
                if (enabled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                            && !PermissionHelper.hasNotifications(requireContext())) {
                        notificationPermissionLauncher.launch(
                                Manifest.permission.POST_NOTIFICATIONS);
                    }
                    if (uid != null) {
                        NotificationWatcher.start(uid);
                    }
                } else {
                    NotificationWatcher.stop();
                }
                return true;
            });
        }

        Preference clearCache = findPreference("clear_cache");
        if (clearCache != null) {
            clearCache.setOnPreferenceClickListener(preference -> {
                deleteRecursively(new File(requireContext().getCacheDir(), "images"));
                deleteRecursively(new File(requireContext().getCacheDir(), "audio"));
                Toast.makeText(requireContext(), R.string.settings_cache_cleared,
                        Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        Preference about = findPreference("about");
        if (about != null) {
            about.setOnPreferenceClickListener(preference -> {
                showAbout();
                return true;
            });
        }

        Preference logout = findPreference("logout");
        if (logout != null) {
            logout.setOnPreferenceClickListener(preference -> {
                confirmLogout();
                return true;
            });
        }
    }

    private void showAbout() {
        String version;
        try {
            version = requireContext().getPackageManager()
                    .getPackageInfo(requireContext().getPackageName(), 0).versionName;
        } catch (Exception e) {
            version = "?";
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_about)
                .setMessage(getString(R.string.settings_about_message, version))
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_logout_confirm_title)
                .setMessage(R.string.settings_logout_confirm_message)
                .setPositiveButton(R.string.settings_logout, (dialog, which) -> {
                    new SessionManager(requireContext()).logout();
                    Intent intent = new Intent(requireContext(), LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    requireActivity().finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
