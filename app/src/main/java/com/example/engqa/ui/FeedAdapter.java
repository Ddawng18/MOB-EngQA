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
import com.example.engqa.model.FeedItem;
import com.example.engqa.model.Promo;
import com.example.engqa.model.Question;
import com.example.engqa.model.WordEntry;
import com.example.engqa.util.UiUtils;

import java.util.List;

/**
 * One RecyclerView, two card types ({@link FeedItem#TYPE_QUESTION} / {@link FeedItem#TYPE_PROMO}).
 *
 * <p>{@code getItemViewType} returns the item type and {@code onCreateViewHolder} picks the matching
 * layout, which is the two-ViewHolder pattern from the course. DiffUtil keeps realtime updates cheap.
 */
public class FeedAdapter extends ListAdapter<FeedItem, RecyclerView.ViewHolder> {

    public interface OnFeedActionListener {
        void onQuestionClick(Question question);

        void onPromoClick(Promo promo);

        void onWordClick(WordEntry word);
    }

    private static final int VIEW_QUESTION = 1;
    private static final int VIEW_PROMO = 2;
    private static final int VIEW_WORD = 3;

    private static final DiffUtil.ItemCallback<FeedItem> DIFF =
            new DiffUtil.ItemCallback<FeedItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull FeedItem oldItem,
                                               @NonNull FeedItem newItem) {
                    return oldItem.key().equals(newItem.key());
                }

                @Override
                public boolean areContentsTheSame(@NonNull FeedItem oldItem,
                                                  @NonNull FeedItem newItem) {
                    if (oldItem.getType() != newItem.getType()) {
                        return false;
                    }
                    if (oldItem.isWord()) {
                        WordEntry a = oldItem.getWord();
                        WordEntry b = newItem.getWord();
                        if (a == null || b == null) {
                            return a == null && b == null;
                        }
                        return eq(a.getWord(), b.getWord())
                                && eq(a.primaryDefinition(), b.primaryDefinition());
                    }
                    if (oldItem.isPromo()) {
                        Promo a = oldItem.getPromo();
                        Promo b = newItem.getPromo();
                        if (a == null || b == null) {
                            return a == null && b == null;
                        }
                        return a.getLeadCount() == b.getLeadCount()
                                && a.getViewCount() == b.getViewCount()
                                && a.isCenterVerified() == b.isCenterVerified()
                                && eq(a.getTitle(), b.getTitle());
                    }
                    Question a = oldItem.getQuestion();
                    Question b = newItem.getQuestion();
                    if (a == null || b == null) {
                        return a == null && b == null;
                    }
                    return a.getAnswerCount() == b.getAnswerCount()
                            && a.getScore() == b.getScore()
                            && a.isSolved() == b.isSolved()
                            && eq(a.getTitle(), b.getTitle());
                }

                private boolean eq(String a, String b) {
                    return a == null ? b == null : a.equals(b);
                }
            };

    private final OnFeedActionListener listener;

    public FeedAdapter(OnFeedActionListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        FeedItem item = getItem(position);
        if (item.isWord()) {
            return VIEW_WORD;
        }
        return item.isPromo() ? VIEW_PROMO : VIEW_QUESTION;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_WORD) {
            return new WordViewHolder(
                    inflater.inflate(R.layout.item_word_of_day, parent, false));
        }
        if (viewType == VIEW_PROMO) {
            return new PromoViewHolder(
                    inflater.inflate(R.layout.item_promo, parent, false));
        }
        return new QuestionViewHolder(
                inflater.inflate(R.layout.item_question, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        FeedItem item = getItem(position);
        if (holder instanceof WordViewHolder) {
            ((WordViewHolder) holder).bind(item.getWord(), listener);
        } else if (holder instanceof PromoViewHolder) {
            ((PromoViewHolder) holder).bind(item.getPromo(), listener);
        } else {
            ((QuestionViewHolder) holder).bind(item.getQuestion(), listener);
        }
    }

    // ------------------------------------------------------------------ holders

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

        void bind(Question question, OnFeedActionListener listener) {
            if (question == null) {
                return;
            }
            String name = question.getAuthorName() != null ? question.getAuthorName() : "user";
            avatar.setText(name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase());
            user.setText(name);
            time.setText(UiUtils.relativeTime(question.getCreatedAt()));
            tag.setText(question.getCategoryName() != null
                    ? question.getCategoryName() : question.getCategoryId());
            title.setText(question.getTitle());
            answerCount.setText(itemView.getContext()
                    .getString(R.string.answer_count, question.getAnswerCount()));
            solved.setVisibility(question.isSolved() ? View.VISIBLE : View.GONE);
            audioIcon.setVisibility(question.isHasAudio() || question.getAudioUrl() != null
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

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onQuestionClick(question);
                }
            });
        }
    }

    static class PromoViewHolder extends RecyclerView.ViewHolder {

        private final TextView avatar;
        private final TextView center;
        private final ImageView verified;
        private final TextView time;
        private final ImageView banner;
        private final TextView type;
        private final TextView title;
        private final TextView body;
        private final TextView meta;
        private final TextView deal;

        PromoViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.tvPromoAvatar);
            center = itemView.findViewById(R.id.tvPromoCenter);
            verified = itemView.findViewById(R.id.ivPromoVerified);
            time = itemView.findViewById(R.id.tvPromoTime);
            banner = itemView.findViewById(R.id.ivPromoBanner);
            type = itemView.findViewById(R.id.tvPromoType);
            title = itemView.findViewById(R.id.tvPromoTitle);
            body = itemView.findViewById(R.id.tvPromoBody);
            meta = itemView.findViewById(R.id.tvPromoMeta);
            deal = itemView.findViewById(R.id.tvPromoDeal);
        }

        void bind(Promo promo, OnFeedActionListener listener) {
            if (promo == null) {
                return;
            }
            String name = promo.getCenterName() != null ? promo.getCenterName() : "Center";
            avatar.setText(name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase());
            center.setText(name);
            verified.setVisibility(promo.isCenterVerified() ? View.VISIBLE : View.GONE);
            time.setText(UiUtils.relativeTime(promo.getCreatedAt()));
            type.setText(typeLabel(promo.getType()));
            title.setText(promo.getTitle());
            body.setText(promo.getBody());
            meta.setText(metaLine(promo));
            deal.setVisibility(promo.isExpired(System.currentTimeMillis())
                    ? View.GONE : View.VISIBLE);

            if (promo.getImageUrl() != null && !promo.getImageUrl().isEmpty()) {
                banner.setVisibility(View.VISIBLE);
                Glide.with(banner).load(promo.getImageUrl()).centerCrop().into(banner);
            } else {
                banner.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPromoClick(promo);
                }
            });
        }

        private static String metaLine(Promo promo) {
            StringBuilder builder = new StringBuilder();
            append(builder, promo.getLevel());
            append(builder, promo.getFee());
            if (promo.isExpired(System.currentTimeMillis())) {
                append(builder, "Đã hết hạn");
            }
            return builder.toString();
        }

        private static void append(StringBuilder builder, String value) {
            if (value == null || value.trim().isEmpty()) {
                return;
            }
            if (builder.length() > 0) {
                builder.append(" · ");
            }
            builder.append(value.trim());
        }

        private String typeLabel(String promoType) {
            int resId = R.string.promo_type_course;
            if (Promo.TYPE_EVENT.equals(promoType)) {
                resId = R.string.promo_type_event;
            } else if (Promo.TYPE_SCHOLARSHIP.equals(promoType)) {
                resId = R.string.promo_type_scholarship;
            } else if (Promo.TYPE_ANNOUNCEMENT.equals(promoType)) {
                resId = R.string.promo_type_announcement;
            }
            return itemView.getContext().getString(resId);
        }
    }

    static class WordViewHolder extends RecyclerView.ViewHolder {

        private final TextView word;
        private final TextView phonetic;
        private final TextView partOfSpeech;
        private final TextView definition;

        WordViewHolder(@NonNull View itemView) {
            super(itemView);
            word = itemView.findViewById(R.id.tvWordOfDay);
            phonetic = itemView.findViewById(R.id.tvWordPhonetic);
            partOfSpeech = itemView.findViewById(R.id.tvWordPartOfSpeech);
            definition = itemView.findViewById(R.id.tvWordDefinition);
        }

        void bind(WordEntry entry, OnFeedActionListener listener) {
            if (entry == null) {
                return;
            }
            word.setText(entry.getWord());
            String phoneticText = entry.getPhonetic();
            phonetic.setText(phoneticText != null ? phoneticText : "");
            phonetic.setVisibility(phoneticText != null && !phoneticText.isEmpty()
                    ? View.VISIBLE : View.GONE);
            String pos = entry.primaryPartOfSpeech();
            partOfSpeech.setText(pos);
            partOfSpeech.setVisibility(pos.isEmpty() ? View.GONE : View.VISIBLE);
            definition.setText(entry.primaryDefinition());
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onWordClick(entry);
                }
            });
        }
    }
}
