package neunix.pagevibe;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Fires when ANY DownloadManager download completes system-wide,
 * including ones started by other apps — this receiver filters strictly
 * to download IDs this Fragment instance is actually tracking before
 * doing anything, so it can never react to unrelated downloads.
 *
 * Registered/unregistered around DiscoverFragment's lifecycle (see
 * onResume/onPause there) rather than in the manifest, since it only
 * needs to matter while the Discover screen might actually be visible —
 * a manifest-registered implicit broadcast receiver for this action is
 * also disallowed on modern Android for non-exported system broadcasts
 * in many cases, so a context-registered receiver is the correct choice
 * here regardless.
 */
public class DiscoverDownloadReceiver extends BroadcastReceiver {

    public interface Listener {
        void onDownloadComplete(long downloadId);
    }

    private final Listener listener;

    public DiscoverDownloadReceiver(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
        if (id != -1L && listener != null) {
            listener.onDownloadComplete(id);
        }
    }
}