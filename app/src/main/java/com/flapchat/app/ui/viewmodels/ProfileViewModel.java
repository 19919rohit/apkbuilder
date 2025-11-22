package com.flapchat.app.ui.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.flapchat.app.model.User;
import com.flapchat.app.data.repository.UserRepository;

public class ProfileViewModel extends AndroidViewModel {

    private final UserRepository repository;
    private final MutableLiveData<User> userLiveData = new MutableLiveData<>();

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        repository = UserRepository.getInstance(application);
    }

    public LiveData<User> getUser() {
        return userLiveData;
    }

    public void loadProfile(String userId) {
        repository.getUser(userId, user -> userLiveData.postValue(user));
    }

    public void updateProfile(User user) {
        repository.updateUser(user, success -> {
            if (success) loadProfile(user.getId());
        });
    }
}