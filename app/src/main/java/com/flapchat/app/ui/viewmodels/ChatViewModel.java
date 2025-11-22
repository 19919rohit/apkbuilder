package com.flapchat.app.ui.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.flapchat.app.model.Message;
import com.flapchat.app.data.repository.ChatRepository;
import java.util.List;

public class ChatViewModel extends AndroidViewModel {

    private final ChatRepository repository;
    private final MutableLiveData<List<Message>> messages = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isTyping = new MutableLiveData<>(false);

    public ChatViewModel(@NonNull Application application) {
        super(application);
        repository = ChatRepository.getInstance(application);
    }

    public LiveData<List<Message>> getMessages() {
        return messages;
    }

    public LiveData<Boolean> getTypingStatus() {
        return isTyping;
    }

    public void loadMessages(String roomId) {
        repository.getMessages(roomId, new ChatRepository.MessagesCallback() {
            @Override
            public void onMessagesLoaded(List<Message> loadedMessages) {
                messages.postValue(loadedMessages);
            }
        });
    }

    public void sendMessage(String roomId, Message message) {
        repository.sendMessage(roomId, message, success -> {
            if (success) loadMessages(roomId);
        });
    }

    public void setTyping(boolean typing) {
        isTyping.postValue(typing);
        repository.sendTypingStatus(typing);
    }
}