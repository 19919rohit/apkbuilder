package neunix.stego;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.List;

public class FileAdapter extends ArrayAdapter<File> {

    private Context context;
    private List<File> files;

    public FileAdapter(Context context, List<File> files) {
        super(context, 0, files);
        this.context = context;
        this.files = files;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        if (convertView == null) {
            convertView = LayoutInflater.from(context)
                    .inflate(R.layout.item_file, parent, false);
        }

        File file = files.get(position);

        TextView fileName = convertView.findViewById(R.id.tvFileName);
        ImageButton shareBtn = convertView.findViewById(R.id.btnShare);
        ImageButton deleteBtn = convertView.findViewById(R.id.btnDelete);

        fileName.setText(file.getName());

        // SHARE BUTTON
        shareBtn.setOnClickListener(v -> shareFile(file));

        // DELETE BUTTON
        deleteBtn.setOnClickListener(v -> {

            boolean deleted = file.delete();

            if (deleted) {

                files.remove(position);
                notifyDataSetChanged();

                Toast.makeText(context, "File deleted", Toast.LENGTH_SHORT).show();

            } else {

                Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show();

            }

        });

        return convertView;
    }

    private void shareFile(File file) {

        try {

            Uri uri = FileProvider.getUriForFile(
                    context,
                    "neunix.stego.provider",
                    file
            );

            Intent intent = new Intent(Intent.ACTION_SEND);

            // Forces sharing as document instead of image preview
            intent.setType("*/*");

            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            context.startActivity(
                    Intent.createChooser(intent, "Share File")
            );

        } catch (Exception e) {

            Toast.makeText(context, "Share failed", Toast.LENGTH_SHORT).show();
            e.printStackTrace();

        }
    }
}