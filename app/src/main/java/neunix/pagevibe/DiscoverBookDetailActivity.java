package neunix.pagevibe;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Locale;

public class DiscoverBookDetailActivity extends AppCompatActivity {

    private static final String EXTRA_BOOK_ID       = "extra_book_id";
    private static final String EXTRA_TITLE         = "extra_title";
    private static final String EXTRA_AUTHOR        = "extra_author";
    private static final String EXTRA_DESCRIPTION   = "extra_description";
    private static final String EXTRA_CATEGORY      = "extra_category";
    private static final String EXTRA_LANGUAGE      = "extra_language";
    private static final String EXTRA_RATING        = "extra_rating";
    private static final String EXTRA_DOWNLOADS     = "extra_downloads";
    private static final String EXTRA_FEATURED      = "extra_featured";
    private static final String EXTRA_PAGES         = "extra_pages";
    private static final String EXTRA_DOWNLOAD_SIZE = "extra_download_size";
    private static final String EXTRA_COVER_URL     = "extra_cover_url";
    private static final String EXTRA_PDF_URL       = "extra_pdf_url";
    private static final String EXTRA_KEYWORDS      = "extra_keywords";

    public static Intent createIntent(Context context, DiscoverBook book) {
        Intent i = new Intent(context, DiscoverBookDetailActivity.class);
        i.putExtra(EXTRA_BOOK_ID, book.getBookId());
        i.putExtra(EXTRA_TITLE, book.getTitle());
        i.putExtra(EXTRA_AUTHOR, book.getAuthor());
        i.putExtra(EXTRA_DESCRIPTION, book.getDescription());
        i.putExtra(EXTRA_CATEGORY, book.getCategory());
        i.putExtra(EXTRA_LANGUAGE, book.getLanguage());
        i.putExtra(EXTRA_RATING, book.getRating());
        i.putExtra(EXTRA_DOWNLOADS, book.getDownloads());
        i.putExtra(EXTRA_FEATURED, book.isFeatured());
        i.putExtra(EXTRA_PAGES, book.getPages());
        i.putExtra(EXTRA_DOWNLOAD_SIZE, book.getDownloadSize());
        i.putExtra(EXTRA_COVER_URL, book.getCoverUrl());
        i.putExtra(EXTRA_PDF_URL, book.getPdfUrl());
        i.putStringArrayListExtra(EXTRA_KEYWORDS, new ArrayList<>(book.getSearchKeywords()));
        return i;
    }

    private DiscoverBook book;
    private ThemeManager themeManager;
    private DiscoverDownloadManagerHelper downloadHelper;
    private DiscoverDownloadReceiver downloadReceiver;

    private TextView btnDownload, btnOpen, progressText;
    private LinearLayout progressGroup;
    private ProgressBar progressBar;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressPollRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discover_book_detail);

        book = bookFromIntent(getIntent());
        themeManager = new ThemeManager(this);
        downloadHelper = new DiscoverDownloadManagerHelper(this);

        findViewById(R.id.detailBtnBack).setOnClickListener(v -> finish());
        bindContent();

        btnDownload   = findViewById(R.id.detailBtnDownload);
        btnOpen       = findViewById(R.id.detailBtnOpen);
        progressGroup = findViewById(R.id.detailProgressGroup);
        progressBar   = findViewById(R.id.detailProgressBar);
        progressText  = findViewById(R.id.detailProgressText);

        refreshActionState();
        ThemeApplier.apply(findViewById(android.R.id.content), themeManager.getActiveTheme());
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeApplier.apply(findViewById(android.R.id.content), themeManager.getActiveTheme());
        registerDownloadReceiver();
        refreshActionState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterDownloadReceiver();
        stopProgressPolling();
    }

    private DiscoverBook bookFromIntent(Intent intent) {
        return new DiscoverBook(
                intent.getStringExtra(EXTRA_BOOK_ID),
                intent.getStringExtra(EXTRA_TITLE),
                intent.getStringExtra(EXTRA_AUTHOR),
                intent.getStringExtra(EXTRA_DESCRIPTION),
                intent.getStringExtra(EXTRA_CATEGORY),
                intent.getStringExtra(EXTRA_LANGUAGE),
                intent.getDoubleExtra(EXTRA_RATING, 0.0),
                intent.getLongExtra(EXTRA_DOWNLOADS, 0L),
                intent.getBooleanExtra(EXTRA_FEATURED, false),
                intent.getLongExtra(EXTRA_PAGES, 0L),
                intent.getStringExtra(EXTRA_DOWNLOAD_SIZE),
                intent.getStringExtra(EXTRA_COVER_URL),
                intent.getStringExtra(EXTRA_PDF_URL),
                intent.getStringArrayListExtra(EXTRA_KEYWORDS)
        );
    }

    private void bindContent() {
        ((TextView) findViewById(R.id.detailTitle)).setText(book.getTitle());
        ((TextView) findViewById(R.id.detailAuthor)).setText("by " + book.getAuthor());
        findViewById(R.id.detailFeaturedBadge).setVisibility(book.isFeatured() ? View.VISIBLE : View.GONE);

        String catLang = book.getCategory();
        if (!book.getLanguage().isEmpty()) catLang += "  ·  " + book.getLanguage();
        ((TextView) findViewById(R.id.detailCategoryLanguage)).setText(catLang);

        ((TextView) findViewById(R.id.detailStatRating))
                .setText(book.getRating() > 0 ? String.format(Locale.getDefault(), "★ %.1f", book.getRating()) : "—");
        ((TextView) findViewById(R.id.detailStatDownloads)).setText(book.formatDownloads());
        ((TextView) findViewById(R.id.detailStatPages)).setText(book.getPages() > 0 ? String.valueOf(book.getPages()) : "—");
        ((TextView) findViewById(R.id.detailStatSize)).setText(!book.getDownloadSize().isEmpty() ? book.getDownloadSize() : "—");

        TextView description = findViewById(R.id.detailDescription);
        description.setText(!book.getDescription().isEmpty() ? book.getDescription() : "No description available for this book.");

        ImageView cover = findViewById(R.id.detailCover);
        DiscoverImageLoader.load(this, book.getCoverUrl(), cover);
    }

    private void refreshActionState() {
        DiscoverDownloadManagerHelper.DownloadState state = downloadHelper.getState(book.getBookId());

        btnDownload.setVisibility(View.GONE);
        progressGroup.setVisibility(View.GONE);
        btnOpen.setVisibility(View.GONE);

        switch (state) {
            case DOWNLOADED:
                btnOpen.setVisibility(View.VISIBLE);
                btnOpen.setOnClickListener(v -> openBook());
                DiscoverLibrarySync.syncIfNeeded(this, book, downloadHelper.getDownloadedUri(book.getBookId()));
                break;

            case DOWNLOADING:
            case PENDING:
            case PAUSED:
                progressGroup.setVisibility(View.VISIBLE);
                updateProgressUi(state);
                startProgressPolling();
                break;

            case NOT_DOWNLOADED:
            case FAILED:
            default:
                btnDownload.setVisibility(View.VISIBLE);
                btnDownload.setText(state == DiscoverDownloadManagerHelper.DownloadState.FAILED
                        ? "Retry Download" : "Download");
                btnDownload.setOnClickListener(v -> startDownload());
                break;
        }
    }

    private void startDownload() {
        if (!book.hasValidPdfUrl()) {
            Toast.makeText(this, "This book doesn't have a downloadable file", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!NetworkUtils.isOnline(this)) {
            Toast.makeText(this, "You're offline — connect to the internet to download", Toast.LENGTH_LONG).show();
            return;
        }

        btnDownload.setVisibility(View.GONE);
        progressGroup.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        progressText.setText("Starting download…");

        long id = downloadHelper.startDownload(book);
        if (id == -1L) {
            Toast.makeText(this, "Couldn't start the download. Please try again.", Toast.LENGTH_SHORT).show();
            refreshActionState();
            return;
        }
        startProgressPolling();
    }

    private void openBook() {
        Uri fileUri = downloadHelper.getDownloadedUri(book.getBookId());
        if (fileUri == null) {
            Toast.makeText(this, "File not found — try downloading again", Toast.LENGTH_SHORT).show();
            refreshActionState();
            return;
        }
        Intent intent = new Intent(this, PdfActivity.class);
        intent.setData(fileUri);
        startActivity(intent);
    }

    private void updateProgressUi(DiscoverDownloadManagerHelper.DownloadState state) {
        int progress = downloadHelper.getProgress(book.getBookId());
        progressBar.setProgress(progress);
        String label = state == DiscoverDownloadManagerHelper.DownloadState.PAUSED
                ? "Paused — waiting for connection"
                : (progress > 0 ? "Downloading… " + progress + "%" : "Starting download…");
        progressText.setText(label);
    }

    private void startProgressPolling() {
        if (progressPollRunnable != null) return;
        progressPollRunnable = new Runnable() {
            @Override
            public void run() {
                DiscoverDownloadManagerHelper.DownloadState state = downloadHelper.getState(book.getBookId());
                if (state == DiscoverDownloadManagerHelper.DownloadState.DOWNLOADING
                        || state == DiscoverDownloadManagerHelper.DownloadState.PENDING
                        || state == DiscoverDownloadManagerHelper.DownloadState.PAUSED) {
                    updateProgressUi(state);
                    progressHandler.postDelayed(this, 800L);
                } else {
                    progressPollRunnable = null;
                    refreshActionState();
                }
            }
        };
        progressHandler.post(progressPollRunnable);
    }

    private void stopProgressPolling() {
        if (progressPollRunnable != null) {
            progressHandler.removeCallbacks(progressPollRunnable);
            progressPollRunnable = null;
        }
    }

    private void registerDownloadReceiver() {
        if (downloadReceiver != null) return;
        downloadReceiver = new DiscoverDownloadReceiver(id -> {
            if (downloadHelper.matchesStoredId(book.getBookId(), id)) refreshActionState();
        });
        registerReceiver(downloadReceiver, new IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    private void unregisterDownloadReceiver() {
        if (downloadReceiver == null) return;
        try { unregisterReceiver(downloadReceiver); } catch (Throwable ignored) {}
        downloadReceiver = null;
    }
}