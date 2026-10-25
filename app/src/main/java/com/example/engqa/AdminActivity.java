package com.example.engqa;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.engqa.model.Center;
import com.example.engqa.model.Promo;
import com.example.engqa.model.Report;
import com.example.engqa.model.UiState;
import com.example.engqa.ui.AdminCenterAdapter;
import com.example.engqa.ui.AdminPromoAdapter;
import com.example.engqa.ui.AdminReportAdapter;
import com.example.engqa.util.Navigation;
import com.example.engqa.viewmodel.AdminViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Moderation console (admins only).
 *
 * <p>Three queues: centre verification, promo approval and content reports. Approving a centre is
 * the only action that changes a user's privileges; it promotes the owner to {@code CENTER} and
 * points the profile at the new centre so the "create promo" entry becomes usable.
 */
public class AdminActivity extends AppCompatActivity
        implements AdminCenterAdapter.Listener,
        AdminPromoAdapter.Listener,
        AdminReportAdapter.Listener {

    private static final int TAB_CENTERS = 0;
    private static final int TAB_PROMOS = 1;
    private static final int TAB_REPORTS = 2;

    private AdminViewModel viewModel;
    private AdminCenterAdapter centerAdapter;
    private AdminPromoAdapter promoAdapter;
    private AdminReportAdapter reportAdapter;
    private RecyclerView recycler;
    private TextView emptyView;
    private TextView tabCenters;
    private TextView tabPromos;
    private TextView tabReports;
    private int selectedTab = TAB_CENTERS;
    private Boolean adminAllowed;

    private List<Center> centers = new ArrayList<>();
    private List<Promo> promos = new ArrayList<>();
    private List<Report> reports = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = getColor(R.color.bg_screen);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_admin);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        recycler = findViewById(R.id.recyclerModeration);
        emptyView = findViewById(R.id.tvAdminEmpty);
        tabCenters = findViewById(R.id.btnTabCenters);
        tabPromos = findViewById(R.id.btnTabPromos);
        tabReports = findViewById(R.id.btnTabReports);

        recycler.setLayoutManager(new LinearLayoutManager(this));
        centerAdapter = new AdminCenterAdapter(this);
        promoAdapter = new AdminPromoAdapter(this);
        reportAdapter = new AdminReportAdapter(this);

        Navigation.wireBack(this);
        tabCenters.setOnClickListener(v -> selectTab(TAB_CENTERS));
        tabPromos.setOnClickListener(v -> selectTab(TAB_PROMOS));
        tabReports.setOnClickListener(v -> selectTab(TAB_REPORTS));

        viewModel = new ViewModelProvider(this).get(AdminViewModel.class);
        observe();
        viewModel.checkAdmin();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Re-register after returning from a detail screen. A normal account never issues the
        // admin-only queries because adminAllowed stays false.
        if (Boolean.TRUE.equals(adminAllowed)) {
            viewModel.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        viewModel.stop();
    }

    private void observe() {
        viewModel.getIsAdmin().observe(this, allowed -> {
            adminAllowed = allowed;
            if (Boolean.TRUE.equals(allowed)) {
                viewModel.start();
                return;
            }
            Toast.makeText(this, R.string.admin_not_allowed, Toast.LENGTH_LONG).show();
            finish();
        });

        viewModel.getCenters().observe(this, state -> {
            if (state == null) {
                return;
            }
            if (state.getStatus() == UiState.Status.ERROR) {
                toast(state.getMessage());
            } else if (state.getStatus() == UiState.Status.SUCCESS && state.getData() != null) {
                centers = state.getData();
                updateCounts();
                if (selectedTab == TAB_CENTERS) {
                    render();
                }
            }
        });

        viewModel.getPromos().observe(this, state -> {
            if (state == null) {
                return;
            }
            if (state.getStatus() == UiState.Status.ERROR) {
                toast(state.getMessage());
            } else if (state.getStatus() == UiState.Status.SUCCESS && state.getData() != null) {
                promos = state.getData();
                updateCounts();
                if (selectedTab == TAB_PROMOS) {
                    render();
                }
            }
        });

        viewModel.getReports().observe(this, state -> {
            if (state == null) {
                return;
            }
            if (state.getStatus() == UiState.Status.ERROR) {
                toast(state.getMessage());
            } else if (state.getStatus() == UiState.Status.SUCCESS && state.getData() != null) {
                reports = state.getData();
                updateCounts();
                if (selectedTab == TAB_REPORTS) {
                    render();
                }
            }
        });

        viewModel.getActionState().observe(this, state -> {
            if (state == null) {
                return;
            }
            if (state.getStatus() == UiState.Status.SUCCESS) {
                toast(messageFor(state.getData()));
            } else if (state.getStatus() == UiState.Status.ERROR) {
                toast(state.getMessage());
            }
        });
    }

    private void selectTab(int tab) {
        selectedTab = tab;
        styleTabs();
        render();
    }

    private void styleTabs() {
        styleTab(tabCenters, selectedTab == TAB_CENTERS);
        styleTab(tabPromos, selectedTab == TAB_PROMOS);
        styleTab(tabReports, selectedTab == TAB_REPORTS);
    }

    private void styleTab(TextView tab, boolean active) {
        tab.setBackgroundResource(active ? R.drawable.bg_chip_active : R.drawable.bg_chip);
        tab.setTextColor(ContextCompat.getColor(this,
                active ? R.color.accent_orange : R.color.text_secondary));
    }

    private void updateCounts() {
        tabCenters.setText(getString(R.string.admin_tab_with_count,
                getString(R.string.admin_tab_centers), centers.size()));
        tabPromos.setText(getString(R.string.admin_tab_with_count,
                getString(R.string.admin_tab_promos), promos.size()));
        tabReports.setText(getString(R.string.admin_tab_with_count,
                getString(R.string.admin_tab_reports), reports.size()));
    }

    private void render() {
        int emptyText;
        int count;
        switch (selectedTab) {
            case TAB_PROMOS:
                recycler.setAdapter(promoAdapter);
                promoAdapter.submitList(promos);
                count = promos.size();
                emptyText = R.string.admin_empty_promos;
                break;
            case TAB_REPORTS:
                recycler.setAdapter(reportAdapter);
                reportAdapter.submitList(reports);
                count = reports.size();
                emptyText = R.string.admin_empty_reports;
                break;
            case TAB_CENTERS:
            default:
                recycler.setAdapter(centerAdapter);
                centerAdapter.submitList(centers);
                count = centers.size();
                emptyText = R.string.admin_empty_centers;
                break;
        }
        emptyView.setText(emptyText);
        emptyView.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
        recycler.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        findViewById(R.id.progressAdmin).setVisibility(View.GONE);
    }

    // --------------------------------------------------------- centre queue

    @Override
    public void onApprove(Center center) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_approve_center_title)
                .setMessage(getString(R.string.admin_approve_center_message, center.getName()))
                .setPositiveButton(R.string.admin_approve,
                        (dialog, which) -> viewModel.approveCenter(center))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onReject(Center center) {
        promptReject(R.string.admin_reject_center_title,
                note -> viewModel.rejectCenter(center, note));
    }

    // ---------------------------------------------------------- promo queue

    @Override
    public void onApprove(Promo promo) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_approve_promo_title)
                .setMessage(getString(R.string.admin_approve_promo_message, promo.getTitle()))
                .setPositiveButton(R.string.admin_approve,
                        (dialog, which) -> viewModel.approvePromo(promo))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onReject(Promo promo) {
        promptReject(R.string.admin_reject_promo_title,
                note -> viewModel.rejectPromo(promo, note));
    }

    // --------------------------------------------------------- report queue

    /** Opens the reported item so the moderator can judge it before acting. */
    @Override
    public void onOpen(Report report) {
        String path = report.getTargetPath();
        if (path == null) {
            return;
        }
        if (path.startsWith("questions/")) {
            startActivity(new Intent(this, QuestionDetailActivity.class)
                    .putExtra(QuestionDetailActivity.EXTRA_QUESTION_ID,
                            path.substring("questions/".length())));
        } else if (path.startsWith("promos/")) {
            startActivity(new Intent(this, PromoDetailActivity.class)
                    .putExtra(PromoDetailActivity.EXTRA_PROMO_ID,
                            path.substring("promos/".length())));
        }
    }

    @Override
    public void onWarn(Report report) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_report_warn_title)
                .setMessage(R.string.admin_report_warn_message)
                .setPositiveButton(R.string.admin_warn,
                        (dialog, which) -> viewModel.warnReport(report))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onDelete(Report report) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_report_delete_title)
                .setMessage(R.string.admin_report_delete_message)
                .setPositiveButton(R.string.admin_delete_post,
                        (dialog, which) -> viewModel.deleteReportedPost(report))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onDismiss(Report report) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_report_dismiss_title)
                .setMessage(R.string.admin_report_dismiss_message)
                .setPositiveButton(R.string.admin_dismiss,
                        (dialog, which) -> viewModel.dismissReport(report, null))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // -------------------------------------------------------------- helpers

    private interface NoteAction {
        void run(String note);
    }

    private void promptReject(int title, NoteAction action) {
        EditText input = new EditText(this);
        input.setHint(R.string.admin_reject_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding / 2, padding, 0);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(input)
                .setPositiveButton(R.string.admin_reject,
                        (dialog, which) -> action.run(input.getText().toString().trim()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private int messageFor(String code) {
        if ("center_approved".equals(code)) {
            return R.string.admin_center_approved;
        }
        if ("center_rejected".equals(code)) {
            return R.string.admin_center_rejected;
        }
        if ("promo_approved".equals(code)) {
            return R.string.admin_promo_approved;
        }
        if ("promo_rejected".equals(code)) {
            return R.string.admin_promo_rejected;
        }
        if ("report_warned".equals(code)) {
            return R.string.admin_report_warned;
        }
        if ("report_deleted".equals(code)) {
            return R.string.admin_report_deleted;
        }
        if ("report_dismissed".equals(code)) {
            return R.string.admin_report_dismissed;
        }
        return R.string.ok;
    }

    private void toast(String message) {
        if (message != null) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void toast(int message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
