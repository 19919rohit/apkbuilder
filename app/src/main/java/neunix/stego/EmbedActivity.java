package neunix.stego;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EmbedActivity extends AppCompatActivity {

    private Uri carrierUri;
    private Uri payloadUri;

    private Bitmap carrierBitmap;

    private ImageView carrierPreview;
    private TextView tvCarrierInfo, tvFilePayloadInfo;
    private EditText etPassword, etTextMessage;
    private ProgressBar progress;
    private Button btnEmbed;

    private RadioGroup radioPayloadType;
    private LinearLayout layoutFile, layoutText;
    private Spinner spinnerExpansionMode;

    private final Map<Integer, Bitmap> expansionCache = new HashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    private ActivityResultLauncher<Intent> externalCarrierPicker;
    private ActivityResultLauncher<Intent> payloadPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_embed);

        bindViews();
        setupDropdown();
        setupPickers();
        setupValidation();

        btnEmbed.setOnClickListener(v -> embed());

        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> onBackPressed());
    }

    private void bindViews() {

        carrierPreview = findViewById(R.id.carrierPreview);
        tvCarrierInfo = findViewById(R.id.tvCarrierInfo);
        tvFilePayloadInfo = findViewById(R.id.tvFilePayloadInfo);

        etPassword = findViewById(R.id.etPassword);
        etTextMessage = findViewById(R.id.etTextMessage);

        progress = findViewById(R.id.progressBar);
        btnEmbed = findViewById(R.id.btnEmbed);

        radioPayloadType = findViewById(R.id.radioPayloadType);
        layoutFile = findViewById(R.id.layoutFile);
        layoutText = findViewById(R.id.layoutText);

        spinnerExpansionMode = findViewById(R.id.spinnerExpand);

        Button pickCarrierBtn = findViewById(R.id.pickCarrierBtn);
        Button pickPayloadBtn = findViewById(R.id.pickPayloadBtn);

        pickCarrierBtn.setOnClickListener(v -> showPickerDialog());
        pickPayloadBtn.setOnClickListener(v -> pick(payloadPicker, "*/*"));
    }

    private void setupDropdown() {

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_item,
                new String[]{"Normal","Expand 25%","Expand 50%","Expand 100%"}
        );

        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spinnerExpansionMode.setAdapter(adapter);

        spinnerExpansionMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                updatePreviewAndCapacity(pos);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}

        });
    }

    private void setupPickers() {

        externalCarrierPicker =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        r -> {

                            if (r.getResultCode() == RESULT_OK && r.getData()!=null) {

                                carrierUri = r.getData().getData();
                                expansionCache.clear();

                                new Thread(this::loadCarrier).start();

                                validateReady();
                            }
                        });

        payloadPicker =
                registerForActivityResult(
                        new ActivityResultContracts.StartActivityForResult(),
                        r -> {

                            if (r.getResultCode()==RESULT_OK && r.getData()!=null) {

                                payloadUri = r.getData().getData();

                                tvFilePayloadInfo.setText(fileName(payloadUri));

                                validateReady();
                                refreshCapacity();
                            }
                        });
    }

    private void showPickerDialog() {

        String[] options = {"App Files","Other Apps / Gallery"};

        new AlertDialog.Builder(this)

                .setTitle("Select Carrier Image")

                .setItems(options,(dialog,which)->{

                    if(which==0){

                        pickInternalFile("Embedded",file->{

                            carrierUri = Uri.fromFile(file);

                            expansionCache.clear();

                            new Thread(this::loadCarrier).start();

                            validateReady();

                        });

                    }
                    else{

                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);

                        i.addCategory(Intent.CATEGORY_OPENABLE);

                        i.setType("image/*");

                        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                        externalCarrierPicker.launch(i);
                    }

                })

                .show();
    }

    private void pickInternalFile(String subFolder,InternalFileCallback callback){

        try{

            File dir = Utils.getBaseDir(this, subFolder);

            File[] files = dir.listFiles();

            if(files==null || files.length==0){

                toast("No internal files found");

                return;
            }

            String[] names = new String[files.length];

            for(int i=0;i<files.length;i++){

                names[i]=files[i].getName();
            }

            new AlertDialog.Builder(this)

                    .setTitle("Select File")

                    .setItems(names,(d,w)->callback.onFileSelected(files[w]))

                    .show();

        }
        catch(Exception e){

            toast("Internal file error");
        }
    }

    private interface InternalFileCallback{

        void onFileSelected(File file);
    }

    private void setupValidation(){

        etTextMessage.addTextChangedListener(watcher);

        etPassword.addTextChangedListener(watcher);
    }

    private final TextWatcher watcher = new TextWatcher(){

        @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}

        @Override
        public void onTextChanged(CharSequence s,int start,int before,int count){

            refreshCapacity();

            validateReady();
        }

        @Override public void afterTextChanged(Editable s){}
    };

    private void validateReady(){

        boolean ready =
                carrierUri!=null &&
                (radioPayloadType.getCheckedRadioButtonId()==R.id.radioText
                        ? !etTextMessage.getText().toString().trim().isEmpty()
                        : payloadUri!=null);

        btnEmbed.setEnabled(ready);

        btnEmbed.setAlpha(ready?1f:0.5f);
    }

    private void refreshCapacity(){

        updatePreviewAndCapacity(spinnerExpansionMode.getSelectedItemPosition());
    }

    private void updatePreviewAndCapacity(int modeIndex){

        if(carrierBitmap==null) return;

        executor.submit(()->{

            Bitmap bmp;

            synchronized(expansionCache){

                bmp = expansionCache.get(modeIndex);
            }

            if(bmp==null){

                int factor = mapModeToFactor(modeIndex);

                bmp = ContentAwareExpander.expand(carrierBitmap,factor);

                synchronized(expansionCache){

                    expansionCache.put(modeIndex,bmp);
                }
            }

            int max = StegEngineCore.getMaxPayloadSize(bmp);

            int current =
                    radioPayloadType.getCheckedRadioButtonId()==R.id.radioText
                            ? etTextMessage.getText().toString().getBytes(StandardCharsets.UTF_8).length
                            : payloadUri==null ? 0 : getFileSize(payloadUri);

            int finalCurrent=current;

            Bitmap finalBmp=bmp;

            runOnUiThread(()->{

                carrierPreview.setImageBitmap(finalBmp);

                tvCarrierInfo.setText(
                        "Payload size: "+Utils.formatSize(finalCurrent)+
                        "\nAvailable capacity: "+Utils.formatSize(max)
                );

                tvCarrierInfo.setTextColor(
                        getColor(finalCurrent>max?R.color.red:R.color.green)
                );
            });
        });
    }

    private int mapModeToFactor(int index){

        switch(index){

            case 1: return 1;
            case 2: return 2;
            case 3: return 4;
            default: return 0;
        }
    }

    private void loadCarrier(){

        try{

            carrierBitmap = JPGtoPNG.convert(this,carrierUri);

            int max = StegEngineCore.getMaxPayloadSize(carrierBitmap);

            runOnUiThread(()->{

                carrierPreview.setImageBitmap(carrierBitmap);

                tvCarrierInfo.setText(
                        "Resolution: "+
                        carrierBitmap.getWidth()+" x "+
                        carrierBitmap.getHeight()+
                        "\nMax capacity: "+Utils.formatSize(max)
                );
            });

        }
        catch(Exception e){

            toast("Carrier error");
        }
    }

    private void embed(){

        if(carrierBitmap==null){

            toast("Select carrier first");

            return;
        }

        progress.setVisibility(View.VISIBLE);

        btnEmbed.setEnabled(false);

        executor.submit(()->{

            try{

                int mode = spinnerExpansionMode.getSelectedItemPosition();

                Bitmap bmp = carrierBitmap;

                byte[] payloadBytes;

                String originalName;

                if(radioPayloadType.getCheckedRadioButtonId()==R.id.radioText){

                    String text = etTextMessage.getText().toString();

                    payloadBytes=text.getBytes(StandardCharsets.UTF_8);

                    originalName="message.txt";
                }
                else{

                    originalName=fileName(payloadUri);

                    try(InputStream in=getContentResolver().openInputStream(payloadUri);
                        ByteArrayOutputStream baos=new ByteArrayOutputStream()){

                        byte[] buf=new byte[8192];

                        int n;

                        while((n=in.read(buf))!=-1) baos.write(buf,0,n);

                        payloadBytes=baos.toByteArray();
                    }
                }

                File outFile = Utils.getTimestampedFile(this,"stego.png","Embedded");

                try(FileOutputStream out=new FileOutputStream(outFile)){

                    StegEngineCore.embed(
                            bmp,
                            payloadBytes,
                            originalName,
                            etPassword.getText().toString(),
                            out
                    );
                }

                runOnUiThread(()->toast("Embedded and saved to Files"));
            }
            catch(Exception e){

                runOnUiThread(()->toast("Embed failed"));
            }
            finally{

                runOnUiThread(()->{

                    progress.setVisibility(View.GONE);

                    btnEmbed.setEnabled(true);
                });
            }
        });
    }

    private int getFileSize(Uri uri){

        try(Cursor c=getContentResolver().query(uri,null,null,null,null)){

            if(c!=null && c.moveToFirst()){

                int idx=c.getColumnIndex(OpenableColumns.SIZE);

                if(idx>=0) return (int)c.getLong(idx);
            }
        }
        catch(Exception ignored){}

        return 0;
    }

    private String fileName(Uri uri){

        try(Cursor c=getContentResolver().query(uri,null,null,null,null)){

            if(c!=null && c.moveToFirst()){

                int idx=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);

                if(idx>=0) return c.getString(idx);
            }
        }
        catch(Exception ignored){}

        return "file";
    }

    private void pick(ActivityResultLauncher<Intent> l,String type){

        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);

        i.addCategory(Intent.CATEGORY_OPENABLE);

        i.setType(type);

        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        l.launch(i);
    }

    private void toast(String s){

        runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show());
    }
}