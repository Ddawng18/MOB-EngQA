package com.example.engqa.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.engqa.data.Callback;
import com.example.engqa.data.CategoryRepository;
import com.example.engqa.data.QuestionRepository;
import com.example.engqa.data.RepoErrors;
import com.example.engqa.model.Category;
import com.example.engqa.model.Question;
import com.example.engqa.model.UiState;
import com.example.engqa.util.TextUtils2;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the feed filter (category, sort, keyword), pages already loaded and the Firestore
 * listener. Because the ViewModel survives a configuration change, rotation never creates a second
 * listener; the screen only calls {@link #start()} / {@link #stop()} from its lifecycle.
 */
public class HomeViewModel extends ViewModel {

    public static final int PAGE_SIZE = 10;

    private final QuestionRepository questionRepository = new QuestionRepository();
    private final CategoryRepository categoryRepository = new CategoryRepository();

    private final MutableLiveData<UiState<List<Question>>> feed = new MutableLiveData<>();
    private final MutableLiveData<List<Category>> categories = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingMore = new MutableLiveData<>(false);

    private ListenerRegistration feedRegistration;
    private ListenerRegistration categoriesRegistration;

    private String categoryId = QuestionRepository.ALL_CATEGORIES;
    private String sort = QuestionRepository.SORT_NEWEST;
    private String keyword = "";
    private DocumentSnapshot lastDocument;
    private boolean fromCache;

    public LiveData<UiState<List<Question>>> getFeed() {
        return feed;
    }

    public LiveData<List<Category>> getCategories() {
        return categories;
    }

    public LiveData<Boolean> getLoadingMore() {
        return loadingMore;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public String getSort() {
        return sort;
    }

    public boolean isFromCache() {
        return fromCache;
    }

    public void start() {
        if (feedRegistration != null) {
            return;
        }
        attachFeed();
        if (categoriesRegistration == null) {
            categoriesRegistration = categoryRepository.observeCategories(new Callback<List<Category>>() {
                @Override
                public void onSuccess(List<Category> result) {
                    categories.setValue(result);
                }

                @Override
                public void onError(Exception e) {
                    // Chips are non-critical; keep whatever was loaded before.
                }
            });
        }
    }

    public void stop() {
        if (feedRegistration != null) {
            feedRegistration.remove();
            feedRegistration = null;
        }
        if (categoriesRegistration != null) {
            categoriesRegistration.remove();
            categoriesRegistration = null;
        }
    }

    @Override
    protected void onCleared() {
        stop();
        super.onCleared();
    }

    public void setCategory(String categoryId) {
        this.categoryId = categoryId;
        restart();
    }

    public void setSort(String sort) {
        this.sort = sort;
        restart();
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword == null ? "" : keyword.trim();
        restart();
    }

    public void loadMore() {
        if (Boolean.TRUE.equals(loadingMore.getValue()) || lastDocument == null) {
            return;
        }
        loadingMore.setValue(true);
        questionRepository.loadMore(categoryId, sort, PAGE_SIZE, lastDocument,
                new Callback<QuestionRepository.Page>() {
                    @Override
                    public void onSuccess(QuestionRepository.Page page) {
                        loadingMore.setValue(false);
                        if (page.questions.isEmpty()) {
                            lastDocument = null;
                            return;
                        }
                        List<Question> current = new ArrayList<>();
                        UiState<List<Question>> state = feed.getValue();
                        if (state != null && state.getData() != null) {
                            current.addAll(state.getData());
                        }
                        current.addAll(page.questions);
                        lastDocument = page.lastDocument;
                        feed.setValue(UiState.success(current, fromCache));
                    }

                    @Override
                    public void onError(Exception e) {
                        loadingMore.setValue(false);
                    }
                });
    }

    private void restart() {
        if (feedRegistration != null) {
            feedRegistration.remove();
            feedRegistration = null;
        }
        lastDocument = null;
        attachFeed();
    }

    private void attachFeed() {
        feed.setValue(UiState.<List<Question>>loading());
        String token = TextUtils2.searchToken(keyword);
        if (!token.isEmpty()) {
            feedRegistration = questionRepository.search(token, PAGE_SIZE, pageCallback());
        } else {
            feedRegistration = questionRepository.observeLatest(categoryId, sort, PAGE_SIZE, pageCallback());
        }
    }

    private Callback<QuestionRepository.Page> pageCallback() {
        return new Callback<QuestionRepository.Page>() {
            @Override
            public void onSuccess(QuestionRepository.Page page) {
                lastDocument = page.lastDocument;
                fromCache = page.fromCache;
                if (page.questions.isEmpty()) {
                    feed.setValue(UiState.<List<Question>>empty());
                } else {
                    feed.setValue(UiState.success(page.questions, page.fromCache));
                }
            }

            @Override
            public void onError(Exception e) {
                feed.setValue(UiState.<List<Question>>error(RepoErrors.message(e)));
            }
        };
    }
}
