package com.example.engqa.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.engqa.R;
import com.example.engqa.model.Answer;
import com.example.engqa.model.Vote;
import com.example.engqa.util.TimeUtils;

import java.util.HashMap;
import java.util.Map;

/** Renders the answer list with upvote / downvote controls and the accepted badge. */
public class AnswerAdapter extends ListAdapter<Answer, AnswerAdapter.AnswerViewHolder> {

    public interface OnAnswerActionListener {
        void onVote(Answer answer, int requestedValue);

        void onAccept(Answer answer);
    }

    private static final DiffUtil.ItemCallback<Answer> DIFF = new DiffUtil.ItemCallback<Answer>() {
        @Override
        public boolean areItemsTheSame(@NonNull Answer oldItem, @NonNull Answer newItem) {
            return oldItem.getId() != null && oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Answer oldItem, @NonNull Answer newItem) {
            return oldItem.getUpCount() == newItem.getUpCount()
                    && oldItem.getDownCount() == newItem.getDownCount()
                    && oldItem.isAccepted() == newItem.isAccepted()
                    && eq(oldItem.getBody(), newItem.getBody());
        }

        private boolean eq(String a, String b) {
            return a == null ? b == null : a.equals(b);
        }
    };

    private final OnAnswerActionListener listener;
    private Map<String, Integer> myVotes = new HashMap<>();
    private String questionOwnerId;
    private String currentUid;
    private boolean votingEnabled = true;
    private boolean isQuestionSolved = false;

    public AnswerAdapter(OnAnswerActionListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    public void setMyVotes(Map<String, Integer> myVotes) {
        this.myVotes = myVotes != null ? myVotes : new HashMap<>();
    }

    public void setContext(String questionOwnerId, String currentUid) {
        boolean changed = !eq(this.questionOwnerId, questionOwnerId) || !eq(this.currentUid, currentUid);
        this.questionOwnerId = questionOwnerId;
        this.currentUid = currentUid;
        if (changed) {
            notifyDataSetChanged();
        }
    }

    public void setVotingEnabled(boolean votingEnabled) {
        if (this.votingEnabled != votingEnabled) {
            this.votingEnabled = votingEnabled;
            notifyDataSetChanged();
        }
    }

    public void setQuestionSolved(boolean isQuestionSolved) {
        if (this.isQuestionSolved != isQuestionSolved) {
            this.isQuestionSolved = isQuestionSolved;
            notifyDataSetChanged();
        }
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    @NonNull
    @Override
    public AnswerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_answer, parent, false);
        return new AnswerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AnswerViewHolder holder, int position) {
        holder.bind(getItem(position), myVotes, questionOwnerId, currentUid, votingEnabled, isQuestionSolved, listener);
    }

    static class AnswerViewHolder extends RecyclerView.ViewHolder {

        private final TextView avatar;
        private final TextView username;
        private final TextView time;
        private final TextView body;
        private final TextView upvote;
        private final TextView downvote;
        private final TextView acceptedBadge;
        private final TextView acceptButton;

        AnswerViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.tvAvatar);
            username = itemView.findViewById(R.id.tvUsername);
            time = itemView.findViewById(R.id.tvTime);
            body = itemView.findViewById(R.id.tvAnswerText);
            upvote = itemView.findViewById(R.id.tvUpvote);
            downvote = itemView.findViewById(R.id.tvDownvote);
            acceptedBadge = itemView.findViewById(R.id.tvAcceptedBadge);
            acceptButton = itemView.findViewById(R.id.btnAccept);
        }

        void bind(Answer answer, Map<String, Integer> myVotes, String questionOwnerId,
                  String currentUid, boolean votingEnabled, boolean isQuestionSolved, OnAnswerActionListener listener) {
            String name = answer.getAuthorName() != null ? answer.getAuthorName() : "user";
            avatar.setText(name.isEmpty() ? "?" : String.valueOf(name.charAt(0)).toUpperCase());
            username.setText(name);
            time.setText(TimeUtils.relative(answer.getCreatedAt()));
            body.setText(answer.getBody());

            int myVote = myVotes.getOrDefault(answer.getId(), 0);
            upvote.setText("\u25B2 " + answer.getUpCount());
            downvote.setText("\u25BC " + answer.getDownCount());
            int upColor = myVote == Vote.UP ? R.color.vote_up_active : R.color.text_secondary;
            int downColor = myVote == Vote.DOWN ? R.color.vote_down_active : R.color.text_secondary;
            upvote.setTextColor(ContextCompat.getColor(itemView.getContext(), upColor));
            downvote.setTextColor(ContextCompat.getColor(itemView.getContext(), downColor));

            acceptedBadge.setVisibility(answer.isAccepted() ? View.VISIBLE : View.GONE);

            boolean isOwner = questionOwnerId != null && questionOwnerId.equals(currentUid);
            boolean isSelfAnswer = currentUid != null && currentUid.equals(answer.getAuthorId());
            boolean canAccept = isOwner && !isQuestionSolved && !answer.isAccepted() && !isSelfAnswer;
            acceptButton.setVisibility(canAccept ? View.VISIBLE : View.GONE);

            if (votingEnabled) {
                upvote.setOnClickListener(v -> {
                    int requested = myVote == Vote.UP ? 0 : Vote.UP;
                    if (listener != null) {
                        listener.onVote(answer, requested);
                    }
                });
                downvote.setOnClickListener(v -> {
                    int requested = myVote == Vote.DOWN ? 0 : Vote.DOWN;
                    if (listener != null) {
                        listener.onVote(answer, requested);
                    }
                });
            } else if (currentUid == null) {
                View.OnClickListener guestListener = v -> {
                    android.content.Context ctx = v.getContext();
                    android.widget.Toast.makeText(ctx, "Bạn cần đăng nhập", android.widget.Toast.LENGTH_SHORT).show();
                    ctx.startActivity(new android.content.Intent(ctx, com.example.engqa.LoginActivity.class));
                };
                upvote.setOnClickListener(guestListener);
                downvote.setOnClickListener(guestListener);
            } else {
                upvote.setOnClickListener(null);
                downvote.setOnClickListener(null);
            }
            acceptButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAccept(answer);
                }
            });
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onVote(answer, 0);
                }
            });
        }
    }
}
