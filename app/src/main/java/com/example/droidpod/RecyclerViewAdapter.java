package com.example.droidpod;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class RecyclerViewAdapter extends RecyclerView.Adapter<RecyclerViewAdapter.ViewHolder> {

    private static final String TAG = "RecyclerViewAdapter";

    private List<Audio> list = Collections.emptyList();
    private Context mContext;

    public RecyclerViewAdapter(Context mContext, ArrayList<Audio> list) {
        this.list = list;
        this.mContext = mContext;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_listitem, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String text = (list.get(position).getArtist()) + " - " + (list.get(position).getAlbum());
        holder.title.setText(list.get(position).getTitle());
        holder.artistAlbum.setText(text);
        holder.albumArt.setImageBitmap(MediaPlayerService.getAlbumArt(this.mContext, list.get(position).getAlbumId()));

        Glide.with(mContext)
                .asBitmap()
                .load(MediaPlayerService.getAlbumArt(this.mContext, list.get(position).getAlbumId()))
                .into(holder.albumArt);
    }

    @Override
    public int getItemCount() {
        return list.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        TextView title;
        TextView artistAlbum;
        CircleImageView albumArt;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = (TextView) itemView.findViewById(R.id.title);
            artistAlbum = (TextView) itemView.findViewById(R.id.artist_album);
            albumArt = (CircleImageView) itemView.findViewById(R.id.album);
        }
    }
}

