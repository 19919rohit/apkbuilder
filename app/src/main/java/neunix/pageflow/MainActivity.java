package neunix.pageflow;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;

import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int PICK_PDF = 100;

    private PdfCore pdfCore;
    private ViewPager2 viewPager;
    private PdfPageAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pdfCore = new PdfCore(this);

        viewPager = findViewById(R.id.viewPager);
        adapter = new PdfPageAdapter();
        viewPager.setAdapter(adapter);

        openPicker();
    }

    private void openPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/pdf");
        startActivityForResult(intent, PICK_PDF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_PDF && data != null) {

            Uri uri = data.getData();
            pdfCore.open(uri);

            loadPages();
        }
    }

    private void loadPages() {

        int count = pdfCore.pageCount();
        List<Bitmap> pages = new ArrayList<>();

        int w = 1080;
        int h = 1920;

        for (int i = 0; i < count; i++) {
            pages.add(pdfCore.renderPage(i, w, h));
        }

        adapter.setPages(pages);
    }
}