package com.example.engqa.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.engqa.data.Callback;
import com.example.engqa.data.CenterRepository;
import com.example.engqa.data.RepoErrors;
import com.example.engqa.model.Center;
import com.example.engqa.model.CenterPost;
import com.example.engqa.model.Course;
import com.example.engqa.model.UiState;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;

/** Backs the centre list, centre detail and centre edit screens. */
public class CenterViewModel extends ViewModel {

    private final CenterRepository centerRepository = new CenterRepository();

    private final MutableLiveData<UiState<List<Center>>> centers = new MutableLiveData<>();
    private final MutableLiveData<UiState<Center>> center = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<Course>>> courses = new MutableLiveData<>();
    private final MutableLiveData<UiState<List<CenterPost>>> posts = new MutableLiveData<>();
    private final MutableLiveData<UiState<String>> actionState = new MutableLiveData<>();

    private ListenerRegistration centersRegistration;
    private ListenerRegistration centerRegistration;
    private ListenerRegistration coursesRegistration;
    private ListenerRegistration postsRegistration;

    private String centerId;

    public LiveData<UiState<List<Center>>> getCenters() {
        return centers;
    }

    public LiveData<UiState<Center>> getCenter() {
        return center;
    }

    public LiveData<UiState<List<Course>>> getCourses() {
        return courses;
    }

    public LiveData<UiState<List<CenterPost>>> getPosts() {
        return posts;
    }

    public LiveData<UiState<String>> getActionState() {
        return actionState;
    }

    public String getCenterId() {
        return centerId;
    }

    public void startList() {
        if (centersRegistration != null) {
            return;
        }
        centers.setValue(UiState.<List<Center>>loading());
        centersRegistration = centerRepository.observeApprovedCenters(
                new Callback<List<Center>>() {
                    @Override
                    public void onSuccess(List<Center> result) {
                        centers.setValue(result.isEmpty()
                                ? UiState.<List<Center>>empty()
                                : UiState.success(result));
                    }

                    @Override
                    public void onError(Exception e) {
                        centers.setValue(UiState.<List<Center>>error(RepoErrors.message(e)));
                    }
                });
    }

    public void startDetail(String centerId) {
        if (this.centerId != null && this.centerId.equals(centerId) && centerRegistration != null) {
            return;
        }
        this.centerId = centerId;
        attachDetail();
    }

    public void stop() {
        removeAll();
    }

    @Override
    protected void onCleared() {
        stop();
        super.onCleared();
    }

    public void requestCenter(String ownerUid, Center newCenter) {
        actionState.setValue(UiState.<String>loading());
        centerRepository.requestCenter(ownerUid, newCenter, new Callback<String>() {
            @Override
            public void onSuccess(String id) {
                actionState.setValue(UiState.success(id));
            }

            @Override
            public void onError(Exception e) {
                actionState.setValue(UiState.<String>error(RepoErrors.message(e)));
            }
        });
    }

    public void updateCenter(Center updated) {
        if (centerId == null) {
            return;
        }
        centerRepository.updateCenter(centerId, updated, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                actionState.setValue(UiState.success("center_updated"));
            }

            @Override
            public void onError(Exception e) {
                actionState.setValue(UiState.<String>error(RepoErrors.message(e)));
            }
        });
    }

    public void addCourse(Course course) {
        if (centerId == null) {
            return;
        }
        centerRepository.addCourse(centerId, course, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                actionState.setValue(UiState.success("course_added"));
            }

            @Override
            public void onError(Exception e) {
                actionState.setValue(UiState.<String>error(RepoErrors.message(e)));
            }
        });
    }

    public void addPost(CenterPost post) {
        if (centerId == null) {
            return;
        }
        centerRepository.addPost(centerId, post, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                actionState.setValue(UiState.success("post_added"));
            }

            @Override
            public void onError(Exception e) {
                actionState.setValue(UiState.<String>error(RepoErrors.message(e)));
            }
        });
    }

    /** Whether the signed-in user manages the centre currently loaded in the detail screen. */
    public boolean isCurrentUserManager(String uid) {
        UiState<Center> state = center.getValue();
        return state != null && state.getData() != null
                && uid != null && state.getData().getManagerUids() != null
                && state.getData().getManagerUids().contains(uid);
    }

    private void attachDetail() {
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
        coursesRegistration = centerRepository.observeCourses(centerId,
                new Callback<List<Course>>() {
                    @Override
                    public void onSuccess(List<Course> result) {
                        courses.setValue(result.isEmpty()
                                ? UiState.<List<Course>>empty()
                                : UiState.success(result));
                    }

                    @Override
                    public void onError(Exception e) {
                        courses.setValue(UiState.<List<Course>>empty());
                    }
                });
        postsRegistration = centerRepository.observePosts(centerId,
                new Callback<List<CenterPost>>() {
                    @Override
                    public void onSuccess(List<CenterPost> result) {
                        posts.setValue(result.isEmpty()
                                ? UiState.<List<CenterPost>>empty()
                                : UiState.success(result));
                    }

                    @Override
                    public void onError(Exception e) {
                        posts.setValue(UiState.<List<CenterPost>>empty());
                    }
                });
    }

    private void removeAll() {
        if (centersRegistration != null) {
            centersRegistration.remove();
            centersRegistration = null;
        }
        if (centerRegistration != null) {
            centerRegistration.remove();
            centerRegistration = null;
        }
        if (coursesRegistration != null) {
            coursesRegistration.remove();
            coursesRegistration = null;
        }
        if (postsRegistration != null) {
            postsRegistration.remove();
            postsRegistration = null;
        }
    }
}
