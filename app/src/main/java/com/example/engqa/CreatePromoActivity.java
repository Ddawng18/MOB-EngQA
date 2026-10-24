package com.example.engqa;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.engqa.model.Center;
import com.example.engqa.model.Promo;
import com.example.engqa.model.UiState;
import com.example.engqa.model.User;
import com.example.engqa.util.PromoValidator;
import com.example.engqa.viewmodel.CreatePromoViewModel;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * "Đăng bài quảng bá": a CENTER account composes a post, picks an optional deadline and submits it
 * in {@code PENDING} state. Returns {@code RESULT_OK} so the caller can show a confirmation.
 */
public class CreatePromoActivity extends AppCompatActivity
        implements DatePickerFragment.OnDateSetListener {

    private static final String[] TYPES = {
            Promo.TYPE_COURSE, Promo.TYPE_EVENT,
            Promo.TYPE_SCHOLARSHIP, Promo.TYPE_ANNOUNCEMENT
    };
    private static final int[] TYPE_LABELS = {
            R.string.promo_type_course, R.string.promo_type_event,
            R.string.promo_type_scholarship, R.string.promo_type_announcement
    };

    private CreatePromoViewModel viewModel;

    private EditText etTitle;
    private EditText etBody;
    private Spinner spinnerType;
    private EditText etLevel;
    private EditText etFee;
    private EditText etSchedule;
    private EditText etPhone;
    private EditText etLink;
    private EditText etImage;
    private TextView tvDeadline;
    private TextView tvFormError;
    private TextView btnSubmit;
    private ProgressBar progress;

    private Date endAt;
    private User loadedUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int barColor = getColor(R.color.bg_topbar);
        EdgeToEdge.enable(this, SystemBarStyle.dark(barColor), SystemBarStyle.dark(barColor));
        setContentView(R.layout.activity_create_promo);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.createPromoRoot),
                (v, insets) -> {
                    Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.ime());
                    v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                    return insets;
                });

        String uid = new SessionManager(this).getUserId();
        if (uid == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(CreatePromoViewModel.class);

        etTitle = findViewById(R.id.etPromoTitle);
        etBody = findViewById(R.id.etPromoBody);
        spinnerType = findViewById(R.id.spinnerPromoType);
        etLevel = findViewById(R.id.etPromoLevel);
        etFee = findViewById(R.id.etPromoFee);
        etSchedule = findViewById(R.id.etPromoSchedule);
        etPhone = findViewById(R.id.etPromoPhone);
        etLink = findViewById(R.id.etPromoLink);
        etImage = findViewById(R.id.etPromoImage);
        tvDeadline = findViewById(R.id.tvPromoDeadline);
        tvFormError = findViewById(R.id.tvFormError);
        btnSubmit = findViewById(R.id.btnSubmitPromo);
        progress = findViewById(R.id.progressPromo);

        setupTypeSpinner();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnPickDate).setOnClickListener(v ->
                DatePickerFragment.newInstance(endAt)
                        .show(getSupportFragmentManager(), "deadline"));
        tvDeadline.setOnClickListener(v ->
                DatePickerFragment.newInstance(endAt)
                        .show(getSupportFragmentManager(), "deadline"));
        tvDeadline.setOnLongClickListener(v -> {
            endAt = null;
            tvDeadline.setText(R.string.promo_no_deadline);
            tvDeadline.setTextColor(getColor(R.color.text_hint));
            return true;
        });
        btnSubmit.setOnClickListener(v -> submit());

        observe(uid);
    }

    @Override
    protected void onStart() {
        super.onStart();
        viewModel.start(new SessionManager(this).getUserId());
    }

    @Override
    protected void onStop() {
        super.onStop();
        viewModel.stop();
    }

    @Override
    public void onDateSet(int year, int month, int dayOfMonth) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, dayOfMonth, 23, 59, 59);
        calendar.set(Calendar.MILLISECOND, 0);
        endAt = calendar.getTime();
        tvDeadline.setText(new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                .format(endAt));
        tvDeadline.setTextColor(getColor(R.color.text_primary));
    }

    private void setupTypeSpinner() {
        String[] labels = new String[TYPE_LABELS.length];
        for (int i = 0; i < TYPE_LABELS.length; i++) {
            labels[i] = getString(TYPE_LABELS[i]);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, labels) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(getColor(R.color.text_primary));
                tv.setTextSize(14);
                tv.setPadding(dp(8), 0, 0, 0);
                return tv;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextColor(getColor(R.color.text_primary));
                tv.setTextSize(14);
                tv.setPadding(dp(16), dp(12), dp(16), dp(12));
                return tv;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(adapter);
    }

    private void observe(String uid) {
        viewModel.getUser().observe(this, state -> {
            if (state == null) {
                return;
            }
            if (state.getStatus() == UiState.Status.SUCCESS) {
                loadedUser = state.getData();
                if (loadedUser != null && !User.ROLE_CENTER.equals(loadedUser.getRole())) {
                    Toast.makeText(this, R.string.promo_center_only, Toast.LENGTH_LONG).show();
                    finish();
                }
            } else if (state.getStatus() == UiState.Status.ERROR) {
                showError(state.getMessage());
            }
        });

        viewModel.getCenter().observe(this, state -> {
            if (state != null && state.getStatus() == UiState.Status.SUCCESS) {
                Center center = state.getData();
                if (center != null) {
                    ((TextView) findViewById(R.id.tvPromoCenterName)).setText(center.getName());
                    if (TextUtils.isEmpty(etPhone.getText())) {
                        etPhone.setText(center.getPhone());
                    }
                    if (TextUtils.isEmpty(etLink.getText())) {
                        etLink.setText(center.getWebsite());
                    }
                }
            }
        });

        viewModel.getSubmitState().observe(this, state -> {
            if (state == null) {
                return;
            }
            switch (state.getStatus()) {
                case LOADING:
                    progress.setVisibility(View.VISIBLE);
                    btnSubmit.setEnabled(false);
                    break;
                case SUCCESS:
                    progress.setVisibility(View.GONE);
                    btnSubmit.setEnabled(true);
                    Toast.makeText(this, R.string.promo_submitted, Toast.LENGTH_LONG).show();
                    setResult(RESULT_OK);
                    finish();
                    break;
                case ERROR:
                default:
                    progress.setVisibility(View.GONE);
                    btnSubmit.setEnabled(true);
                    showError(state.getMessage());
                    break;
            }
        });
    }

    private void submit() {
        hideError();
        String title = etTitle.getText().toString();
        String body = etBody.getText().toString();
        String phone = etPhone.getText().toString();
        String link = etLink.getText().toString();
        String image = etImage.getText().toString();

        String titleError = PromoValidator.titleError(title);
        if (titleError != null) {
            etTitle.setError(titleError);
            etTitle.requestFocus();
            showError(titleError);
            return;
        }
        String bodyError = PromoValidator.bodyError(body);
        if (bodyError != null) {
            etBody.setError(bodyError);
            etBody.requestFocus();
            showError(bodyError);
            return;
        }
        String phoneError = PromoValidator.phoneError(phone);
        if (phoneError != null) {
            etPhone.setError(phoneError);
            etPhone.requestFocus();
            showError(phoneError);
            return;
        }
        String linkError = PromoValidator.urlError(link);
        if (linkError != null) {
            etLink.setError(linkError);
            etLink.requestFocus();
            showError(linkError);
            return;
        }
        String imageError = PromoValidator.imageUrlError(image);
        if (imageError != null) {
            etImage.setError(imageError);
            etImage.requestFocus();
            showError(imageError);
            return;
        }

        String type = TYPES[Math.max(0, spinnerType.getSelectedItemPosition())];
        viewModel.submit(title, body, type,
                etLevel.getText().toString().trim(),
                etFee.getText().toString().trim(),
                etSchedule.getText().toString().trim(),
                phone.trim(), link.trim(),
                image.trim().isEmpty() ? null : image.trim(),
                endAt);
    }

    private void showError(String message) {
        tvFormError.setText(message);
        tvFormError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        tvFormError.setVisibility(View.GONE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
