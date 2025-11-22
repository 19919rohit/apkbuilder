package com.flapchat.app.ui.activities;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.flapchat.app.R;
import com.flapchat.app.ui.adapters.ChatAdapter;
import com.flapchat.app.ui.viewmodels.ChatViewModel;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView chatRecycler;
    private ChatAdapter chatAdapter;
    private EditText inputMessage;
    private ImageButton sendButton;
    private ChatViewModel chatViewModel;
    private String chatId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        chatId = getIntent().getStringExtra("CHAT_ID");

        chatRecycler = findViewById(R.id.recycler_chat);
        chatRecycler.setLayoutManager(new LinearLayoutManager(this));
        chatAdapter = new ChatAdapter(this);
        chatRecycler.setAdapter(chatAdapter);

        inputMessage = findViewById(R.id.edit_message);
        sendButton = findViewById(R.id.button_send);

        chatViewModel = new ChatViewModel(chatId);

        inputMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Could send typing indicator here
                chatViewModel.setTypingStatus(!s.toString().isEmpty());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        sendButton.setOnClickListener(v -> {
            String msg = inputMessage.getText().toString();
            if (!msg.trim().isEmpty()) {
                chatViewModel.sendMessage(msg);
                inputMessage.setText("");
            }
        });

        loadMessages();
    }

    private void loadMessages() {
        chatViewModel.getMessages().observe(this, messages -> {
            chatAdapter.submitList(messages);
            chatRecycler.scrollToPosition(messages.size() - 1);
        });
    }
}