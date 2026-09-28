package com.parktube.aabrowser;

import android.app.PendingIntent;
import android.content.Intent;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.service.media.MediaBrowserService;

import java.util.ArrayList;
import java.util.List;

public class CarMediaService extends MediaBrowserService {
  private static final String ROOT_ID = "pwk_root";
  private static final String MEDIA_ID = "pwk_audio";
  private MediaSession mediaSession;

  @Override public void onCreate() {
    super.onCreate();

    mediaSession = new MediaSession(this, "ParkTubePWKCar");
    mediaSession.setCallback(new MediaSession.Callback() {
      @Override public void onPlay() {
        sendCommand("com.pwk.parktube.ACTION_PLAY");
        updateState(PlaybackState.STATE_PLAYING);
      }

      @Override public void onPause() {
        sendCommand("com.pwk.parktube.ACTION_PAUSE");
        updateState(PlaybackState.STATE_PAUSED);
      }

      @Override public void onPlayFromMediaId(String mediaId, Bundle extras) {
        if (MEDIA_ID.equals(mediaId)) {
          sendCommand("com.pwk.parktube.ACTION_PLAY");
          updateState(PlaybackState.STATE_PLAYING);
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

    MediaMetadata metadata = new MediaMetadata.Builder()
      .putString(MediaMetadata.METADATA_KEY_TITLE, "ParkTube PWK")
      .putString(MediaMetadata.METADATA_KEY_ARTIST, "오디오 전용")
      .putString(MediaMetadata.METADATA_KEY_ALBUM, "Android Auto")
      .build();
    mediaSession.setMetadata(metadata);

    setSessionToken(mediaSession.getSessionToken());
    mediaSession.setActive(true);
    updateState(PlaybackState.STATE_PAUSED);
  }

  private void sendCommand(String action) {
    Intent i = new Intent(action);
    i.setPackage(getPackageName());
    sendBroadcast(i);
  }

  private void updateState(int state) {
    long actions =
      PlaybackState.ACTION_PLAY |
      PlaybackState.ACTION_PAUSE |
      PlaybackState.ACTION_PLAY_PAUSE |
      PlaybackState.ACTION_PLAY_FROM_MEDIA_ID |
      PlaybackState.ACTION_SKIP_TO_NEXT |
      PlaybackState.ACTION_SKIP_TO_PREVIOUS;

    PlaybackState playbackState = new PlaybackState.Builder()
      .setActions(actions)
      .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, state == PlaybackState.STATE_PLAYING ? 1f : 0f)
      .build();
    mediaSession.setPlaybackState(playbackState);
  }

  @Override public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
    return new BrowserRoot(ROOT_ID, null);
  }

  @Override public void onLoadChildren(String parentId, Result<List<MediaBrowser.MediaItem>> result) {
    List<MediaBrowser.MediaItem> items = new ArrayList<>();
    if (ROOT_ID.equals(parentId)) {
      MediaDescription description = new MediaDescription.Builder()
        .setMediaId(MEDIA_ID)
        .setTitle("ParkTube PWK")
        .setSubtitle("휴대폰에서 선택한 콘텐츠를 오디오로 제어")
        .build();
      items.add(new MediaBrowser.MediaItem(description, MediaBrowser.MediaItem.FLAG_PLAYABLE));
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
