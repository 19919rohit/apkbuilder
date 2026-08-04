package neunix.pagevibe;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DiscoverBookAdapter extends RecyclerView.Adapter<DiscoverBookAdapter.VH> {

    public interface Listener {
        void onDownloadClicked(DiscoverBook book);
        void onOpenClicked(DiscoverBook book);
        void onBookClicked(DiscoverBook book);
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
            bindDownloadState(holder, book);
            return;
        }

        holder.title.setText(book.getTitle());
        holder.author.setText(book.getAuthor());

        if (book.getRating() > 0) {
            holder.ratingBadge.setVisibility(View.VISIBLE);
            holder.ratingBadge.setText(String.format(Locale.getDefault(), "★ %.1f", book.getRating()));
        } else {
            holder.ratingBadge.setVisibility(View.GONE);
        }

        StringBuilder meta = new StringBuilder();
        if (!book.getCategory().isEmpty()) meta.append(book.getCategory());
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

        holder.itemView.setOnClickListener(v -> listener.onBookClicked(book));
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
        TextView ratingBadge, featuredBadge, title, author, meta;
        TextView btnDownload, btnOpen;
        LinearLayout progressGroup;
        ProgressBar progressBar;
        TextView progressText;

        VH(View v) {
            super(v);
            cover         = v.findViewById(R.id.discoverCardCover);
            ratingBadge   = v.findViewById(R.id.discoverCardRatingBadge);
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