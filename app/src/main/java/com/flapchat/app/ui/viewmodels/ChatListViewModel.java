package com.flapchat.app.ui.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.flapchat.app.model.Chat;
import com.flapchat.app.data.repository.ChatRepository;
import java.util.List;

public class ChatListViewModel extends AndroidViewModel {

    private final ChatRepository repository;
    private final MutableLiveData<List<Chat>> chatList = new MutableLiveData<>();

    public ChatListViewModel(@NonNull Application application) {
        super(application);
        repository = ChatRepository.getInstance(application);
    }

    public LiveData<List<Chat>> getChatList() {
        return chatList;
    }

    public void loadChats() {
        repository.getUserChats(new ChatRepository.ChatListCallback() {
            @Override
            public void onChatsLoaded(List<Chat> chats) {
                chatList.postValue(chats);
            }
        });
    }
}