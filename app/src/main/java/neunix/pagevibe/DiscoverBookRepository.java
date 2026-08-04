package neunix.pagevibe;

import android.content.Context;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.List;

/**
 * Sole owner of all Firestore reads for the Discover feature.
 *
 * READ STRATEGY (simplified from an earlier TTL-based version that was
 * too aggressive — a SharedPreferences-persisted 30-minute TTL meant a
 * force-closed-then-reopened app could still silently prefer stale
 * on-device cache over a real server read, which is exactly what made
 * new content invisible):
 *
 *   1. In-memory cache hit (same process, already fetched once this
 *      session) → reuse it. Zero cost, zero latency. This alone
 *      satisfies "cache for the app session."
 *   2. Fresh process (app reopened after being killed/removed from
 *      recents) → the in-memory cache is gone (it's a plain instance
 *      field, not persisted), so this always does one real SERVER read.
 *   3. Pull-to-refresh (forceRefresh=true) → always a real SERVER read.
 *   4. Genuinely offline → falls back to the free on-device Source.CACHE
 *      read so the screen still shows something instead of an error.
 *
 * Never writes to Firestore — no download-count increments, no
 * analytics documents.
 */
public class DiscoverBookRepository {

    private static final String COLLECTION = "discover_books";

    private static volatile DiscoverBookRepository instance;

    private final Context appContext;
    private final FirebaseFirestore firestore;

    private final List<DiscoverBook> memoryCache = new ArrayList<>();
    private volatile boolean hasMemoryCache = false;

    private DiscoverBookRepository(Context context) {
        this.appContext = context.getApplicationContext();
        this.firestore = FirebaseFirestore.getInstance();
    }

    public static DiscoverBookRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (DiscoverBookRepository.class) {
                if (instance == null) instance = new DiscoverBookRepository(context);
            }
        }
        return instance;
    }

    public interface FetchCallback {
        void onSuccess(List<DiscoverBook> books, boolean servedFromLocalCache);
        void onError(String message);
    }

    public void fetchBooks(boolean forceRefresh, FetchCallback callback) {
        if (!forceRefresh && hasMemoryCache) {
            callback.onSuccess(new ArrayList<>(memoryCache), true);
            return;
        }

        boolean online = NetworkUtils.isOnline(appContext);

        if (!online) {
            readFromSource(Source.CACHE, callback, true);
            return;
        }

        readFromSource(Source.SERVER, callback, false);
    }

    private void readFromSource(Source source, FetchCallback callback, boolean isCacheSource) {
        firestore.collection(COLLECTION).get(source)
                .addOnSuccessListener(snapshot -> deliverSuccess(snapshot, callback, isCacheSource))
                .addOnFailureListener(e -> {
                    if (isCacheSource) {
                        callback.onError(friendlyErrorMessage());
                        return;
                    }
                    // A SERVER read can fail mid-flight (Wi-Fi to mobile
                    // handoff, brief Firestore hiccup, request timeout).
                    // Fall back to local cache once before surfacing an
                    // error, rather than a bare failure screen.
                    firestore.collection(COLLECTION).get(Source.CACHE)
                            .addOnSuccessListener(cacheSnapshot -> {
                                if (cacheSnapshot != null && !cacheSnapshot.isEmpty()) {
                                    deliverSuccess(cacheSnapshot, callback, true);
                                } else {
                                    callback.onError(friendlyErrorMessage());
                                }
                            })
                            .addOnFailureListener(e2 -> callback.onError(friendlyErrorMessage()));
                });
    }

    private void deliverSuccess(QuerySnapshot snapshot, FetchCallback callback, boolean fromCache) {
        List<DiscoverBook> parsed = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            DiscoverBook book = DiscoverBook.fromDocument(doc);
            if (book != null) parsed.add(book);
        }

        memoryCache.clear();
        memoryCache.addAll(parsed);
        hasMemoryCache = true;

        callback.onSuccess(new ArrayList<>(parsed), fromCache);
    }

    private String friendlyErrorMessage() {
        return "Couldn't load Discover books right now. Check your connection and try again.";
    }
}