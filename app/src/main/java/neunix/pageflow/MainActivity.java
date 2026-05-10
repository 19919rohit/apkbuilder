package neunix.pageflow;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.viewpager2.widget.ViewPager2;

public class MainActivity extends Activity {

    private static final int PICK_PDF = 100;

    private PdfCore pdfCore;
    private PdfPageAdapter adapter;
    private ViewPager2 viewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        viewPager = findViewById(R.id.viewPager);

        openPicker();
    }

    private void openPicker() {

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");

        startActivityForResult(intent, PICK_PDF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_PDF && resultCode == RESULT_OK && data != null) {

            Uri uri = data.getData();

            pdfCore = new PdfCore(this);
            pdfCore.open(uri);

            adapter = new PdfPageAdapter(this, pdfCore);

            viewPager.setAdapter(adapter);

            // keep only nearby pages alive
            viewPager.setOffscreenPageLimit(1);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (pdfCore != null) {
            pdfCore.close();
        }
    }
}