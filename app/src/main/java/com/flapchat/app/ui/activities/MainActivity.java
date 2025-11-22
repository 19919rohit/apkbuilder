package com.flapchat.app.ui.activities;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.flapchat.app.R;
import com.flapchat.app.model.Chat;
import com.flapchat.app.ui.adapters.ChatListAdapter;
import com.flapchat.app.ui.viewmodels.ChatListViewModel;

import java.util.List;

public class MainActivity extends AppCompatActivity implements ChatListAdapter.OnChatClickListener {

    private RecyclerView chatListRecycler;
    private ChatListAdapter chatListAdapter;
    private ChatListViewModel chatListViewModel;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Setup system theme dynamically
        int nightModeFlags = getResources().getConfiguration().uiMode &
                Configuration.UI_MODE_NIGHT_MASK;
        if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        // RecyclerView setup
        chatListRecycler = findViewById(R.id.recycler_chat_list);
        chatListRecycler.setLayoutManager(new LinearLayoutManager(this));
        chatListAdapter = new ChatListAdapter(this);
        chatListAdapter.setOnChatClickListener(this);
        chatListRecycler.setAdapter(chatListAdapter);

        emptyView = findViewById(R.id.text_empty_chats);

        // ViewModel
        chatListViewModel = new ChatListViewModel();

        // Observe chats (LiveData)
        chatListViewModel.getChatsLiveData().observe(this, new Observer<List<Chat>>() {
            @Override
            public void onChanged(List<Chat> chats) {
                if (chats == null || chats.isEmpty()) {
                    emptyView.setVisibility(View.VISIBLE);
                    chatListRecycler.setVisibility(View.GONE);
                } else {
                    emptyView.setVisibility(View.GONE);
                    chatListRecycler.setVisibility(View.VISIBLE);
                    chatListAdapter.submitList(chats);
                }
            }
        });

        // Load chats initially
        chatListViewModel.loadChats();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh chats when returning to activity
        chatListViewModel.loadChats();
    }

    // ChatListAdapter click callback
    @Override
    public void onChatClick(@NonNull Chat chat) {
        openChat(chat.getId());
    }

    @Override
    public void onChatLongClick(@NonNull Chat chat) {
        // Example: open multi-select mode or show options
        chatListAdapter.toggleSelection(chat);
    }

    // Navigate to ChatActivity
    public void openChat(String chatId) {
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("CHAT_ID", chatId);
        startActivity(intent);
    }
}