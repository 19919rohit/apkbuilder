package neunix.pagevibe;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.List;

/**
 * Sole owner of all Firestore reads for the Discover feature. Every read
 * strategy here exists to minimize billed document reads on the Spark
 * (free) plan — see fetchBooks() for the full decision tree.
 *
 * NEVER writes to Firestore. Download counts, ratings, etc. are never
 * incremented client-side.
 */
public class DiscoverBookRepository {

    private static final String COLLECTION    = "discover_books";
    private static final String PREFS_NAME    = "pagevibe_prefs";
    private static final String KEY_LAST_FETCH = "discover_last_fetch_ts";

    // How long a cold-start is allowed to trust the on-device cache
    // before forcing a real server read. Manual pull-to-refresh always
    // bypasses this.
    private static final long CACHE_TTL_MS = 30 * 60 * 1000L; // 30 minutes

    private static volatile DiscoverBookRepository instance;

    private final Context appContext;
    private final FirebaseFirestore firestore;

    // In-memory cache — lives for the process lifetime. Zero-cost reads
    // for every screen revisit within the same app session.
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

    /**
     * Decision tree, cheapest option first:
     *  1. In-memory list from this session, if present and not forced —
     *     zero cost, zero latency.
     *  2. Offline — read whatever's in the on-device Firestore cache
     *     (free, works with zero connectivity).
     *  3. Online, within TTL, cold start (no in-memory data yet) — try
     *     the free on-device cache FIRST; only fall back to a real
     *     server read if that cache is genuinely empty.
     *  4. Online, outside TTL, or forceRefresh=true — one real server
     *     read (updates the TTL timestamp).
     */
    public void fetchBooks(boolean forceRefresh, FetchCallback callback) {
        if (!forceRefresh && hasMemoryCache) {
            callback.onSuccess(new ArrayList<>(memoryCache), true);
            return;
        }

        boolean online = NetworkUtils.isOnline(appContext);
        boolean withinTtl = (System.currentTimeMillis() - getLastFetchTimestamp()) < CACHE_TTL_MS;

        if (!online) {
            readFromSource(Source.CACHE, callback, true);
            return;
        }

        if (forceRefresh || !withinTtl) {
            readFromSource(Source.SERVER, callback, false);
            return;
        }

        firestore.collection(COLLECTION).get(Source.CACHE)
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && !snapshot.isEmpty()) {
                        deliverSuccess(snapshot, callback, true);
                    } else {
                        readFromSource(Source.SERVER, callback, false);
                    }
                })
                .addOnFailureListener(e -> readFromSource(Source.SERVER, callback, false));
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
                    // Fall back to the local cache once before surfacing
                    // an error — the user might still get a perfectly
                    // usable (if slightly stale) list instead of a bare
                    // failure screen.
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

        if (!fromCache) setLastFetchTimestamp(System.currentTimeMillis());

        callback.onSuccess(new ArrayList<>(parsed), fromCache);
    }

    private String friendlyErrorMessage() {
        return "Couldn't load Discover books right now. Check your connection and try again.";
    }

    private long getLastFetchTimestamp() {
        return prefs().getLong(KEY_LAST_FETCH, 0L);
    }

    private void setLastFetchTimestamp(long ts) {
        prefs().edit().putLong(KEY_LAST_FETCH, ts).apply();
    }

    private SharedPreferences prefs() {
        return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}