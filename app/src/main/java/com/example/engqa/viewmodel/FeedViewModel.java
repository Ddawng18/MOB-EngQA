package com.example.engqa.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.engqa.data.Callback;
import com.example.engqa.data.CategoryRepository;
import com.example.engqa.data.PromoRepository;
import com.example.engqa.data.QuestionRepository;
import com.example.engqa.data.RepoErrors;
import com.example.engqa.data.UserRepository;
import com.example.engqa.data.WordRepository;
import com.example.engqa.model.Category;
import com.example.engqa.model.FeedItem;
import com.example.engqa.model.Promo;
import com.example.engqa.model.Question;
import com.example.engqa.model.UiState;
import com.example.engqa.model.User;
import com.example.engqa.model.WordEntry;
import com.example.engqa.util.FeedMixer;
import com.example.engqa.util.PrefsHelper;
import com.example.engqa.util.SearchRanker;
import com.example.engqa.util.TextUtils2;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Home feed: realtime questions from {@link QuestionRepository} plus approved promos from
 * {@link PromoRepository}, merged by {@link FeedMixer} into a single {@code LiveData}.
 *
 * <p>The two streams stay independent: changing the category restarts only the question query,
 * while promos keep streaming. The selected chip is persisted with {@link PrefsHelper}. Listeners
 * are attached in {@link #start()} and detached in {@link #stop()} / {@link #onCleared()}, and the
 * ViewModel survives rotation.
 */
public class FeedViewModel extends AndroidViewModel {

    public static final int PAGE_SIZE = 10;
    private static final int CADENCE = FeedMixer.DEFAULT_CADENCE;
    private static final long SEARCH_DEBOUNCE_MS = 300L;

    private final QuestionRepository questionRepository = new QuestionRepository();
    private final CategoryRepository categoryRepository = new CategoryRepository();
    private final PromoRepository promoRepository = new PromoRepository();
    private final UserRepository userRepository = new UserRepository();
    private final WordRepository wordRepository = new WordRepository();

    private final MutableLiveData<UiState<List<Question>>> questionState = new MutableLiveData<>();
    private final MutableLiveData<List<Promo>> promoState = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Category>> categories = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingMore = new MutableLiveData<>(false);
    private final MediatorLiveData<UiState<List<FeedItem>>> feed = new MediatorLiveData<>();
    private final MutableLiveData<User> currentUser = new MutableLiveData<>();
    private final MutableLiveData<WordEntry> wordOfDay = new MutableLiveData<>();

    private ListenerRegistration questionRegistration;
    private ListenerRegistration categoriesRegistration;
    private ListenerRegistration promoRegistration;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Runnable searchRunnable = this::attachQuestions;
    private final List<Question> searchResults = new ArrayList<>();
    private int searchShown;
    private int queryGeneration;

    /** {@code null} means the "Tất cả" chip; {@link FeedMixer#FILTER_CENTER} means promos only. */
    private String categoryId;
    private String sort = QuestionRepository.SORT_NEWEST;
    private String keyword = "";
    private DocumentSnapshot lastDocument;
    private boolean fromCache;
    private boolean promosLoaded;
    private boolean wordRequested;

    public FeedViewModel(@NonNull Application application) {
        super(application);
        categoryId = PrefsHelper.getFilter(application);
        feed.addSource(questionState, state -> rebuild());
        feed.addSource(promoState, promos -> {
            promosLoaded = true;
            rebuild();
        });
        feed.addSource(wordOfDay, word -> rebuild());
    }

    // ------------------------------------------------------------------ state

    public LiveData<UiState<List<FeedItem>>> getFeed() {
        return feed;
    }

    public LiveData<List<Category>> getCategories() {
        return categories;
    }

    public LiveData<Boolean> getLoadingMore() {
        return loadingMore;
    }

    /** Signed-in profile (role, centre) used by the home profile menu. */
    public LiveData<User> getCurrentUser() {
        return currentUser;
    }

    public LiveData<WordEntry> getWordOfDay() {
        return wordOfDay;
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

    /** True while the query has at least one searchable token and question search is active. */
    public boolean isSearchActive() {
        return !keyword.isEmpty() && TextUtils2.isSearchable(keyword)
                && !FeedMixer.FILTER_CENTER.equals(categoryId);
    }

    /** True for a non-empty query that cannot produce tokens (for example "!!"). */
    public boolean isUnsearchableQuery() {
        return !keyword.isEmpty() && !TextUtils2.isSearchable(keyword);
    }

    /** True when the user has typed anything, so the empty state can explain "no matches". */
    public boolean hasSearchQuery() {
        return !keyword.isEmpty();
    }

    // ------------------------------------------------------------------ lifecycle

    public void start() {
        if (promoRegistration == null) {
            promoRegistration = promoRepository.observeApproved(new Callback<List<Promo>>() {
                @Override
                public void onSuccess(List<Promo> result) {
                    promoState.setValue(result);
                }

                @Override
                public void onError(Exception e) {
                    // Keep whatever promos we already had; the feed still works without them.
                    promosLoaded = true;
                    rebuild();
                }
            });
        }
        if (categoriesRegistration == null) {
            categoriesRegistration = categoryRepository.observeCategories(
                    new Callback<List<Category>>() {
                        @Override
                        public void onSuccess(List<Category> result) {
                            categories.setValue(result);
                        }

                        @Override
                        public void onError(Exception e) {
                            // Chips are non-critical.
                        }
                    });
        }
        if (questionRegistration == null && !FeedMixer.FILTER_CENTER.equals(categoryId)) {
            attachQuestions();
        } else {
            rebuild();
        }
        refreshCurrentUser();
        loadWordOfDay();
    }

    /** Loads the daily word once per ViewModel; the repository serves the cache when fresh. */
    private void loadWordOfDay() {
        if (wordRequested) {
            return;
        }
        wordRequested = true;
        wordRepository.loadToday(getApplication(), false, new Callback<WordEntry>() {
            @Override
            public void onSuccess(WordEntry result) {
                wordOfDay.setValue(result);
            }

            @Override
            public void onError(Exception e) {
                wordOfDay.setValue(null);
            }
        });
    }

    /** Loads the signed-in profile (one read) so the profile menu knows the role. */
    public void refreshCurrentUser() {
        String uid = userRepository.currentUid();
        if (uid == null) {
            currentUser.setValue(null);
            return;
        }
        userRepository.getUser(uid, new Callback<User>() {
            @Override
            public void onSuccess(User user) {
                currentUser.setValue(user);
            }

            @Override
            public void onError(Exception e) {
                // Keep the previous value; the menu falls back to the visitor state.
            }
        });
    }

    public void stop() {
        searchHandler.removeCallbacks(searchRunnable);
        remove(questionRegistration);
        questionRegistration = null;
        remove(categoriesRegistration);
        categoriesRegistration = null;
        remove(promoRegistration);
        promoRegistration = null;
    }

    @Override
    protected void onCleared() {
        stop();
        searchHandler.removeCallbacksAndMessages(null);
        wordRepository.shutdown();
        super.onCleared();
    }

    // ------------------------------------------------------------------ filters

    public void setCategory(String newCategoryId) {
        String next = (newCategoryId == null || newCategoryId.isEmpty()) ? null : newCategoryId;
        if (Objects.equals(next, categoryId)) {
            return;
        }
        categoryId = next;
        PrefsHelper.setFilter(getApplication(), categoryId);
        searchHandler.removeCallbacks(searchRunnable);
        if (FeedMixer.FILTER_CENTER.equals(categoryId)) {
            // Questions are irrelevant for the "Trung tâm" chip.
            invalidateGeneration();
            remove(questionRegistration);
            questionRegistration = null;
            searchResults.clear();
            searchShown = 0;
            rebuild();
        } else {
            restartQuestions();
        }
    }

    public void setSort(String newSort) {
        if (Objects.equals(newSort, sort)) {
            return;
        }
        sort = newSort;
        if (!FeedMixer.FILTER_CENTER.equals(categoryId)) {
            restartQuestions();
        }
    }

    public void setKeyword(String newKeyword) {
        String next = newKeyword == null ? "" : newKeyword.trim();
        if (Objects.equals(next, keyword)) {
            return;
        }
        String previousPhrase = TextUtils2.phrase(keyword);
        String nextPhrase = TextUtils2.phrase(next);
        keyword = next;
        rebuild();

        if (FeedMixer.FILTER_CENTER.equals(categoryId)) {
            searchHandler.removeCallbacks(searchRunnable);
            searchResults.clear();
            searchShown = 0;
            return;
        }

        if (next.isEmpty()) {
            // Clearing the field restores the normal, unfiltered feed immediately.
            searchHandler.removeCallbacks(searchRunnable);
            restartQuestions();
            return;
        }

        if (!TextUtils2.isSearchable(next)) {
            // Do not show unrelated results under a non-empty but unsearchable query.
            searchHandler.removeCallbacks(searchRunnable);
            invalidateGeneration();
            remove(questionRegistration);
            questionRegistration = null;
            searchResults.clear();
            searchShown = 0;
            lastDocument = null;
            questionState.setValue(UiState.<List<Question>>empty());
            return;
        }

        if (nextPhrase.equals(previousPhrase)) {
            // Case/whitespace-only changes keep the same tokens and therefore the same results.
            return;
        }

        searchHandler.removeCallbacks(searchRunnable);
        invalidateGeneration();
        remove(questionRegistration);
        questionRegistration = null;
        searchResults.clear();
        searchShown = 0;
        lastDocument = null;
        loadingMore.setValue(false);
        questionState.setValue(UiState.<List<Question>>loading());
        searchHandler.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS);
    }

    // ------------------------------------------------------------------ paging

    public void loadMore() {
        if (FeedMixer.FILTER_CENTER.equals(categoryId) || isUnsearchableQuery()) {
            return;
        }
        if (isSearchActive()) {
            // Search pages through the already-ranked, bounded candidate set. No extra reads.
            loadMoreSearch();
            return;
        }
        if (Boolean.TRUE.equals(loadingMore.getValue()) || lastDocument == null) {
            return;
        }
        loadingMore.setValue(true);
        int generation = queryGeneration;
        questionRepository.loadMore(categoryId, sort, PAGE_SIZE, lastDocument,
                appendCallback(generation));
    }

    private void loadMoreSearch() {
        if (Boolean.TRUE.equals(loadingMore.getValue())
                || searchShown >= searchResults.size()) {
            lastDocument = null;
            return;
        }
        loadingMore.setValue(true);
        searchShown = Math.min(searchShown + PAGE_SIZE, searchResults.size());
        questionState.setValue(UiState.success(
                new ArrayList<>(searchResults.subList(0, searchShown)), fromCache));
        loadingMore.setValue(false);
    }

    private Callback<QuestionRepository.Page> appendCallback(int generation) {
        return new Callback<QuestionRepository.Page>() {
            @Override
            public void onSuccess(QuestionRepository.Page page) {
                if (generation != queryGeneration) {
                    return;
                }
                loadingMore.setValue(false);
                if (page.questions.isEmpty()) {
                    lastDocument = null;
                    return;
                }
                List<Question> merged = new ArrayList<>();
                UiState<List<Question>> current = questionState.getValue();
                if (current != null && current.getData() != null) {
                    merged.addAll(current.getData());
                }
                merged.addAll(page.questions);
                lastDocument = page.lastDocument;
                questionState.setValue(UiState.success(merged, fromCache));
            }

            @Override
            public void onError(Exception e) {
                if (generation == queryGeneration) {
                    loadingMore.setValue(false);
                }
            }
        };
    }

    // ------------------------------------------------------------------ internals

    private void restartQuestions() {
        searchHandler.removeCallbacks(searchRunnable);
        remove(questionRegistration);
        questionRegistration = null;
        lastDocument = null;
        searchResults.clear();
        searchShown = 0;
        loadingMore.setValue(false);
        attachQuestions();
    }

    private void attachQuestions() {
        searchHandler.removeCallbacks(searchRunnable);
        int generation = ++queryGeneration;
        questionState.setValue(UiState.<List<Question>>loading());
        searchResults.clear();
        searchShown = 0;
        lastDocument = null;
        loadingMore.setValue(false);

        if (!keyword.isEmpty()) {
            if (!TextUtils2.isSearchable(keyword)) {
                questionState.setValue(UiState.<List<Question>>empty());
                return;
            }
            questionRegistration = questionRepository.search(
                    TextUtils2.retrievalTokens(keyword), categoryId, sort,
                    QuestionRepository.SEARCH_CANDIDATE_LIMIT,
                    searchPageCallback(generation));
            return;
        }
        questionRegistration = questionRepository.observeLatest(
                categoryId, sort, PAGE_SIZE, pageCallback(generation));
    }

    private Callback<QuestionRepository.Page> searchPageCallback(int generation) {
        return new Callback<QuestionRepository.Page>() {
            @Override
            public void onSuccess(QuestionRepository.Page page) {
                if (generation != queryGeneration) {
                    return;
                }
                fromCache = page.fromCache;
                lastDocument = null;
                List<Question> ranked = SearchRanker.rankQuestions(
                        page.questions, keyword, tieBreaker());
                searchResults.clear();
                searchResults.addAll(ranked);
                int target = Math.min(Math.max(searchShown, Math.min(PAGE_SIZE, ranked.size())),
                        ranked.size());
                searchShown = target;
                if (ranked.isEmpty()) {
                    questionState.setValue(UiState.<List<Question>>empty());
                } else {
                    questionState.setValue(UiState.success(
                            new ArrayList<>(ranked.subList(0, target)), fromCache));
                }
            }

            @Override
            public void onError(Exception e) {
                if (generation != queryGeneration) {
                    return;
                }
                searchResults.clear();
                searchShown = 0;
                questionState.setValue(
                        UiState.<List<Question>>error(RepoErrors.message(e)));
            }
        };
    }

    private Callback<QuestionRepository.Page> pageCallback(int generation) {
        return new Callback<QuestionRepository.Page>() {
            @Override
            public void onSuccess(QuestionRepository.Page page) {
                if (generation != queryGeneration) {
                    return;
                }
                lastDocument = page.lastDocument;
                fromCache = page.fromCache;
                if (page.questions.isEmpty()) {
                    questionState.setValue(UiState.<List<Question>>empty());
                } else {
                    questionState.setValue(UiState.success(page.questions, page.fromCache));
                }
            }

            @Override
            public void onError(Exception e) {
                if (generation == queryGeneration) {
                    questionState.setValue(
                            UiState.<List<Question>>error(RepoErrors.message(e)));
                }
            }
        };
    }

    private Comparator<Question> tieBreaker() {
        Comparator<Question> byNewest = (left, right) -> {
            Date leftDate = left.getCreatedAt();
            Date rightDate = right.getCreatedAt();
            if (leftDate == null && rightDate == null) {
                return 0;
            }
            if (leftDate == null) {
                return 1;
            }
            if (rightDate == null) {
                return -1;
            }
            return rightDate.compareTo(leftDate);
        };
        if (QuestionRepository.SORT_POPULAR.equals(sort)) {
            return (left, right) -> {
                int byScore = Long.compare(right.getScore(), left.getScore());
                return byScore != 0 ? byScore : byNewest.compare(left, right);
            };
        }
        return byNewest;
    }

    private void invalidateGeneration() {
        queryGeneration++;
        lastDocument = null;
        loadingMore.setValue(false);
    }

    /** Recomputes the merged feed from the current question state, promos and filters. */
    private void rebuild() {
        UiState<List<Question>> questionsState = questionState.getValue();
        List<Question> questions = questionsState != null && questionsState.getData() != null
                ? questionsState.getData() : new ArrayList<>();
        List<Promo> promos = FeedMixer.filterByKeyword(promoState.getValue(), keyword);

        List<FeedItem> items = new ArrayList<>();
        if (categoryId == null && keyword.isEmpty()) {
            WordEntry word = wordOfDay.getValue();
            if (word != null) {
                items.add(FeedItem.of(word));
            }
        }
        items.addAll(FeedMixer.mix(questions, promos, categoryId, CADENCE));

        if (FeedMixer.FILTER_CENTER.equals(categoryId)) {
            if (!promosLoaded && items.isEmpty()) {
                feed.setValue(UiState.<List<FeedItem>>loading());
            } else if (items.isEmpty()) {
                feed.setValue(UiState.<List<FeedItem>>empty());
            } else {
                feed.setValue(UiState.success(items));
            }
            return;
        }

        if (questionsState == null) {
            feed.setValue(UiState.<List<FeedItem>>loading());
            return;
        }
        switch (questionsState.getStatus()) {
            case LOADING:
                feed.setValue(UiState.<List<FeedItem>>loading());
                break;
            case ERROR:
                feed.setValue(UiState.<List<FeedItem>>error(questionsState.getMessage()));
                break;
            case EMPTY:
                feed.setValue(items.isEmpty()
                        ? UiState.<List<FeedItem>>empty()
                        : UiState.success(items));
                break;
            case SUCCESS:
            default:
                feed.setValue(items.isEmpty()
                        ? UiState.<List<FeedItem>>empty()
                        : UiState.success(items, questionsState.isFromCache()));
                break;
        }
    }

    private static void remove(ListenerRegistration registration) {
        if (registration != null) {
            registration.remove();
        }
    }
}
