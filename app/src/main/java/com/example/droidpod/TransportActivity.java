package com.example.droidpod;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class TransportActivity extends AppCompatActivity {

    private MediaPlayerService player;

    private TextView title;
    private TextView artist;
    private TextView album;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transport);

        title = (TextView)findViewById(R.id.textTitle);
        artist = (TextView)findViewById(R.id.textArtist);
        album = (TextView)findViewById(R.id.textAlbum);

        setMetadata();
    }

    private void setMetadata() {
        title.setText();
        artist.setText();
        album.setText();
    }

    private void onPrevButtonClick() {
        player.playbackAction(3);
    }

    private void onNextButtonClick() {
        player.playbackAction(2);
    }

    private void onPlayPauseButtonClick() {

    }
}