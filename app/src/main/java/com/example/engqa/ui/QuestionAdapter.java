package com.example.engqa.ui;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.engqa.R;
import com.example.engqa.media.ThumbnailLoader;
import com.example.engqa.model.Question;
import com.example.engqa.util.TimeUtils;

import java.util.List;

/** Renders the question feed. {@link ListAdapter} + {@link DiffUtil} means realtime updates only
 * redraw the rows that actually changed. */
public class QuestionAdapter extends ListAdapter<Question, QuestionAdapter.QuestionViewHolder> {

    public interface OnQuestionClickListener {
        void onQuestionClick(Question question);
    }

    private static final DiffUtil.ItemCallback<Question> DIFF =
            new DiffUtil.ItemCallback<Question>() {
                @Override
                public boolean areItemsTheSame(@NonNull Question oldItem, @NonNull Question newItem) {
                    return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Question oldItem,
                                                  @NonNull Question newItem) {
                    return oldItem.getAnswerCount() == newItem.getAnswerCount()
                            && oldItem.getScore() == newItem.getScore()
                            && oldItem.isSolved() == newItem.isSolved()
                            && eq(oldItem.getTitle(), newItem.getTitle());
                }

                private boolean eq(String a, String b) {
                    return a == null ? b == null : a.equals(b);
                }
            };

    private final OnQuestionClickListener listener;

    public QuestionAdapter(OnQuestionClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public QuestionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_question, parent, false);
        return new QuestionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QuestionViewHolder holder, int position) {
        holder.bind(getItem(position), listener);
    }

    static class QuestionViewHolder extends RecyclerView.ViewHolder {

        private final TextView avatar;
        private final TextView user;
        private final TextView time;
        private final TextView tag;
        private final TextView title;
        private final TextView answerCount;
        private final TextView solved;
        private final ImageView imageIcon;
        private final ImageView audioIcon;
        private final View imageWrap;
        private final ImageView thumbnail;
        private final TextView imageCount;

        QuestionViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.tvQuestionAvatar);
            user = itemView.findViewById(R.id.tvQuestionUser);
            time = itemView.findViewById(R.id.tvQuestionTime);
            tag = itemView.findViewById(R.id.tvQuestionTag);
            title = itemView.findViewById(R.id.tvQuestionTitle);
            answerCount = itemView.findViewById(R.id.tvQuestionAnswers);
            solved = itemView.findViewById(R.id.tvQuestionSolved);
            imageIcon = itemView.findViewById(R.id.ivHasImage);
            audioIcon = itemView.findViewById(R.id.ivHasAudio);
            imageWrap = itemView.findViewById(R.id.questionImageWrap);
            thumbnail = itemView.findViewById(R.id.ivQuestionThumbnail);
            imageCount = itemView.findViewById(R.id.tvQuestionImageCount);
        }

        void bind(Question question, OnQuestionClickListener listener) {
            String name = question.getAuthorName() != null ? question.getAuthorName() : "user";
            avatar.setText(name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase());
            user.setText(name);
            time.setText(TimeUtils.relative(question.getCreatedAt()));
            tag.setText(question.getCategoryName() != null
                    ? question.getCategoryName() : question.getCategoryId());
            title.setText(question.getTitle());
            answerCount.setText(itemView.getContext()
                    .getString(R.string.answer_count, question.getAnswerCount()));
            solved.setVisibility(question.isSolved() ? View.VISIBLE : View.GONE);
            audioIcon.setVisibility(question.getAudioUrl() != null
                    ? View.VISIBLE : View.GONE);

            List<String> images = question.getImageUrls();
            Bitmap thumbBitmap = ThumbnailLoader.load(question.getThumbBase64());
            String cover = images != null && !images.isEmpty() ? images.get(0) : null;
            boolean hasUrlCover = cover != null && !cover.trim().isEmpty();
            boolean hasCover = thumbBitmap != null || hasUrlCover;

            if (thumbBitmap != null) {
                imageWrap.setVisibility(View.VISIBLE);
                Glide.with(thumbnail).clear(thumbnail);
                thumbnail.setImageBitmap(thumbBitmap);
            } else if (hasUrlCover) {
                imageWrap.setVisibility(View.VISIBLE);
                Glide.with(thumbnail).load(cover).centerCrop().into(thumbnail);
            } else {
                imageWrap.setVisibility(View.GONE);
                Glide.with(thumbnail).clear(thumbnail);
                thumbnail.setImageBitmap(null);
            }

            long totalImages = question.getImageCount() > 0
                    ? question.getImageCount()
                    : (images != null ? images.size() : 0);
            if (hasCover && totalImages > 1) {
                imageCount.setText(itemView.getContext().getString(
                        R.string.more_images, (int) (totalImages - 1)));
                imageCount.setVisibility(View.VISIBLE);
            } else {
                imageCount.setVisibility(View.GONE);
            }
            imageIcon.setVisibility(!hasCover && totalImages > 0
                    ? View.VISIBLE : View.GONE);
            audioIcon.setVisibility(question.isHasAudio() || question.getAudioUrl() != null
                    ? View.VISIBLE : View.GONE);

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onQuestionClick(question);
                }
            });
        }
    }
}
