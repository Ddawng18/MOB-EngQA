package com.example.engqa;

import android.content.Intent;
import android.graphics.Typeface;
import android.widget.TextView;

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
    protected static final int NAV_SAVED = 2;

    /** The bottom-nav tab that this screen belongs to. */
    protected abstract int selectedNavItem();

    /** Wire the three nav items. Call from {@code onCreate} after {@code setContentView}. */
    protected void bindBottomNav() {
        setupNavItem(findViewById(R.id.navHome), NAV_HOME);
        setupNavItem(findViewById(R.id.navAsk), NAV_ASK);
        setupNavItem(findViewById(R.id.navSaved), NAV_SAVED);
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
            case NAV_SAVED:
                target = ServerErrorActivity.class;
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
