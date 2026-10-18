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
import com.example.engqa.model.Course;

/** Renders a centre's courses. */
public class CourseAdapter extends ListAdapter<Course, CourseAdapter.CourseViewHolder> {

    private static final DiffUtil.ItemCallback<Course> DIFF = new DiffUtil.ItemCallback<Course>() {
        @Override
        public boolean areItemsTheSame(@NonNull Course oldItem, @NonNull Course newItem) {
            return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Course oldItem, @NonNull Course newItem) {
            return eq(oldItem.getTitle(), newItem.getTitle())
                    && eq(oldItem.getSchedule(), newItem.getSchedule());
        }

        private boolean eq(String a, String b) {
            return a == null ? b == null : a.equals(b);
        }
    };

    public CourseAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public CourseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_course, parent, false);
        return new CourseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CourseViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class CourseViewHolder extends RecyclerView.ViewHolder {

        private final TextView title;
        private final TextView level;
        private final TextView schedule;
        private final TextView description;

        CourseViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.tvCourseTitle);
            level = itemView.findViewById(R.id.tvCourseLevel);
            schedule = itemView.findViewById(R.id.tvCourseSchedule);
            description = itemView.findViewById(R.id.tvCourseDescription);
        }

        void bind(Course course) {
            title.setText(course.getTitle());
            level.setText(course.getLevel());
            schedule.setText(course.getSchedule());
            description.setText(course.getDescription());
        }
    }
}
