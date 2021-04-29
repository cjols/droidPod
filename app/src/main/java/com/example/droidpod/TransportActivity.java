package com.example.droidpod;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class TransportActivity extends AppCompatActivity {

    private MediaPlayerService player;
    private boolean mBound;

    private TextView title;
    private TextView artist;
    private TextView album;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transport);

        LocalBroadcastManager.getInstance(TransportActivity.this).registerReceiver(mMessageReceiver,
                new IntentFilter("com.example.droidPod.REQUEST_PROCESSED"));

        title = (TextView)findViewById(R.id.textTitle);
        artist = (TextView)findViewById(R.id.textArtist);
        album = (TextView)findViewById(R.id.textAlbum);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mBound) {
            Intent mIntent = new Intent(this, MediaPlayerService.class);
            bindService(mIntent, mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder) service;
            player = binder.getService();
            mBound = true;
            setMetadata();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
        }
    };

    private void setMetadata() {
        title.setText(player.activeAudio.getTitle());
        artist.setText(player.activeAudio.getArtist());
        album.setText(player.activeAudio.getAlbum());
    }

    public void onPrevButtonClick(View v) {
        player.playbackAction(3);
        setMetadata();
    }

    public void onNextButtonClick(View v) {
        player.playbackAction(2);
        setMetadata();
    }

    public void onPlayPauseButtonClick(View v) {
        // TODO play pause logic
        // alternate between play and pause button image and playback action
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                Bundle data = intent.getExtras();
                String valueReceived = data.getString("DATA");
                Log.e("recv2", valueReceived);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };
}