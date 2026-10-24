package com.example.engqa;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * Base class for every screen that shows the shared bottom navigation bar.
 * <p>
 * The bar itself lives in {@code layout/view_bottom_nav.xml} and is included by each screen, so it
 * looks identical everywhere. Tab switches reuse an existing instance of the target screen
 * ({@link Intent#FLAG_ACTIVITY_REORDER_TO_FRONT}) instead of recreating it, which preserves each
 * screen's state (scroll position, form input, ...) while the bar stays visible.
 */
public abstract class BaseNavActivity extends AppCompatActivity {

    protected static final int NAV_HOME = 0;
    protected static final int NAV_ASK = 1;
    protected static final int NAV_PROFILE = 2;

    /** The bottom-nav tab that this screen belongs to. */
    protected abstract int selectedNavItem();

    /** {@code true} only for the three root tab screens (not detail screens using the same bar). */
    protected boolean isTabActivity() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isTabActivity()) {
            return;
        }
        // Predictive-back compatible: only the root tabs intercept Back.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (selectedNavItem() != NAV_HOME) {
                    // Back from Ask/Profile returns to the start destination, not the last tab.
                    navigateTo(NAV_HOME);
                    return;
                }
                // At the root, leave the app instead of surfacing a previously visited tab.
                moveTaskToBack(true);
            }
        });
    }

    /** Wire the three nav items. Call from {@code onCreate} after {@code setContentView}. */
    protected void bindBottomNav() {
        setupNavItem(findViewById(R.id.navHome), NAV_HOME);
        setupNavItem(findViewById(R.id.navAsk), NAV_ASK);
        setupNavItem(findViewById(R.id.navProfile), NAV_PROFILE);
    }

    private void setupNavItem(TextView item, int navItem) {
        if (item == null) {
            return;
        }
        boolean active = navItem == selectedNavItem();
        item.setTextColor(ContextCompat.getColor(this,
                active ? R.color.accent_orange : R.color.text_secondary));
        item.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
        item.setOnClickListener(view -> navigateTo(navItem));
    }

    protected void navigateTo(int navItem) {
        if (navItem == selectedNavItem()) {
            return;
        }
        Class<?> target;
        switch (navItem) {
            case NAV_ASK:
                target = MainActivity.class;
                break;
            case NAV_PROFILE:
                target = MenuScreenActivity.class;
                break;
            case NAV_HOME:
            default:
                target = HomeActivity.class;
                break;
        }
        Intent intent = new Intent(this, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }
}
