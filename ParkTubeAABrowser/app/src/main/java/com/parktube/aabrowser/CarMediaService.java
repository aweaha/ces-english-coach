package com.parktube.aabrowser;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.media.MediaBrowserServiceCompat;

import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import java.util.ArrayList;
import java.util.List;

public class CarMediaService extends MediaBrowserServiceCompat {
  private static final String ROOT_ID = "pwk_root";
  private static final String MEDIA_ID = "pwk_audio";
  private MediaSessionCompat mediaSession;
  private boolean syncReceiverRegistered = false;
  private final BroadcastReceiver syncReceiver = new BroadcastReceiver() {
    @Override public void onReceive(Context context, Intent intent) {
      if (intent == null || !"com.pwk.parktube.ACTION_SYNC".equals(intent.getAction())) return;
      String title = intent.getStringExtra("title");
      boolean paused = intent.getBooleanExtra("paused", true);
      long position = intent.getLongExtra("position", 0L);
      long duration = intent.getLongExtra("duration", 0L);

      MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
        .putString(MediaMetadataCompat.METADATA_KEY_TITLE,
          title == null || title.isEmpty() ? "ParkTube PWK" : title)
        .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "ParkTube PWK")
        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Android Auto")
        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
        .build();
      mediaSession.setMetadata(metadata);
      updateState(paused ? PlaybackStateCompat.STATE_PAUSED : PlaybackStateCompat.STATE_PLAYING, position);
    }
  };

  @Override public void onCreate() {
    super.onCreate();

    mediaSession = new MediaSessionCompat(this, "ParkTubePWKCar");
    mediaSession.setCallback(new MediaSessionCompat.Callback() {
      @Override public void onPlay() {
        sendCommand("com.pwk.parktube.ACTION_PLAY");
        updateState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN);
      }

      @Override public void onPause() {
        sendCommand("com.pwk.parktube.ACTION_PAUSE");
        updateState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN);
      }

      @Override public void onPlayFromMediaId(String mediaId, Bundle extras) {
        if (MEDIA_ID.equals(mediaId)) {
          sendCommand("com.pwk.parktube.ACTION_PLAY");
          updateState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN);
        }
      }

      @Override public void onSkipToNext() {
        sendCommand("com.pwk.parktube.ACTION_NEXT");
      }

      @Override public void onSkipToPrevious() {
        sendCommand("com.pwk.parktube.ACTION_PREV");
      }
    });

    Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
    if (launch != null) {
      PendingIntent pi = PendingIntent.getActivity(
        this, 0, launch,
        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
      );
      mediaSession.setSessionActivity(pi);
    }

    MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
      .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "ParkTube PWK")
      .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "오디오 전용")
      .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Android Auto")
      .build();
    mediaSession.setMetadata(metadata);

    setSessionToken(mediaSession.getSessionToken());
    mediaSession.setActive(true);
    updateState(PlaybackStateCompat.STATE_PAUSED, 0L);

    IntentFilter syncFilter = new IntentFilter("com.pwk.parktube.ACTION_SYNC");
    if (android.os.Build.VERSION.SDK_INT >= 33) {
      registerReceiver(syncReceiver, syncFilter, Context.RECEIVER_NOT_EXPORTED);
    } else {
      registerReceiver(syncReceiver, syncFilter);
    }
    syncReceiverRegistered = true;
  }

  private void sendCommand(String action) {
    Intent i = new Intent(action);
    i.setPackage(getPackageName());
    sendBroadcast(i);
  }

  private void updateState(int state, long position) {
    long actions =
      PlaybackStateCompat.ACTION_PLAY |
      PlaybackStateCompat.ACTION_PAUSE |
      PlaybackStateCompat.ACTION_PLAY_PAUSE |
      PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
      PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
      PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;

    PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
      .setActions(actions)
      .setState(state, position,
        state == PlaybackStateCompat.STATE_PLAYING ? 1f : 0f)
      .build();
    mediaSession.setPlaybackState(playbackState);
  }

  @Override public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
    return new BrowserRoot(ROOT_ID, null);
  }

  @Override public void onLoadChildren(String parentId, Result<List<MediaBrowserCompat.MediaItem>> result) {
    List<MediaBrowserCompat.MediaItem> items = new ArrayList<>();
    if (ROOT_ID.equals(parentId)) {
      MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
        .setMediaId(MEDIA_ID)
        .setTitle("ParkTube PWK")
        .setSubtitle("휴대폰에서 영상을 먼저 재생한 뒤 여기서 제어")
        .build();
      items.add(new MediaBrowserCompat.MediaItem(
        description,
        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
      ));
    }
    result.sendResult(items);
  }

  @Override public void onDestroy() {
    if (syncReceiverRegistered) {
      try { unregisterReceiver(syncReceiver); } catch (Exception ignored) {}
      syncReceiverRegistered = false;
    }
    if (mediaSession != null) {
      mediaSession.setActive(false);
      mediaSession.release();
      mediaSession = null;
    }
    super.onDestroy();
  }
}
