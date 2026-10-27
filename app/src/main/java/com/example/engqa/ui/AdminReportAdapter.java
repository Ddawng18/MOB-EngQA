package com.example.engqa.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.engqa.R;
import com.example.engqa.model.Report;
import com.example.engqa.util.ReportTargets;
import com.example.engqa.util.TimeUtils;

/** Pending content reports in the moderation console. */
public class AdminReportAdapter extends ListAdapter<Report, AdminReportAdapter.ReportViewHolder> {

    public interface Listener {
        void onOpen(Report report);

        void onDismiss(Report report);

        void onWarn(Report report);

        void onDelete(Report report);
    }

    private static final DiffUtil.ItemCallback<Report> DIFF =
            new DiffUtil.ItemCallback<Report>() {
                @Override
                public boolean areItemsTheSame(@NonNull Report oldItem, @NonNull Report newItem) {
                    return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Report oldItem, @NonNull Report newItem) {
                    return oldItem.getStatus() != null
                            && oldItem.getStatus().equals(newItem.getStatus());
                }
            };

    private final Listener listener;

    public AdminReportAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ReportViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_report, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class ReportViewHolder extends RecyclerView.ViewHolder {

        private final TextView target;
        private final TextView reason;
        private final TextView reporter;
        private final TextView time;

        ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            target = itemView.findViewById(R.id.tvReportTarget);
            reason = itemView.findViewById(R.id.tvReportReason);
            reporter = itemView.findViewById(R.id.tvReportReporter);
            time = itemView.findViewById(R.id.tvReportTime);
        }

        void bind(Report report, Listener listener) {
            target.setText(targetLabel(report.getTargetPath()));
            reason.setText(report.getReason());
            reporter.setText(itemView.getContext()
                    .getString(R.string.admin_reporter, report.getReporterName()));
            time.setText(TimeUtils.relative(report.getCreatedAt()));
            itemView.setOnClickListener(v -> listener.onOpen(report));

            itemView.findViewById(R.id.btnDismissReport)
                    .setOnClickListener(v -> listener.onDismiss(report));
            itemView.findViewById(R.id.btnWarnReport)
                    .setOnClickListener(v -> listener.onWarn(report));
            itemView.findViewById(R.id.btnDeleteReport)
                    .setOnClickListener(v -> listener.onDelete(report));
        }

        private String targetLabel(String path) {
            switch (ReportTargets.parse(path).getType()) {
                case PROMO:
                    return itemView.getContext().getString(R.string.admin_target_promo);
                case ANSWER:
                    return itemView.getContext().getString(R.string.admin_target_answer);
                case QUESTION:
                default:
                    return itemView.getContext().getString(R.string.admin_target_question);
            }
        }
    }
}
