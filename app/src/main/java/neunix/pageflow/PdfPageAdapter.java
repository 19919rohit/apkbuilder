package neunix.pageflow;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class PdfPageAdapter extends RecyclerView.Adapter<PdfPageAdapter.VH> {

    private List<Bitmap> pages = new ArrayList<>();

    public void setPages(List<Bitmap> list) {
        pages = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_page, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        holder.image.setImageBitmap(pages.get(position));
    }

    @Override
    public int getItemCount() {
        return pages.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView image;

        public VH(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.pageImage);
        }
    }
}