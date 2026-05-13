package neunix.pageflow;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;

public class PdfActivity extends Activity {

    private static final int PICK = 100;

    private PageFlipView pageFlipView;

    private PdfCore core;

    private int currentPage = 0;

    private Bitmap currentBitmap;
    private Bitmap nextBitmap;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        setContentView(R.layout.activity_pdf);

        pageFlipView =
                findViewById(R.id.pageFlipView);

        openPicker();
    }

    private void openPicker() {

        Intent i =
                new Intent(Intent.ACTION_OPEN_DOCUMENT);

        i.setType("application/pdf");

        i.addCategory(Intent.CATEGORY_OPENABLE);

        startActivityForResult(i, PICK);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == PICK
                && resultCode == RESULT_OK
                && data != null) {

            Uri uri = data.getData();

            try {

                core = new PdfCore();

                core.open(this, uri);

                loadPages();

                pageFlipView.setOnFlipListener(
                        new PageFlipView.OnFlipListener() {

                            @Override
                            public void onNextPage() {

                                if (currentPage <
                                        core.getPageCount() - 1) {

                                    currentPage++;

                                    loadPages();
                                }
                            }

                            @Override
                            public void onPreviousPage() {

                                if (currentPage > 0) {

                                    currentPage--;

                                    loadPages();
                                }
                            }
                        }
                );

            } catch (Exception e) {

                e.printStackTrace();
            }
        }
    }

    private void loadPages() {

        try {

            currentBitmap =
                    core.renderPage(
                            currentPage,
                            1080,
                            1920
                    );

            if (currentPage
                    < core.getPageCount() - 1) {

                nextBitmap =
                        core.renderPage(
                                currentPage + 1,
                                1080,
                                1920
                        );

            } else {

                nextBitmap = null;
            }

            pageFlipView.setBitmaps(
                    currentBitmap,
                    nextBitmap
            );

        } catch (Exception e) {

            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (core != null) {
            core.close();
        }
    }
}