package com.example.droidpod;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Service to control MediaPlayer object
 */
public class MediaPlayerService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnInfoListener, MediaPlayer.OnBufferingUpdateListener,
        AudioManager.OnAudioFocusChangeListener {

    private MediaPlayer mediaPlayer;
    private AudioManager audioManager;
    private int resumePosition;
    private AudioFocusRequest mFocusRequest;
    private AudioAttributes mPlaybackAttributes;

    // audio files
    protected ArrayList<Audio> audioList;
    protected int audioIndex = -1;
    protected Audio activeAudio;

    // phone vars
    private boolean onGoingCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    public static final String ACTION_PLAY = "com.example.droidPod.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.example.droidPod.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "com.example.droidPod.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "com.example.droidPod.ACTION_NEXT";
    public static final String ACTION_STOP = "com.example.droidPod.ACTION_STOP";

    //MediaSession
    private MediaSessionManager mediaSessionManager;
    private MediaSessionCompat mediaSession;
    protected MediaControllerCompat.TransportControls transportControls;

    //AudioPlayer notification ID
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "Transport Controller";

    //Playback Status
    protected PlaybackStatus mStatus;

    /**
     * Initialize the MediaPlayer object
     */
    private void initMediaPlayer() {
        mediaPlayer = new MediaPlayer();

        // event listeners
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnInfoListener(this);

        mediaPlayer.reset();

        // initialization of the audio attributes and focus request
        mPlaybackAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        mediaPlayer.setAudioAttributes(mPlaybackAttributes);

        try {
            mediaPlayer.setDataSource(activeAudio.getData());
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }
        mediaPlayer.prepareAsync();
    }

    /**
     * Invoked when service is created.
     * Init listeners for calls, output changes, and music to play.
     */
    @Override
    public void onCreate() {
        super.onCreate();

        // Notification channel instantiation
        createNotificationChannel();
        // Manage incoming phone calls during playback.
        // Pause MediaPlayer on incoming call,
        // Resume on hangup.
        callStateListener();

        //ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs -- BroadcastReceiver
        registerBecomingNoisyReceiver();

        //Listen for new Audio to play -- BroadcastReceiver
        register_playNewAudio();
    }

    /**
     * Invoked when service is destroyed.
     * Stops media, releases the media player, and removes audio focus.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            stopMedia();
            mediaPlayer.release();
        }
        removeAudioFocus();
        if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        removeNotification();

        //unregister BroadcastReceivers
        unregisterReceiver(becomingNoisyReceiver);
        unregisterReceiver(playNewAudio);

        //clear cached playlist
        new StorageService(getApplicationContext()).clearCachedAudioPlaylist();
    }

    /**
     * Start media playback
     */
    private void playMedia() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    /**
     * Stop media playback
     */
    private void stopMedia() {
        if (mediaPlayer == null)
            return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
    }

    /**
     * Pause media playback
     */
    private void pauseMedia() {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            resumePosition = mediaPlayer.getCurrentPosition();
        }
    }

    /**
     * Resume media playback
     */
    private void resumeMedia() {
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.seekTo(resumePosition);
            mediaPlayer.start();
        }
    }

    /**
     * Assign active audio track and initialize new media player
     */
    private final BroadcastReceiver playNewAudio = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //Get the new media index form SharedPreferences
            audioIndex = new StorageService(getApplicationContext()).loadAudioIndex();
            if (audioIndex != -1 && audioIndex < audioList.size()) {
                //index is in a valid range
                activeAudio = audioList.get(audioIndex);
            } else {
                stopSelf();
            }

            //A PLAY_NEW_AUDIO action received
            //reset mediaPlayer to play the new Audio
            stopMedia();
            mediaPlayer.reset();
            initMediaPlayer();
            updateMetaData();
            mStatus = PlaybackStatus.PLAYING;
            buildNotification();
        }
    };

    /**
     * register playNewAudio receiver
     */
    private void register_playNewAudio() {
        IntentFilter filter = new IntentFilter(MainActivity.Broadcast_PLAY_NEW_AUDIO);
        registerReceiver(playNewAudio, filter);
    }

    /**
     *  Initializes the media session
     * @throws RemoteException remote exception
     */
    private void initMediaSession() throws RemoteException {
        if (mediaSessionManager != null) return; //mediaSessionManager exists

        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);

        // Create a new MediaSession
        mediaSession = new MediaSessionCompat(getApplicationContext(), "AudioPlayer");

        //Get MediaSessions transport controls
        transportControls = mediaSession.getController().getTransportControls();

        //set MediaSession -> ready to receive media commands
        mediaSession.setActive(true);

        //indicate that the MediaSession handles transport control commands
        // through its MediaSessionCompat.Callback.
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        //Set mediaSession's MetaData
        updateMetaData();

        // Attach Callback to receive MediaSession updates
        mediaSession.setCallback(new MediaSessionCompat.Callback() {

            @Override
            public void onPlay() {
                super.onPlay();
                resumeMedia();
                mStatus = PlaybackStatus.PLAYING;
                buildNotification();
            }

            @Override
            public void onPause() {
                super.onPause();
                pauseMedia();
                mStatus = PlaybackStatus.PAUSED;
                buildNotification();
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                skipToNext();
                updateMetaData();
                mStatus = PlaybackStatus.PLAYING;
                buildNotification();
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();
                skipToPrevious();
                updateMetaData();
                mStatus = PlaybackStatus.PLAYING;
                buildNotification();
            }

            @Override
            public void onStop() {
                super.onStop();
                removeNotification();
                //Stop the service
                stopSelf();
            }

            @Override
            public void onSeekTo(long position) {
                super.onSeekTo(position);
            }
        });
    }

    /**
     * updates shown metadata to that of the currently playing track
     */
    private void updateMetaData() {
        Bitmap albumArt = getAlbumArt(this, activeAudio.getAlbumId());

        // Update the current metadata
        mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, albumArt)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, activeAudio.getArtist())
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, activeAudio.getAlbum())
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, activeAudio.getTitle())
                .build());
    }

    /**
     * Skips current track, and goes onto next in audio list
     */
    private void skipToNext() {
        if (audioIndex == audioList.size() - 1) {
            audioIndex = 0;
            activeAudio = audioList.get(audioIndex);
        } else {
            activeAudio = audioList.get(++audioIndex);
        }

        new StorageService(getApplicationContext()).storeAudioIndex(audioIndex);
        stopMedia();
        mediaPlayer.reset();
        initMediaPlayer();
    }

    /**
     * Goes back to previous track from current track
     */
    private void skipToPrevious() {
        // check track time to restart track if under 3 sec has passed
        if (getCurrentVal() < 3000) {
            // checks if current track is first in playlist
            if (audioIndex == 0) {
                audioIndex = audioList.size() - 1;
                activeAudio = audioList.get(audioIndex);
            } else {
                activeAudio = audioList.get(--audioIndex);
            }

            new StorageService(getApplicationContext()).storeAudioIndex(audioIndex);
            stopMedia();
            mediaPlayer.reset();
            initMediaPlayer();
        } else {
            mediaPlayer.seekTo(0);
        }
    }

    /**
     * gets playback time of the current song
     * @return String of the song position
     */
    public String getCurrentTime() {
        return timeToString(mediaPlayer.getCurrentPosition());
    }

    /**
     * gets duration of the current song
     * @return String of the song length
     */
    public String getEndTime() {
        return timeToString(mediaPlayer.getDuration());
    }

    /**
     * Convert time in int to a string with XX:XX format
     * @param time ms time toString
     * @return String conversion of the raw int data
     */
    public String timeToString(int time) {
        int minutes = time / 60000;
        int seconds = (time % 60000) / 1000;
        if (seconds < 10)
            return minutes + ":0" + seconds;
        return minutes + ":" + seconds;
    }

    /**
     * gets current position of the playback
     * @return mediaPlayer.getCurrentPosition();
     */
    public int getCurrentVal() {
        return mediaPlayer.getCurrentPosition();
    }

    /**
     * gets duration of the current song
     * @return mediaPlayer.getDuration();
     */
    public int getEndVal() {
        return mediaPlayer.getDuration();
    }

    /**
     * Move playback position
     * @param position target length to move playback to in ms
     */
    public void seekTo(int position) {
        mediaPlayer.seekTo(position);
    }

    /**
     * Builds notification for current play track
     */
    protected void buildNotification() {
        int notificationAction = android.R.drawable.ic_media_pause;
        PendingIntent play_pauseAction = null;

        // Build notification based on MediaPlayer state
        if (mStatus == PlaybackStatus.PLAYING) {
            notificationAction = android.R.drawable.ic_media_pause;
            play_pauseAction = playbackAction(1);
        } else if (mStatus == PlaybackStatus.PAUSED) {
            notificationAction = android.R.drawable.ic_media_play;
            play_pauseAction = playbackAction(0);
        }

        Bitmap largeIcon = getAlbumArt(this, activeAudio.getAlbumId());

        // Create notification
        NotificationCompat.Builder notificationBuilder = new NotificationCompat
                .Builder(this, CHANNEL_ID).setShowWhen(false)
                .setContentText(activeAudio.getArtist())
                .setContentTitle(activeAudio.getAlbum())
                .setContentInfo(activeAudio.getTitle())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2)).setColor(getResources()
                        .getColor(R.color.design_default_color_primary, getTheme()))
                .setLargeIcon(largeIcon).setSmallIcon(android.R.drawable.stat_sys_headset)
                .addAction(android.R.drawable.ic_media_previous, "previous", playbackAction(3))
                .addAction(notificationAction, "pause", play_pauseAction)
                .addAction(android.R.drawable.ic_media_next, "next", playbackAction(2));

        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE))
                .notify(NOTIFICATION_ID, notificationBuilder.build());
    }


    /**
     * Get album art of a particular album.
     * @param context context
     * @param album_id ID of the album to retrieve album art
     * @return album art bitmap
     */
    public static Bitmap getAlbumArt(Context context, Long album_id) {
        Bitmap albumArtBitMap = null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        try {

            final Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
            Uri uri = ContentUris.withAppendedId(sArtworkUri, album_id);
            ParcelFileDescriptor pfd = context.getContentResolver()
                    .openFileDescriptor(uri, "r");

            if (pfd != null) {
                FileDescriptor fd = pfd.getFileDescriptor();
                albumArtBitMap = BitmapFactory.decodeFileDescriptor(fd, null, options);
                pfd = null;
                fd = null;
            }
        } catch (Error | Exception ignored) { }

        return albumArtBitMap;
    }

    /**
     * Removes notification
     */
    private void removeNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    /**
     * Generates media player actions from notification triggers
     * @param actionNumber 0: Play, 1: Pause, 2: Next, 3: Prev
     * @return PENDING INTENT
     */
    protected PendingIntent playbackAction(int actionNumber) {
        Intent playbackAction = new Intent(this, MediaPlayerService.class);
        switch (actionNumber) {
            case 0:
                playbackAction.setAction(ACTION_PLAY);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 1:
                playbackAction.setAction(ACTION_PAUSE);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 2:
                playbackAction.setAction(ACTION_NEXT);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            case 3:
                playbackAction.setAction(ACTION_PREVIOUS);
                return PendingIntent.getService(this, actionNumber, playbackAction, 0);
            default:
                break;
        }
        return null;
    }

    /**
     * handles the playback action calls
     * @param playbackAction tells transport to play, pause, etc.
     */
    private void handleIncomingActions(Intent playbackAction) {
        if (playbackAction == null || playbackAction.getAction() == null) return;

        String actionString = playbackAction.getAction();
        if (actionString.equalsIgnoreCase(ACTION_PLAY)) {
            transportControls.play();
        } else if (actionString.equalsIgnoreCase(ACTION_PAUSE)) {
            transportControls.pause();
        } else if (actionString.equalsIgnoreCase(ACTION_NEXT)) {
            transportControls.skipToNext();
        } else if (actionString.equalsIgnoreCase(ACTION_PREVIOUS)) {
            transportControls.skipToPrevious();
        } else if (actionString.equalsIgnoreCase(ACTION_STOP)) {
            transportControls.stop();
        }
    }

    // Binder given to clients
    private final IBinder iBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    /**
     * Invoked when the network stream has updated buffering
     * @param mp media player
     * @param percent percent loaded
     */
    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        //Invoked indicating buffering status of
        //a media resource being streamed over the network.
    }

    /**
     * Stops media and service
     * Invoked upon completion of media playback
     * @param mp media player
     */
    @Override
    public void onCompletion(MediaPlayer mp) {
        stopMedia();
        stopSelf();
    }

    /**
     * Error handling function
     * Catches asynchronous operational errors
     * @param mp MediaPlayer object
     * @param i error
     * @param extra error descriptor
     * @return false on completion
     */
    @Override
    public boolean onError(MediaPlayer mp, int i, int extra) {
        switch (i) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Log.d("MediaPlayer Error", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.d("MediaPlayer Error", "MEDIA ERROR SERVER DIED " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.d("MediaPlayer Error", "MEDIA ERROR UNKNOWN " + extra);
                break;
        }
        return false;
    }

    /**
     * Invoked when info is being communicated
     * @param mp media player
     * @param what index
     * @param extra info
     * @return false
     */
    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {

        return false;
    }

    /**
     * Starts media playback
     * Invoked when the media playback is primed.
     * @param mp
     */
    @Override
    public void onPrepared(MediaPlayer mp) {
        playMedia();
    }

    /**
     * Invoked when the seek call has completed
     * @param mp
     */
    @Override
    public void onSeekComplete(MediaPlayer mp) {

    }

    /**
     * Invoked when the audio focus changes
     * @param focusChange
     */
    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            // audio focus gain => resume playback
            case AudioManager.AUDIOFOCUS_GAIN:
                if (mediaPlayer == null)
                    initMediaPlayer();
                else if (!mediaPlayer.isPlaying())
                    mediaPlayer.start();
                mediaPlayer.setVolume(1.0f, 1.0f);
                break;
            // audio focus loss => stop and release player
            case AudioManager.AUDIOFOCUS_LOSS:
                if (mediaPlayer.isPlaying())
                    mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
                break;
            // short audio focus loss => pause playback
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (mediaPlayer.isPlaying())
                    mediaPlayer.pause();
                break;
            // short audio focus loss (e.g. notification) => lower volume
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (mediaPlayer.isPlaying())
                    mediaPlayer.setVolume(0.1f, 0.1f);
                break;
        }
    }

    /**
     * Invoked when activity sends start request
     * Passes audio file to the service through getExtra()
     * @param intent intent
     * @param flags flags
     * @param startId start ID
     * @return super.onStartCommand()
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            StorageService storage = new StorageService(getApplicationContext());
            audioList = storage.loadAudio();
            audioIndex = storage.loadAudioIndex();

            if (audioIndex != -1 && audioIndex < audioList.size()) {
                activeAudio = audioList.get(audioIndex);
            } else {
                stopSelf();
            }
        } catch (NullPointerException e) {
            stopSelf();
        }


        if (mediaSessionManager == null) {
            try {
                initMediaSession();
                initMediaPlayer();
            } catch (RemoteException e) {
                e.printStackTrace();
                stopSelf();
            }
            mStatus = PlaybackStatus.PLAYING;
            buildNotification();
        }

        // check if audio focus can be gained
        if (!requestAudioFocus()) {
            stopSelf();
        }

        handleIncomingActions(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Requests audio focus
     * @return true if request has been granted
     */
    private boolean requestAudioFocus() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(mPlaybackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(this)
                .build();

        final Object mFocusLock = new Object();
        boolean mPlaybackDelayed = false;

        // requesting audio focus
        int res = audioManager.requestAudioFocus(mFocusRequest);
        synchronized (mFocusLock) {
            if (res == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                return false;
            } else if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                return true;
            } else if (res == AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
                mPlaybackDelayed = true;
                return true;
            }
        }
        return false;
    }

    /**
     * Removes audio focus
     * @return true if request to remove audio has been granted
     */
    private boolean removeAudioFocus() {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED == audioManager.abandonAudioFocusRequest(mFocusRequest);
    }

    /**
     * Binds media player service
     */
    public class LocalBinder extends Binder {
        public MediaPlayerService getService() {
            return MediaPlayerService.this;
        }
    }

    /**
     * Change in audio output
     */
    private final BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            pauseMedia();
            mStatus = PlaybackStatus.PAUSED;
            buildNotification();
        }
    };

    /**
     * Invoked on audio output change
     */
    private void registerBecomingNoisyReceiver() {
        //register after getting audio focus
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);
    }

    /**
     * Handles an on going call
     */
    private void callStateListener() {
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                // checks if phone is ringing or in call
                switch (state) {
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (mediaPlayer != null) {
                            pauseMedia();
                            onGoingCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        // Phone idle. Start playing.
                        if (mediaPlayer != null) {
                            if (onGoingCall) {
                                onGoingCall = false;
                                resumeMedia();
                            }
                        }
                        break;
                }
            }
        };
        // Register the listener with the telephony manager
        // Listen for changes to the device call state.
        telephonyManager.listen(phoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE);
    }

    /**
     * Invoked to create notification channel
     */
    private void createNotificationChannel() {
        CharSequence name = getString(R.string.channel_name);
        String description = getString(R.string.channel_description);
        int importance = NotificationManager.IMPORTANCE_LOW;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
        channel.setDescription(description);

        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
    }

    /**
     * Set resume position
     * @param pos position of track to resume in ms
     */
    public void setResumePosition(int pos) {
        resumePosition = pos;
    }
}