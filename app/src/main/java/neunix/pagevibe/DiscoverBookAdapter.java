package neunix.pagevibe;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for the Discover grid. Two things keep this
 * efficient at "hundreds or thousands of books" scale:
 *
 *  1. DiffUtil-backed updateBooks() — sorting/filtering/searching never
 *     calls notifyDataSetChanged(); only the rows that actually changed
 *     get rebound, so re-sorting a 2,000-item list doesn't repaint 2,000
 *     views.
 *  2. Progress updates use a PAYLOAD (notifyItemChanged(pos, PAYLOAD))
 *     instead of a full rebind — while a download is running, only the
 *     progress bar/text repaint on each tick; the cover image is never
 *     re-requested from Glide, the title/author TextViews are never
 *     re-measured.
 */
public class DiscoverBookAdapter extends RecyclerView.Adapter<DiscoverBookAdapter.VH> {

    public interface Listener {
        void onDownloadClicked(DiscoverBook book);
        void onOpenClicked(DiscoverBook book);
    }

    private static final Object PAYLOAD_PROGRESS = new Object();

    private final Context context;
    private final Listener listener;
    private final DiscoverDownloadManagerHelper downloadHelper;

    private final List<DiscoverBook> books = new ArrayList<>();

    public DiscoverBookAdapter(Context context, DiscoverDownloadManagerHelper downloadHelper, Listener listener) {
        this.context = context;
        this.downloadHelper = downloadHelper;
        this.listener = listener;
    }

    /** DiffUtil-backed replace — see class doc. Safe to call from the
     *  main thread with any size list; DiffUtil.calculateDiff runs
     *  synchronously here since Discover lists are at most a few
     *  thousand items (well within DiffUtil's comfortable synchronous
     *  range — no background-thread diffing needed at this scale). */
    public void updateBooks(List<DiscoverBook> newBooks) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return books.size(); }
            @Override public int getNewListSize() { return newBooks.size(); }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return books.get(oldPos).getBookId().equals(newBooks.get(newPos).getBookId());
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                return books.get(oldPos).equals(newBooks.get(newPos));
            }
        });

        books.clear();
        books.addAll(newBooks);
        result.dispatchUpdatesTo(this);
    }

    /** Call this on every DownloadManager progress tick for a specific
     *  book — routes through the DiffUtil payload path, never a full
     *  rebind. */
    public void notifyProgressChanged(String bookId) {
        for (int i = 0; i < books.size(); i++) {
            if (books.get(i).getBookId().equals(bookId)) {
                notifyItemChanged(i, PAYLOAD_PROGRESS);
                return;
            }
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_discover_book_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        onBindViewHolder(holder, position, new ArrayList<>());
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position, @NonNull List<Object> payloads) {
        DiscoverBook book = books.get(position);

        if (!payloads.isEmpty() && payloads.contains(PAYLOAD_PROGRESS)) {
            // Cheap path — only the download-state UI needs refreshing.
            bindDownloadState(holder, book);
            return;
        }

        // Full bind
        holder.title.setText(book.getTitle());
        holder.author.setText(book.getAuthor());
        holder.itemView.setOnClickListener(v -> listener.onBookClicked(book));

        StringBuilder meta = new StringBuilder();
        if (book.getRating() > 0) meta.append("★ ").append(String.format(Locale.getDefault(), "%.1f", book.getRating()));
        if (!book.getCategory().isEmpty()) {
            if (meta.length() > 0) meta.append("  ·  ");
            meta.append(book.getCategory());
        }
        if (book.getPages() > 0) {
            if (meta.length() > 0) meta.append("  ·  ");
            meta.append(book.getPages()).append(" pages");
        }
        if (!book.getDownloadSize().isEmpty()) {
            if (meta.length() > 0) meta.append("  ·  ");
            meta.append(book.getDownloadSize());
        }
        holder.meta.setText(meta.toString());

        holder.featuredBadge.setVisibility(book.isFeatured() ? View.VISIBLE : View.GONE);

        DiscoverImageLoader.load(context, book.getCoverUrl(), holder.cover);

        bindDownloadState(holder, book);

        holder.itemView.setTag(book.getBookId());
    }

    private void bindDownloadState(VH holder, DiscoverBook book) {
        DiscoverDownloadManagerHelper.DownloadState state = downloadHelper.getState(book.getBookId());

        holder.btnDownload.setVisibility(View.GONE);
        holder.progressGroup.setVisibility(View.GONE);
        holder.btnOpen.setVisibility(View.GONE);

        switch (state) {
            case DOWNLOADED:
                holder.btnOpen.setVisibility(View.VISIBLE);
                holder.btnOpen.setOnClickListener(v -> listener.onOpenClicked(book));
                break;

            case DOWNLOADING:
            case PENDING:
            case PAUSED: {
                holder.progressGroup.setVisibility(View.VISIBLE);
                int progress = downloadHelper.getProgress(book.getBookId());
                holder.progressBar.setProgress(progress);
                String label = state == DiscoverDownloadManagerHelper.DownloadState.PAUSED
                        ? "Paused — waiting for connection"
                        : (progress > 0 ? "Downloading… " + progress + "%" : "Starting download…");
                holder.progressText.setText(label);
                break;
            }

            case NOT_DOWNLOADED:
            case FAILED:
            default:
                holder.btnDownload.setVisibility(View.VISIBLE);
                holder.btnDownload.setText(state == DiscoverDownloadManagerHelper.DownloadState.FAILED
                        ? "Retry Download" : "Download");
                holder.btnDownload.setOnClickListener(v -> {
                    // Immediate UI feedback before the async enqueue even
                    // returns — this is the "disable button instantly"
                    // requirement.
                    holder.btnDownload.setVisibility(View.GONE);
                    holder.progressGroup.setVisibility(View.VISIBLE);
                    holder.progressText.setText("Starting download…");
                    holder.progressBar.setProgress(0);
                    listener.onDownloadClicked(book);
                });
                break;
        }
    }

    @Override
    public void onViewRecycled(@NonNull VH holder) {
        super.onViewRecycled(holder);
        DiscoverImageLoader.clear(context, holder.cover);
    }

    @Override
    public int getItemCount() { return books.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView cover;
        TextView featuredBadge, title, author, meta;
        TextView btnDownload, btnOpen;
        LinearLayout progressGroup;
        ProgressBar progressBar;
        TextView progressText;

        VH(View v) {
            super(v);
            cover         = v.findViewById(R.id.discoverCardCover);
            featuredBadge = v.findViewById(R.id.discoverCardFeaturedBadge);
            title         = v.findViewById(R.id.discoverCardTitle);
            author        = v.findViewById(R.id.discoverCardAuthor);
            meta          = v.findViewById(R.id.discoverCardMeta);
            btnDownload   = v.findViewById(R.id.discoverCardBtnDownload);
            btnOpen       = v.findViewById(R.id.discoverCardBtnOpen);
            progressGroup = v.findViewById(R.id.discoverCardProgressGroup);
            progressBar   = v.findViewById(R.id.discoverCardProgressBar);
            progressText  = v.findViewById(R.id.discoverCardProgressText);
        }
    }
}