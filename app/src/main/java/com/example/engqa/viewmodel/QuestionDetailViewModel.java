package com.example.engqa.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.engqa.data.AnswerRepository;
import com.example.engqa.data.Callback;
import com.example.engqa.data.NotificationRepository;
import com.example.engqa.data.QuestionRepository;
import com.example.engqa.data.RepoErrors;
import com.example.engqa.data.UserRepository;
import com.example.engqa.data.VoteRepository;
import com.example.engqa.model.Answer;
import com.example.engqa.model.AppNotification;
import com.example.engqa.model.Question;
import com.example.engqa.model.UiState;
import com.example.engqa.model.User;
import com.example.engqa.model.Vote;
import com.example.engqa.util.AnswerRanking;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Backs the question-detail screen: the question, its answers, and the current user's votes. */
public class QuestionDetailViewModel extends ViewModel {

    private final QuestionRepository questionRepository = new QuestionRepository();
    private final AnswerRepository answerRepository = new AnswerRepository();
    private final VoteRepository voteRepository = new VoteRepository();
    private final NotificationRepository notificationRepository = new NotificationRepository();
    private final UserRepository userRepository = new UserRepository();

    private final MutableLiveData<UiState<Question>> question = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<Answer>>> answers = new MutableLiveData<>();
    private final MutableLiveData<Map<String, Integer>> myVotes = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<UiState<String>> actionState = new MutableLiveData<>();

    private ListenerRegistration questionRegistration;
    private ListenerRegistration answersRegistration;

    private String questionId;
    private String uid;
    private boolean submittingAnswer;

    public LiveData<UiState<Question>> getQuestion() {
        return question;
    }

    public LiveData<UiState<List<Answer>>> getAnswers() {
        return answers;
    }

    public LiveData<Map<String, Integer>> getMyVotes() {
        return myVotes;
    }

    public LiveData<UiState<String>> getActionState() {
        return actionState;
    }

    public String getQuestionId() {
        return questionId;
    }

    public void start(String questionId, String uid) {
        if (this.questionId != null && this.questionId.equals(questionId)
                && questionRegistration != null) {
            return;
        }
        this.questionId = questionId;
        this.uid = uid;
        attach();
    }

    public void stop() {
        if (questionRegistration != null) {
            questionRegistration.remove();
            questionRegistration = null;
        }
        if (answersRegistration != null) {
            answersRegistration.remove();
            answersRegistration = null;
        }
    }

    @Override
    protected void onCleared() {
        stop();
        super.onCleared();
    }

    public void addAnswer(String body) {
        if (submittingAnswer) {
            return;
        }
        if (uid == null) {
            actionState.setValue(UiState.error("Bạn cần đăng nhập"));
            return;
        }
        if (body == null || body.trim().isEmpty()) {
            actionState.setValue(UiState.error("Vui lòng nhập câu trả lời"));
            return;
        }
        submittingAnswer = true;
        actionState.setValue(UiState.<String>loading());
        userRepository.getUser(uid, new Callback<User>() {
            @Override
            public void onSuccess(User user) {
                String name = user != null && user.getUsername() != null
                        ? user.getUsername() : "user";
                createAnswer(body, name);
            }

            @Override
            public void onError(Exception e) {
                createAnswer(body, "user");
            }
        });
    }

    private void createAnswer(String body, String authorName) {
        Answer answer = new Answer();
        answer.setQuestionId(questionId);
        answer.setAuthorId(uid);
        answer.setAuthorName(authorName);
        answer.setBody(body.trim());
        Question current = question.getValue() != null ? question.getValue().getData() : null;
        if (current != null) {
            answer.setQuestionTitle(current.getTitle());
        }
        answerRepository.addAnswer(answer, new Callback<String>() {
            @Override
            public void onSuccess(String answerId) {
                submittingAnswer = false;
                actionState.setValue(UiState.success("answer_posted"));
                notifyQuestionAuthor(AppNotification.TYPE_ANSWER, authorName,
                        "Có câu trả lời mới", authorName + " đã trả lời câu hỏi của bạn");
            }

            @Override
            public void onError(Exception e) {
                submittingAnswer = false;
                actionState.setValue(UiState.error(RepoErrors.message(e)));
            }
        });
    }

    /** Applies a new vote for the current user; the answer counters arrive again via the listener. */
    public void vote(String answerId, int value) {
        if (uid == null) {
            actionState.setValue(UiState.error("Bạn cần đăng nhập"));
            return;
        }
        voteRepository.vote(questionId, answerId, uid, value, new Callback<VoteRepository.VoteResult>() {
            @Override
            public void onSuccess(VoteRepository.VoteResult result) {
                Map<String, Integer> current = myVotes.getValue();
                if (current == null) {
                    current = new HashMap<>();
                }
                Map<String, Integer> updated = new HashMap<>(current);
                if (result.value == 0) {
                    updated.remove(answerId);
                } else {
                    updated.put(answerId, result.value);
                }
                myVotes.setValue(updated);

                if (result.value == Vote.UP) {
                    notifyAnswerAuthor(answerId, "Có lượt bình chọn",
                            "Câu trả lời của bạn vừa được upvote");
                }
            }

            @Override
            public void onError(Exception e) {
                actionState.setValue(UiState.error(RepoErrors.message(e)));
            }
        });
    }

    public void accept(String answerId) {
        answerRepository.accept(questionId, answerId, authorIdOf(answerId), new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                actionState.setValue(UiState.success(answerId));
                notifyAnswerAuthor(answerId, "Câu trả lời được chấp nhận",
                        "Câu trả lời của bạn đã được chấp nhận");
            }

            @Override
            public void onError(Exception e) {
                actionState.setValue(UiState.error(RepoErrors.message(e)));
            }
        });
    }

    public void deleteAnswer(String answerId) {
        answerRepository.delete(questionId, answerId, authorIdOf(answerId), new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                actionState.setValue(UiState.success("deleted"));
            }

            @Override
            public void onError(Exception e) {
                actionState.setValue(UiState.error(RepoErrors.message(e)));
            }
        });
    }

    public void deleteQuestion() {
        Question current = question.getValue() != null ? question.getValue().getData() : null;
        String authorId = current != null ? current.getAuthorId() : null;
        questionRepository.deleteQuestion(questionId, authorId, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                actionState.setValue(UiState.success("question_deleted"));
            }

            @Override
            public void onError(Exception e) {
                actionState.setValue(UiState.error(RepoErrors.message(e)));
            }
        });
    }

    /** Looks up the author of an answer currently loaded in the list. */
    private String authorIdOf(String answerId) {
        UiState<List<Answer>> state = answers.getValue();
        if (state != null && state.getData() != null) {
            for (Answer answer : state.getData()) {
                if (answerId.equals(answer.getId())) {
                    return answer.getAuthorId();
                }
            }
        }
        return null;
    }

    private void attach() {
        question.setValue(UiState.<Question>loading());
        questionRegistration = questionRepository.observeQuestion(questionId, new Callback<Question>() {
            @Override
            public void onSuccess(Question result) {
                if (result == null) {
                    question.setValue(UiState.<Question>error("Không tìm thấy câu hỏi"));
                } else {
                    question.setValue(UiState.success(result));
                }
            }

            @Override
            public void onError(Exception e) {
                question.setValue(UiState.<Question>error(RepoErrors.message(e)));
            }
        });
        answersRegistration = answerRepository.observeAnswers(questionId,
                new Callback<List<Answer>>() {
                    @Override
                    public void onSuccess(List<Answer> result) {
                        if (result.isEmpty()) {
                            answers.setValue(UiState.<List<Answer>>empty());
                        } else {
                            answers.setValue(UiState.success(AnswerRanking.sort(result)));
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        answers.setValue(UiState.<List<Answer>>error(RepoErrors.message(e)));
                    }
                });
    }

    private void notifyQuestionAuthor(String type, String actorName, String title, String body) {
        Question current = question.getValue() != null ? question.getValue().getData() : null;
        if (current == null) {
            return;
        }
        AppNotification notification = new AppNotification();
        notification.setRecipientId(current.getAuthorId());
        notification.setActorId(uid);
        notification.setActorName(actorName);
        notification.setType(type);
        notification.setQuestionId(questionId);
        notification.setTitle(title);
        notification.setBody(body);
        notificationRepository.create(notification, null);
    }

    private void notifyAnswerAuthor(String answerId, String title, String body) {
        UiState<List<Answer>> state = answers.getValue();
        if (state == null || state.getData() == null) {
            return;
        }
        for (Answer answer : state.getData()) {
            if (answerId.equals(answer.getId())) {
                AppNotification notification = new AppNotification();
                notification.setRecipientId(answer.getAuthorId());
                notification.setActorId(uid);
                notification.setActorName(null);
                notification.setType(AppNotification.TYPE_ACCEPTED);
                notification.setQuestionId(questionId);
                notification.setAnswerId(answerId);
                notification.setTitle(title);
                notification.setBody(body);
                notificationRepository.create(notification, null);
                return;
            }
        }
    }
}
