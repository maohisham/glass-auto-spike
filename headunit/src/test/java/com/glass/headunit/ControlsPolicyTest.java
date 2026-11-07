package com.glass.headunit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.support.v4.media.session.PlaybackStateCompat;

import org.junit.Test;

public class ControlsPolicyTest {

    private static final long WITH_SHUFFLE = PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE;
    private static final long WITHOUT_SHUFFLE = PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE;

    @Test
    public void onlyVRadioCountsAsRadio() {
        assertTrue(ControlsPolicy.isRadio(Proxy.VRADIO));
        assertFalse(ControlsPolicy.isRadio("com.spotify.music"));
        assertFalse(ControlsPolicy.isRadio(null));
    }

    @Test
    public void shuffleShowsForAMusicPlayerThatOffersIt() {
        assertTrue(ControlsPolicy.showShuffle("com.spotify.music", WITH_SHUFFLE));
    }

    @Test
    public void shuffleIsHiddenWhenThePlayerDoesNotOfferIt() {
        assertFalse(ControlsPolicy.showShuffle("com.spotify.music", WITHOUT_SHUFFLE));
    }

    @Test
    public void shuffleIsNeverShownForVRadioEvenIfItClaimsToOfferIt() {
        assertFalse(ControlsPolicy.showShuffle(Proxy.VRADIO, WITH_SHUFFLE));
    }

    @Test
    public void shuffleIsHiddenWithNoPlayer() {
        assertFalse(ControlsPolicy.showShuffle(null, WITH_SHUFFLE));
    }

    @Test
    public void progressShowsOnlyWhenThereIsATrackLength() {
        assertTrue(ControlsPolicy.showProgress("com.spotify.music", 216_000));
        assertFalse(ControlsPolicy.showProgress("com.spotify.music", 0));
        assertFalse(ControlsPolicy.showProgress("com.spotify.music", -1));
    }

    @Test
    public void progressIsHiddenForVRadioEvenWithALength() {
        assertFalse(ControlsPolicy.showProgress(Proxy.VRADIO, 216_000));
        assertFalse(ControlsPolicy.showProgress(null, 216_000));
    }

    private static final long WITH_REPEAT = PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_SET_REPEAT_MODE;

    @Test
    public void repeatShowsForAMusicPlayerThatOffersIt() {
        assertTrue(ControlsPolicy.showRepeat("com.spotify.music", WITH_REPEAT));
    }

    @Test
    public void repeatIsHiddenWhenThePlayerDoesNotOfferIt() {
        assertFalse(ControlsPolicy.showRepeat("com.spotify.music", WITHOUT_SHUFFLE));
    }

    @Test
    public void repeatIsNeverShownForVRadioOrWithNoPlayer() {
        assertFalse(ControlsPolicy.showRepeat(Proxy.VRADIO, WITH_REPEAT));
        assertFalse(ControlsPolicy.showRepeat(null, WITH_REPEAT));
    }
}
