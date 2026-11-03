package app.bada

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Avvisi vocali con il sintetizzatore del telefono (funziona offline se la voce italiana è installata).
 * La musica si abbassa mentre parla e poi torna al volume di prima.
 */
class Voice(ctx: Context) : TextToSpeech.OnInitListener {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()
    private val tts = TextToSpeech(ctx.applicationContext, this)
    private var ready = false
    private var n = 0

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        tts.language = Locale.ITALIAN
        tts.setAudioAttributes(attrs)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { if (!tts.isSpeaking) audio.abandonAudioFocusRequest(focus) }
            @Deprecated("API 21") override fun onError(id: String?) { audio.abandonAudioFocusRequest(focus) }
        })
        ready = true
    }

    fun say(text: String) {
        if (!ready) return
        audio.requestAudioFocus(focus)
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "offtrack-${n++}")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
        audio.abandonAudioFocusRequest(focus)
    }
}
