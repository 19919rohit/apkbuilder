package neunix.pagevibe.app;

import android.app.Application;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

public class PageVibeApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationChannels.ensureCreated(this);
        configureFirestoreOfflinePersistence();
    }

    /**
     * Enables Firestore's on-device SQLite persistence cache. This is
     * what makes DiscoverBookRepository's Source.CACHE reads possible —
     * without this explicitly set, cache reads have nothing durable to
     * fall back on across app restarts. Must be configured before any
     * Firestore call is ever made, so the Application class is the only
     * correct place for it.
     */
    private void configureFirestoreOfflinePersistence() {
        try {
            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .build();
            FirebaseFirestore.getInstance().setFirestoreSettings(settings);
        } catch (Throwable ignored) {
            // Firebase/Firestore misconfiguration must never block app
            // startup entirely.
        }
    }
}