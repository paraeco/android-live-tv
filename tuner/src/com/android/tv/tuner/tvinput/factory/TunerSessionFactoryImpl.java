package com.android.tv.tuner.tvinput.factory;

import android.content.Context;
import android.media.tv.TvInputService.Session;
import com.android.tv.tuner.tvinput.TunerSession;
import com.android.tv.tuner.tvinput.TunerSessionExoV2;
import com.android.tv.tuner.tvinput.datamanager.ChannelDataManager;
import com.android.tv.common.flags.Exoplayer2Flags;

/** Creates a {@link TunerSessionFactory}. */
public class TunerSessionFactoryImpl implements TunerSessionFactory {
    private final Exoplayer2Flags exoplayer2Flags;

    public TunerSessionFactoryImpl(Exoplayer2Flags exoplayer2Flags) {
        this.exoplayer2Flags = exoplayer2Flags;
    }

    @Override
    public Session create(
            Context context,
            ChannelDataManager channelDataManager,
            SessionReleasedCallback releasedCallback) {
        return exoplayer2Flags.enabled()
                ? new TunerSessionExoV2(context, channelDataManager, releasedCallback)
                : new TunerSession(context, channelDataManager, releasedCallback);
    }
}