package com.flapchat.app.data.local;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.flapchat.app.data.local.dao.ChatDao;
import com.flapchat.app.data.local.dao.MessageDao;
import com.flapchat.app.data.local.entities.ChatEntity;
import com.flapchat.app.data.local.entities.MessageEntity;
import com.flapchat.app.utils.Converters;

@Database(
        entities = {
                ChatEntity.class,
                MessageEntity.class
        },
        version = 1,
        exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    public abstract ChatDao chatDao();
    public abstract MessageDao messageDao();
}