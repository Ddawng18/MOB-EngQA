package com.example.engqa;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.engqa.model.Promo;
import com.example.engqa.model.UiState;
import com.example.engqa.ui.StateView;
import com.example.engqa.viewmodel.PromoViewModel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import com.example.engqa.util.Navigation;

/**
 * Promo detail: reads the promo by id from Firestore, counts one view per opening, opens the
 * website/phone through implicit intents, saves locally and opens the consultation dialog.
 */
public class PromoDetailActivity extends AppCompatActivity {

    public static final String EXTRA_PROMO_ID = "com.example.engqa.extra.PROMO_ID";

    private PromoViewModel viewModel;
    private StateView stateView;
    private TextView tvSave;
    private Promo currentPromo;
    private String promoId;
    private String uid;
    private boolean saved;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = getColor(R.color.bg_topbar);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_promo_detail);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.promoDetailRoot),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.ime());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        promoId = getIntent().getStringExtra(EXTRA_PROMO_ID);
        if (promoId == null || promoId.isEmpty()) {
            Toast.makeText(this, "Không tìm thấy bài đăng", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        uid = new SessionManager(this).getUserId();

        viewModel = new ViewModelProvider(this).get(PromoViewModel.class);
        stateView = findViewById(R.id.stateView);
        tvSave = findViewById(R.id.btnSavePromo);

        Navigation.wireBack(this);
        findViewById(R.id.btnWebsite).setOnClickListener(v -> openWebsite());
        findViewById(R.id.btnCall).setOnClickListener(v -> callNow());
        findViewById(R.id.btnRegisterLead).setOnClickListener(v -> openLeadDialog());
        tvSave.setOnClickListener(v -> toggleSaved());
        findViewById(R.id.btnReportPromo).setOnClickListener(v -> reportPromo());

        stateView.setOnRetryListener(v -> {
            viewModel.stop();
            viewModel.start(promoId);
        });

        viewModel.getPromo().observe(this, this::render);
        viewModel.getActionState().observe(this, state -> {
            if (state == null) {
                return;
            }
            if (state.getStatus() == UiState.Status.SUCCESS
                    && "lead_submitted".equals(state.getData())) {
                Toast.makeText(this, R.string.lead_success, Toast.LENGTH_LONG).show();
            } else if (state.getStatus() == UiState.Status.ERROR) {
                Toast.makeText(this, state.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        viewModel.getSaved().observe(this, value -> {
            saved = Boolean.TRUE.equals(value);
            updateSaveButton();
        });
        viewModel.getSaveEvent().observe(this, message -> {
            if (message == null) {
                return;
            }
            if ("saved".equals(message)) {
                Toast.makeText(this, R.string.promo_saved, Toast.LENGTH_SHORT).show();
            } else if ("removed".equals(message)) {
                Toast.makeText(this, R.string.saved_removed, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        viewModel.start(promoId);
        viewModel.refreshSaved();
    }

    @Override
    protected void onStop() {
        super.onStop();
        viewModel.stop();
    }

    // ------------------------------------------------------------------ render

    private void render(UiState<Promo> state) {
        if (state == null) {
            return;
        }
        switch (state.getStatus()) {
            case LOADING:
                stateView.showLoading();
                break;
            case SUCCESS:
                stateView.showContent();
                bind(state.getData());
                break;
            case ERROR:
            default:
                stateView.showError(state.getMessage());
                break;
        }
    }

    private void bind(Promo promo) {
        currentPromo = promo;
        if (promo == null) {
            return;
        }
        ((TextView) findViewById(R.id.tvPromoCenter)).setText(promo.getCenterName());
        findViewById(R.id.ivPromoVerified)
                .setVisibility(promo.isCenterVerified() ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.tvPromoType))
                .setText(typeLabel(promo.getType()));
        ((TextView) findViewById(R.id.tvPromoTitle)).setText(promo.getTitle());
        ((TextView) findViewById(R.id.tvPromoBody)).setText(promo.getBody());
        ((TextView) findViewById(R.id.tvPromoLevel)).setText(orDash(promo.getLevel()));
        ((TextView) findViewById(R.id.tvPromoFee)).setText(orDash(promo.getFee()));
        ((TextView) findViewById(R.id.tvPromoSchedule)).setText(orDash(promo.getSchedule()));
        ((TextView) findViewById(R.id.tvPromoDeadline))
                .setText(promo.getEndAt() != null ? formatDate(promo.getEndAt()) : "—");

        String stats = getString(R.string.promo_views, compact(promo.getViewCount()))
                + " · " + getString(R.string.promo_leads, promo.getLeadCount());
        ((TextView) findViewById(R.id.tvPromoStats)).setText(stats);

        boolean expired = promo.isExpired(System.currentTimeMillis());
        findViewById(R.id.tvPromoExpired).setVisibility(expired ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnRegisterLead).setVisibility(expired ? View.GONE : View.VISIBLE);
        findViewById(R.id.btnWebsite).setVisibility(
                promo.getLinkUrl() != null && !promo.getLinkUrl().isEmpty()
                        ? View.VISIBLE : View.GONE);
        findViewById(R.id.btnCall).setVisibility(
                promo.getContactPhone() != null && !promo.getContactPhone().isEmpty()
                        ? View.VISIBLE : View.GONE);

        ImageView banner = findViewById(R.id.ivPromoBanner);
        if (promo.getImageUrl() != null && !promo.getImageUrl().isEmpty()) {
            banner.setVisibility(View.VISIBLE);
            Glide.with(this).load(promo.getImageUrl()).centerCrop().into(banner);
        } else {
            banner.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------------ actions

    /** Sends an approved promo to the same moderation queue as question reports. */
    private void reportPromo() {
        if (uid == null) {
            Toast.makeText(this, R.string.login_required, Toast.LENGTH_SHORT).show();
            return;
        }
        ReportDialogFragment.newInstance("promos/" + promoId)
                .show(getSupportFragmentManager(), "report");
    }

    private void openWebsite() {
        if (currentPromo == null || currentPromo.getLinkUrl() == null) {
            return;
        }
        viewModel.onContactClick();
        try {
            // Start directly: resolveActivity() can return null under Android 11+ package
            // visibility even when a browser is installed.
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(currentPromo.getLinkUrl())));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.promo_no_browser, Toast.LENGTH_LONG).show();
        }
    }

    private void callNow() {
        if (currentPromo == null || currentPromo.getContactPhone() == null) {
            return;
        }
        viewModel.onContactClick();
        try {
            // ACTION_DIAL only opens the dialer, so no CALL_PHONE permission is needed.
            startActivity(new Intent(Intent.ACTION_DIAL,
                    Uri.parse("tel:" + currentPromo.getContactPhone())));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.promo_no_dialer, Toast.LENGTH_LONG).show();
        }
    }

    private void openLeadDialog() {
        if (uid == null) {
            Toast.makeText(this, "Bạn cần đăng nhập để đăng ký tư vấn", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, LoginActivity.class));
            return;
        }
        LeadDialogFragment.newInstance(promoId, uid)
                .show(getSupportFragmentManager(), "lead");
    }

    private void toggleSaved() {
        viewModel.toggleSaved(currentPromo);
    }

    private void updateSaveButton() {
        tvSave.setText(saved ? R.string.unsave_promo : R.string.save_promo);
    }

    // ------------------------------------------------------------------ helpers

    private String typeLabel(String type) {
        int resId = R.string.promo_type_course;
        if (Promo.TYPE_EVENT.equals(type)) {
            resId = R.string.promo_type_event;
        } else if (Promo.TYPE_SCHOLARSHIP.equals(type)) {
            resId = R.string.promo_type_scholarship;
        } else if (Promo.TYPE_ANNOUNCEMENT.equals(type)) {
            resId = R.string.promo_type_announcement;
        }
        return getString(resId);
    }

    private static String orDash(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value;
    }

    private static String formatDate(Date date) {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date);
    }

    /** 1500 -> "1.5K" for the compact view counter. */
    private static String compact(long value) {
        if (value >= 1000) {
            return String.format(Locale.getDefault(), "%.1fK", value / 1000.0);
        }
        return String.valueOf(value);
    }
}
