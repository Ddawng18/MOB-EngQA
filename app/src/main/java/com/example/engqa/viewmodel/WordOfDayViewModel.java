package com.example.engqa.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.engqa.data.Callback;
import com.example.engqa.data.RepoErrors;
import com.example.engqa.data.WordRepository;
import com.example.engqa.model.UiState;
import com.example.engqa.model.WordEntry;

/** Backs the word card in the feed and the word detail screen. */
public class WordOfDayViewModel extends AndroidViewModel {

    private final WordRepository repository = new WordRepository();
    private final MutableLiveData<UiState<WordEntry>> word = new MutableLiveData<>();

    public WordOfDayViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<UiState<WordEntry>> getWord() {
        return word;
    }

    public void loadToday(boolean force) {
        word.setValue(UiState.<WordEntry>loading());
        repository.loadToday(getApplication(), force, callback());
    }

    public void loadWord(String value, boolean force) {
        word.setValue(UiState.<WordEntry>loading());
        repository.load(getApplication(), value, force, callback());
    }

    private Callback<WordEntry> callback() {
        return new Callback<WordEntry>() {
            @Override
            public void onSuccess(WordEntry result) {
                if (result == null) {
                    word.setValue(UiState.<WordEntry>error("Không tải được từ vựng"));
                } else {
                    word.setValue(UiState.success(result));
                }
            }

            @Override
            public void onError(Exception e) {
                word.setValue(UiState.<WordEntry>error(RepoErrors.message(e)));
            }
        };
    }

    @Override
    protected void onCleared() {
        repository.shutdown();
        super.onCleared();
    }
}
