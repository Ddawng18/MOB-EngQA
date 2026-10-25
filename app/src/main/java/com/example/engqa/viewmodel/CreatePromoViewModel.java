package com.example.engqa.viewmodel;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.engqa.data.Callback;
import com.example.engqa.data.CenterRepository;
import com.example.engqa.data.LinkStorageRepository;
import com.example.engqa.data.PromoRepository;
import com.example.engqa.data.RepoErrors;
import com.example.engqa.data.UserRepository;
import com.example.engqa.model.Center;
import com.example.engqa.model.Promo;
import com.example.engqa.model.UiState;
import com.example.engqa.model.User;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.Date;

/**
 * Backs "Đăng bài quảng bá": loads the author's role and centre, then writes a promo in
 * {@code PENDING} state. Only a CENTER account linked to a centre may submit.
 */
public class CreatePromoViewModel extends AndroidViewModel {

    private final UserRepository userRepository = new UserRepository();
    private final CenterRepository centerRepository = new CenterRepository();
    private final PromoRepository promoRepository = new PromoRepository();
    private final LinkStorageRepository storageRepository;
    private final MutableLiveData<UiState<String>> uploadState = new MutableLiveData<>();

    private final MutableLiveData<UiState<User>> user = new MutableLiveData<>();
    private final MutableLiveData<UiState<Center>> center = new MutableLiveData<>();
    private final MutableLiveData<UiState<String>> submitState = new MutableLiveData<>();

    private ListenerRegistration centerRegistration;
    private String uid;

    public CreatePromoViewModel(@NonNull Application application) {
        super(application);
        storageRepository = new LinkStorageRepository(application);
    }

    public LiveData<UiState<String>> getUploadState() {
        return uploadState;
    }

    public void uploadImage(Uri uri) {
        if (uid == null) {
            uploadState.setValue(UiState.<String>error("Bạn cần đăng nhập"));
            return;
        }
        uploadState.setValue(UiState.<String>loading());
        storageRepository.uploadImage(uri, uid, null, "promo", null,
                new Callback<String>() {
                    @Override
                    public void onSuccess(String result) {
                        uploadState.setValue(UiState.success(result));
                    }

                    @Override
                    public void onError(Exception e) {
                        uploadState.setValue(UiState.<String>error(RepoErrors.message(e)));
                    }
                });
    }

    public LiveData<UiState<User>> getUser() {
        return user;
    }

    public LiveData<UiState<Center>> getCenter() {
        return center;
    }

    public LiveData<UiState<String>> getSubmitState() {
        return submitState;
    }

    public void start(String currentUid) {
        if (currentUid == null) {
            user.setValue(UiState.<User>error("Bạn cần đăng nhập"));
            return;
        }
        if (currentUid.equals(uid) && user.getValue() != null) {
            return;
        }
        uid = currentUid;
        user.setValue(UiState.<User>loading());
        userRepository.getUser(currentUid, new Callback<User>() {
            @Override
            public void onSuccess(User result) {
                if (result == null) {
                    user.setValue(UiState.<User>error("Không tìm thấy hồ sơ"));
                    return;
                }
                user.setValue(UiState.success(result));
                bindCenter(result);
            }

            @Override
            public void onError(Exception e) {
                user.setValue(UiState.<User>error(RepoErrors.message(e)));
            }
        });
    }

    private void bindCenter(User profile) {
        if (centerRegistration != null) {
            centerRegistration.remove();
            centerRegistration = null;
        }
        String centerId = profile.getCenterId();
        if (centerId == null || centerId.isEmpty()) {
            center.setValue(UiState.<Center>error("Tài khoản chưa được gắn với trung tâm nào"));
            return;
        }
        center.setValue(UiState.<Center>loading());
        centerRegistration = centerRepository.observeCenter(centerId, new Callback<Center>() {
            @Override
            public void onSuccess(Center result) {
                if (result == null) {
                    center.setValue(UiState.<Center>error("Không tìm thấy trung tâm"));
                } else {
                    center.setValue(UiState.success(result));
                }
            }

            @Override
            public void onError(Exception e) {
                center.setValue(UiState.<Center>error(RepoErrors.message(e)));
            }
        });
    }

    public void stop() {
        if (centerRegistration != null) {
            centerRegistration.remove();
            centerRegistration = null;
        }
    }

    @Override
    protected void onCleared() {
        stop();
        storageRepository.shutdown();
        super.onCleared();
    }

    /**
     * Submits the form. The caller has already validated the fields with {@code PromoValidator};
     * server-side validation is duplicated in the security rules.
     */
    public void submit(String title, String body, String type, String level, String fee,
                       String schedule, String contactPhone, String linkUrl, String imageUrl,
                       Date endAt) {
        UiState<User> userState = user.getValue();
        if (userState == null || userState.getData() == null) {
            submitState.setValue(UiState.<String>error("Bạn cần đăng nhập"));
            return;
        }
        User profile = userState.getData();
        if (!User.ROLE_CENTER.equals(profile.getRole())) {
            submitState.setValue(UiState.<String>error(
                    "Chỉ tài khoản trung tâm mới được đăng bài"));
            return;
        }
        UiState<Center> centerState = center.getValue();
        if (centerState == null || centerState.getData() == null) {
            submitState.setValue(UiState.<String>error(
                    "Tài khoản chưa được gắn với trung tâm nào"));
            return;
        }
        Center centerData = centerState.getData();

        Promo promo = new Promo();
        promo.setCenterId(centerData.getId() != null
                ? centerData.getId() : profile.getCenterId());
        promo.setCenterName(centerData.getName());
        promo.setCenterVerified(centerData.isVerified());
        promo.setAuthorUid(profile.getUid());
        promo.setTitle(title.trim());
        promo.setBody(body.trim());
        promo.setType(type);
        promo.setLevel(level);
        promo.setFee(fee);
        promo.setSchedule(schedule);
        promo.setContactPhone(contactPhone);
        promo.setLinkUrl(linkUrl);
        promo.setImageUrl(imageUrl);
        promo.setEndAt(endAt);

        submitState.setValue(UiState.<String>loading());
        promoRepository.createPromo(promo, new Callback<String>() {
            @Override
            public void onSuccess(String promoId) {
                submitState.setValue(UiState.success(promoId));
            }

            @Override
            public void onError(Exception e) {
                submitState.setValue(UiState.error(RepoErrors.message(e)));
            }
        });
    }
}
