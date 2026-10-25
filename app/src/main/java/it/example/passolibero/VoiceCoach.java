package it.example.passolibero;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;
import java.util.Set;

/** Sintesi vocale locale tramite motore di sistema, con ducking musicale. */
public final class VoiceCoach {
    private final AudioManager audio;
    private final AudioAttributes attributes;
    private final AudioFocusRequest focusRequest;
    private TextToSpeech tts;
    private volatile boolean ready;
    private int utteranceNumber;

    public VoiceCoach(Context context) {
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes).setOnAudioFocusChangeListener(change -> { }).build();
        tts = new TextToSpeech(context.getApplicationContext(), status -> {
            if (status != TextToSpeech.SUCCESS || tts == null) return;
            // Scegliamo soltanto una voce italiana NON dipendente dalla rete.
            int languageStatus = tts.setLanguage(Locale.ITALIAN);
            if (languageStatus == TextToSpeech.LANG_MISSING_DATA ||
                    languageStatus == TextToSpeech.LANG_NOT_SUPPORTED) return;
            Set<android.speech.tts.Voice> voices = tts.getVoices();
            if (voices != null) {
                for (android.speech.tts.Voice voice : voices) {
                    if ("it".equals(voice.getLocale().getLanguage()) &&
                            !voice.isNetworkConnectionRequired()) {
                        if (tts.setVoice(voice) == TextToSpeech.SUCCESS) { ready = true; break; }
                    }
                }
            }
        });
        tts.setAudioAttributes(attributes);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { releaseAudio(); }
            @Override public void onError(String id) { releaseAudio(); }
        });
    }

    public void speak(String phrase) {
        if (!ready || tts == null) return;
        int result = audio.requestAudioFocus(focusRequest);
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return;
        Bundle params = new Bundle();
        int code = tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, "checkpoint-" + (++utteranceNumber));
        if (code != TextToSpeech.SUCCESS) releaseAudio();
    }

    private void releaseAudio() { audio.abandonAudioFocusRequest(focusRequest); }
    public boolean isReady() { return ready; }
    public void close() {
        ready = false;
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        releaseAudio();
    }
}
