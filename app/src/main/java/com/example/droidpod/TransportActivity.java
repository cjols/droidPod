package com.example.droidpod;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.bumptech.glide.Glide;

public class TransportActivity extends AppCompatActivity {

    private MediaPlayerService player;
    private boolean mBound;
    private int curProg;

    private TextView title;
    private TextView artist;
    private TextView album;
    private TextView curTime;
    private TextView endTime;
    private ImageButton playPauseBtn;
    private ImageView albumArtImg;
    private SeekBar seekBar;

    final Handler handler = new Handler();
    final Runnable r = new Runnable() {
        public void run() {
            handler.postDelayed(this, 1000);
            try {
                if (player.mStatus == PlaybackStatus.PLAYING)
                    updateTime();
                if (curProg >= player.getEndVal())
                    player.transportControls.skipToNext();
                if (player.activeAudio.getTitle() != title.getText())
                    setMetadata();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transport);

        initializeActivity();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mConnection);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mBound) {
            Intent mIntent = new Intent(this, MediaPlayerService.class);
            bindService(mIntent, mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private void initializeActivity() {
        // init class broadcast manager
        LocalBroadcastManager.getInstance(TransportActivity.this).registerReceiver(mMessageReceiver,
                new IntentFilter("com.example.droidPod.REQUEST_PROCESSED"));
        // init views
        title = (TextView) findViewById(R.id.textTitle);
        artist = (TextView) findViewById(R.id.textArtist);
        album = (TextView) findViewById(R.id.textAlbum);
        curTime = (TextView) findViewById(R.id.textCurTime);
        endTime = (TextView) findViewById(R.id.textEndTime);
        albumArtImg = (ImageView) findViewById(R.id.imageAlbumArt);
        playPauseBtn = (ImageButton) findViewById(R.id.playPauseButton);
        seekBar = (SeekBar) findViewById(R.id.seekBar);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // set seek bar to change song progress
                if (fromUser) {
                    player.seekTo(progress);
                    if (player.mStatus == PlaybackStatus.PAUSED) {
                        player.setResumePosition(progress);
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder) service;
            player = binder.getService();
            mBound = true;
            setMetadata();
            r.run();
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
        endTime.setText(player.getEndTime());
        seekBar.setMax(player.getEndVal());

        Glide.with(this)
                .asBitmap()
                .load(MediaPlayerService.getAlbumArt(this, player.activeAudio.getAlbumId()))
                .into(albumArtImg);
    }

    private void updateTime() throws InterruptedException {
            curTime.setText(player.getCurrentTime());
            curProg = player.getCurrentVal();
            seekBar.setProgress(curProg);
    }

    public void onPrevButtonClick(View v) {
        player.transportControls.skipToPrevious();
        playPauseBtn.setImageResource(R.drawable.pause);
    }

    public void onNextButtonClick(View v) {
        player.transportControls.skipToNext();
        playPauseBtn.setImageResource(R.drawable.pause);
    }

    public void onPlayPauseButtonClick(View v) {
        // alternate between play and pause button image and playback action
        if (player.mStatus == PlaybackStatus.PLAYING) { // pause
            playPauseBtn.setImageResource(R.drawable.play);
            player.transportControls.pause();
        } else {         // play
            playPauseBtn.setImageResource(R.drawable.pause);
            player.transportControls.play();
        }
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