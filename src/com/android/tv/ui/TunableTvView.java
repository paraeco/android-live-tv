package com.android.tv.ui;

import android.content.Context;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager.TvInputListener;
import android.media.tv.TvView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;

import com.android.internal.util.Preconditions;
import com.android.tv.data.Channel;
import com.android.tv.data.StreamInfo;
import com.android.tv.ui.TunableTvView.OnTuneListener;
import com.android.tv.util.TvInputManagerHelper;
import com.android.tv.util.Utils;

public class TunableTvView extends TvView implements StreamInfo {
    private static final boolean DEBUG = true;
    private static final String TAG = "TunableTvView";

    private static final int DELAY_FOR_SURFACE_RELEASE = 300;

    private float mVolume;
    private long mChannelId = Channel.INVALID_ID;
    private TvInputManagerHelper mInputManagerHelper;
    private boolean mStarted;
    private TvInputInfo mInputInfo;
    private OnTuneListener mOnTuneListener;
    private int mVideoFormat = StreamInfo.VIDEO_DEFINITION_LEVEL_UNKNOWN;
    private int mAudioChannelCount = StreamInfo.AUDIO_CHANNEL_COUNT_UNKNOWN;
    private boolean mHasClosedCaption = false;

    private final SurfaceHolder.Callback mSurfaceHolderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) { }

        @Override
        public void surfaceCreated(SurfaceHolder holder) { }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            // TODO: It is a hack to wait to release a surface at TIS. If there is a way to
            // know when the surface is released at TIS, we don't need this hack.
            try {
                if (DEBUG) Log.d(TAG, "Sleep to wait destroying a surface");
                Thread.sleep(DELAY_FOR_SURFACE_RELEASE);
                if (DEBUG) Log.d(TAG, "Wake up from sleeping");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };

    private final TvInputListener mListener =
            new TvInputListener() {
                @Override
                public void onError(String inputId, int errorCode) {
                    if (errorCode == TvView.ERROR_BUSY) {
                        Log.w(TAG, "Failed to bind an input");
                        long channelId = mChannelId;
                        mChannelId = Channel.INVALID_ID;
                        mInputInfo = null;
                        if (mOnTuneListener != null) {
                            mOnTuneListener.onTuned(false, channelId);
                            mOnTuneListener = null;
                        }
                    } else if (errorCode == TvView.ERROR_TV_INPUT_DISCONNECTED) {
                        Log.w(TAG, "Session is released by crash");
                        long channelId = mChannelId;
                        mChannelId = Channel.INVALID_ID;
                        mInputInfo = null;
                        if (mOnTuneListener != null) {
                            mOnTuneListener.onUnexpectedStop(channelId);
                            mOnTuneListener = null;
                        }
                    }
                }

                @Override
                public void onVideoStreamChanged(String inputId, int width, int height,
                        boolean interlaced) {
                    mVideoFormat = Utils.getVideoDefinitionLevelFromSize(width, height);
                    if (mOnTuneListener != null) {
                        mOnTuneListener.onStreamInfoChanged(TunableTvView.this);
                    }
                }

                @Override
                public void onAudioStreamChanged(String inputId, int channelCount) {
                    mAudioChannelCount = channelCount;
                    if (mOnTuneListener != null) {
                        mOnTuneListener.onStreamInfoChanged(TunableTvView.this);
                    }
                }

                @Override
                public void onClosedCaptionStreamChanged(String inputId, boolean hasClosedCaption) {
                    mHasClosedCaption = hasClosedCaption;
                    if (mOnTuneListener != null) {
                        mOnTuneListener.onStreamInfoChanged(TunableTvView.this);
                    }
                }
            };

    public TunableTvView(Context context) {
        this(context, null, 0);
    }

    public TunableTvView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TunableTvView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        getHolder().addCallback(mSurfaceHolderCallback);
    }

    public void start(TvInputManagerHelper tvInputManagerHelper) {
        mInputManagerHelper = tvInputManagerHelper;
        if (mStarted) {
            return;
        }
        mStarted = true;
    }

    public void stop() {
        if (!mStarted) {
            return;
        }
        mStarted = false;
        reset();
        mChannelId = Channel.INVALID_ID;
        mInputInfo = null;
        mOnTuneListener = null;
    }

    public boolean isPlaying() {
        return mStarted;
    }

    public boolean tuneTo(long channelId, OnTuneListener listener) {
        if (!mStarted) {
            throw new IllegalStateException("TvView isn't started");
        }
        if (DEBUG) Log.d(TAG, "tuneTo " + channelId);
        mVideoFormat = StreamInfo.VIDEO_DEFINITION_LEVEL_UNKNOWN;
        mAudioChannelCount = StreamInfo.AUDIO_CHANNEL_COUNT_UNKNOWN;
        mHasClosedCaption = false;
        String inputId = Utils.getInputIdForChannel(getContext(), channelId);
        TvInputInfo inputInfo = mInputManagerHelper.getTvInputInfo(inputId);
        if (inputInfo == null || !mInputManagerHelper.isAvailable(inputInfo)) {
            return false;
        }
        mOnTuneListener = listener;
        mChannelId = channelId;
        if (!inputInfo.equals(mInputInfo)) {
            reset();
            // TODO: It is a hack to wait to release a surface at TIS. If there is a way to
            // know when the surface is released at TIS, we don't need this hack.
            try {
                Thread.sleep(DELAY_FOR_SURFACE_RELEASE);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            mInputInfo = inputInfo;
        }
        setTvInputListener(mListener);
        tune(mInputInfo.getId(), Utils.getChannelUri(mChannelId));
        if (mOnTuneListener != null) {
            // TODO: Add a callback for tune complete and call onTuned when it was successful.
            mOnTuneListener.onTuned(true, mChannelId);
        }
        return true;
    }

    public TvInputInfo getCurrentTvInputInfo() {
        return mInputInfo;
    }

    public long getCurrentChannelId() {
        return mChannelId;
    }

    @Override
    public void setStreamVolume(float volume) {
        if (!mStarted) {
            throw new IllegalStateException("TvView isn't started");
        }
        if (DEBUG)
            Log.d(TAG, "setStreamVolume " + volume);
        mVolume = volume;
        super.setStreamVolume(volume);
    }

    public interface OnTuneListener {
        void onTuned(boolean success, long channelId);
        void onUnexpectedStop(long channelId);
        void onStreamInfoChanged(StreamInfo info);
    }

    @Override
    public int getVideoDefinitionLevel() {
        return mVideoFormat;
    }

    @Override
    public int getAudioChannelCount() {
        return mAudioChannelCount;
    }

    @Override
    public boolean hasClosedCaption() {
        return mHasClosedCaption;
    }
}
