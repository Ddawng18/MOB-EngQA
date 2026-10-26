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
import androidx.preference.SwitchPreferenceCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.engqa.notification.NotificationWatcher;
import com.example.engqa.util.PermissionHelper;
import com.example.engqa.util.PrefsHelper;
import com.example.engqa.viewmodel.AdminViewModel;

import java.io.File;

/**
 * Settings built with {@link PreferenceFragmentCompat}: notification switch, cache cleanup, about
 * and logout. Values live in {@link PrefsHelper} (SharedPreferences).
 */
public class SettingsFragment extends PreferenceFragmentCompat {

    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private SwitchPreferenceCompat notificationsPreference;
    private AdminViewModel adminViewModel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adminViewModel = new ViewModelProvider(this).get(AdminViewModel.class);
        adminViewModel.getIsAdmin().observe(this, this::syncAdminSection);
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    // Persist the outcome so the switch never claims notifications that the OS blocks.
                    PrefsHelper.setNotificationsEnabled(requireContext(), granted);
                    if (notificationsPreference != null) {
                        notificationsPreference.setChecked(granted);
                    }
                    if (granted) {
                        String uid = new SessionManager(requireContext()).getUserId();
                        if (uid != null) {
                            NotificationWatcher.start(uid);
                        }
                    } else {
                        NotificationWatcher.stop();
                        Toast.makeText(requireContext(),
                                R.string.settings_notifications_denied, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onResume() {
        super.onResume();
        syncNotificationPreference();
        syncAccountPreferences();
        adminViewModel.checkAdmin();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferences, rootKey);

        Preference editProfile = findPreference("edit_profile");
        if (editProfile != null) {
            editProfile.setOnPreferenceClickListener(preference ->
                    openForSignedInUser(EditProfileActivity.class));
        }

        Preference accountSecurity = findPreference("account_security");
        if (accountSecurity != null) {
            accountSecurity.setOnPreferenceClickListener(preference ->
                    openForSignedInUser(AccountSecurityActivity.class));
        }

        Preference moderation = findPreference("admin_moderation");
        if (moderation != null) {
            moderation.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(), AdminActivity.class));
                return true;
            });
        }
        Preference savedPromos = findPreference("saved_promos");
        if (savedPromos != null) {
            savedPromos.setOnPreferenceClickListener(preference -> {
                startActivity(new Intent(requireContext(), SavedPromosActivity.class));
                return true;
            });
        }

        notificationsPreference = findPreference("notifications_enabled");
        if (notificationsPreference != null) {
            notificationsPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = Boolean.TRUE.equals(newValue);
                if (!enabled) {
                    PrefsHelper.setNotificationsEnabled(requireContext(), false);
                    NotificationWatcher.stop();
                    return true;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        && !PermissionHelper.hasNotifications(requireContext())) {
                    // Keep the switch off until the system grants the permission; the result
                    // callback persists the decision.
                    notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS);
                    return false;
                }
                PrefsHelper.setNotificationsEnabled(requireContext(), true);
                String uid = new SessionManager(requireContext()).getUserId();
                if (uid != null) {
                    NotificationWatcher.start(uid);
                }
                return true;
            });
            syncNotificationPreference();
        }

        Preference clearCache = findPreference("clear_cache");
        if (clearCache != null) {
            clearCache.setOnPreferenceClickListener(preference -> {
                File cache = requireContext().getCacheDir();
                deleteRecursively(new File(cache, "images"));
                deleteRecursively(new File(cache, "audio"));
                // Firestore audio is decoded here; this directory was previously missed.
                deleteRecursively(new File(cache, "media"));
                // Glide keeps its own memory and disk caches outside the app directories above.
                final android.content.Context appContext =
                        requireContext().getApplicationContext();
                Glide.get(requireContext()).clearMemory();
                new Thread(() -> Glide.get(appContext).clearDiskCache()).start();
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

    /** Reconciles the stored switch with the OS permission (which the user can revoke outside). */
    private void syncNotificationPreference() {
        if (notificationsPreference == null) {
            return;
        }
        boolean enabled = PrefsHelper.areNotificationsEnabled(requireContext());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !PermissionHelper.hasNotifications(requireContext())) {
            if (enabled) {
                PrefsHelper.setNotificationsEnabled(requireContext(), false);
                NotificationWatcher.stop();
            }
            enabled = false;
        }
        notificationsPreference.setChecked(enabled);
    }

    /** The moderation section only exists for accounts listed in admins/{uid}. */
    private void syncAdminSection(Boolean allowed) {
        Preference section = findPreference("admin_section");
        if (section != null) {
            section.setVisible(Boolean.TRUE.equals(allowed));
        }
    }

    /** Account screens only make sense with a session; otherwise send the user to sign in. */
    private boolean openForSignedInUser(Class<?> target) {
        if (!new SessionManager(requireContext()).isLoggedIn()) {
            startActivity(new Intent(requireContext(), LoginActivity.class));
            return true;
        }
        startActivity(new Intent(requireContext(), target));
        return true;
    }

    /** Keeps the hub honest: real email in the summary, no logout row while signed out. */
    private void syncAccountPreferences() {
        boolean loggedIn = new SessionManager(requireContext()).isLoggedIn();
        String email = loggedIn ? new SessionManager(requireContext()).getEmail() : null;

        Preference editProfile = findPreference("edit_profile");
        if (editProfile != null) {
            editProfile.setSummary(loggedIn
                    ? R.string.settings_edit_profile_summary : R.string.settings_signed_out);
        }
        Preference security = findPreference("account_security");
        if (security != null) {
            security.setSummary(email != null ? email : getString(R.string.settings_signed_out));
        }
        Preference logout = findPreference("logout");
        if (logout != null) {
            logout.setVisible(loggedIn);
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
