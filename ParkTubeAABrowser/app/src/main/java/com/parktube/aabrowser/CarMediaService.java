package com.parktube.aabrowser;

import android.app.PendingIntent;
import android.content.Intent;
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

  @Override public void onCreate() {
    super.onCreate();

    mediaSession = new MediaSessionCompat(this, "ParkTubePWKCar");
    mediaSession.setCallback(new MediaSessionCompat.Callback() {
      @Override public void onPlay() {
        sendCommand("com.pwk.parktube.ACTION_PLAY");
        updateState(PlaybackStateCompat.STATE_PLAYING);
      }

      @Override public void onPause() {
        sendCommand("com.pwk.parktube.ACTION_PAUSE");
        updateState(PlaybackStateCompat.STATE_PAUSED);
      }

      @Override public void onPlayFromMediaId(String mediaId, Bundle extras) {
        if (MEDIA_ID.equals(mediaId)) {
          sendCommand("com.pwk.parktube.ACTION_PLAY");
          updateState(PlaybackStateCompat.STATE_PLAYING);
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
    updateState(PlaybackStateCompat.STATE_PAUSED);
  }

  private void sendCommand(String action) {
    Intent i = new Intent(action);
    i.setPackage(getPackageName());
    sendBroadcast(i);
  }

  private void updateState(int state) {
    long actions =
      PlaybackStateCompat.ACTION_PLAY |
      PlaybackStateCompat.ACTION_PAUSE |
      PlaybackStateCompat.ACTION_PLAY_PAUSE |
      PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID |
      PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
      PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;

    PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
      .setActions(actions)
      .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN,
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
        .setSubtitle("휴대폰에서 선택한 콘텐츠를 오디오로 제어")
        .build();
      items.add(new MediaBrowserCompat.MediaItem(
        description,
        MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
      ));
    }
    result.sendResult(items);
  }

  @Override public void onDestroy() {
    if (mediaSession != null) {
      mediaSession.setActive(false);
      mediaSession.release();
      mediaSession = null;
    }
    super.onDestroy();
  }
}
