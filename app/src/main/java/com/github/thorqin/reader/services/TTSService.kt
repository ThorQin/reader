package com.github.thorqin.reader.services

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.github.thorqin.reader.App
import java.util.*
import android.content.IntentFilter
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.R.attr.name
import android.os.*
import android.view.KeyEvent
import android.os.Bundle
import androidx.core.content.ContextCompat.getSystemService
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.R.attr.name
import androidx.core.content.ContextCompat.getSystemService
import android.media.AudioManager
import androidx.media.session.MediaButtonReceiver
import android.content.ComponentName
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import android.R.attr.name






class TTSService : Service() {

	interface StateListener {
		fun onStop()
		fun onStart()
		fun onUpdate()
	}

	class TTSBinder(val service: TTSService): Binder()
	private val ttsBinder = TTSBinder(this)

	private lateinit var tts: TextToSpeech
	private var playing = false

	private var ttsAvailable = false
	private var playInfo: App.FileDetail? = null

	private var stateListener: StateListener? = null
	private var readingPos = 0L
	private var nextPos =0L

	private lateinit var handler: Handler

	private var mediaButtonReceiver = object: BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			// KeyEvent.KEYCODE_MEDIA_NEXT、KeyEvent.KEYCODE_MEDIA_PREVIOUS、KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
			if (intent.action != Intent.ACTION_MEDIA_BUTTON)
				return
			val event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as KeyEvent? ?: return
			if (event.action != KeyEvent.ACTION_UP)
				return

			val keyCode = event.keyCode
			// val eventTime = event.eventTime - event.downTime //按键按下到松开的时长
			if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
				abortBroadcast()
				handler.post {
					if (playing) {
						stop()
						stateListener?.onStop()
					} else {
						play()
						stateListener?.onStart()
					}
				}
			}
		}
	}

	override fun onCreate() {
		super.onCreate()
		handler = Handler(Looper.getMainLooper())


//		mediaButtonReceiver = ComponentName(packageName, MediaButtonReceiver::class.java.name)
//
//		mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
//
//		mAudioManager.registerMediaButtonEventReceiver(mediaButtonReceiver)

		val mediaFilter = IntentFilter()
		mediaFilter.addAction(Intent.ACTION_MEDIA_BUTTON)
		mediaFilter.priority = 1000
		registerReceiver(mediaButtonReceiver, mediaFilter)

		tts = TextToSpeech(this) {status ->
			if (status == TextToSpeech.SUCCESS) {
				val result = tts.setLanguage(Locale.CHINA)
				if (result == TextToSpeech.LANG_COUNTRY_AVAILABLE || result == TextToSpeech.LANG_AVAILABLE){
					ttsAvailable = true
				}
			}
		}

		tts.setOnUtteranceProgressListener(object: UtteranceProgressListener() {
			override fun onDone(p0: String?) {
//				println("done: $p0")
				readingPos = nextPos
				if (playInfo != null && p0 == "e-reader-sentence") {
					playInfo!!.setTtsPosition(readingPos)
					(application as App).saveFileState(playInfo!!, playInfo!!.key)
					stateListener?.onUpdate()
					ttsPlay()
				}
			}

			override fun onError(p0: String?) {
				System.err.println("error: $p0")
			}

			override fun onStart(p0: String?) {
				println("start: $p0")
			}

		})
	}

	override fun onDestroy() {
		try {
			unregisterReceiver(mediaButtonReceiver)
			tts.shutdown()
		} catch (e: Throwable) {

		}
		super.onDestroy()
	}

	override fun onBind(intent: Intent): IBinder {
		return ttsBinder
	}



	private fun ttsPlay() {
		while (playInfo != null && playing) {
			val info = playInfo as App.FileDetail
			val sentence = info.getTtsSentence(readingPos)
			if (sentence.sentence != null) {
//				println(sentence.sentence)
				val str = sentence.sentence!!.replace(Regex("[\\s\\n\"“”]+"), " ").trim()
				if (str.isNotEmpty()) {
					nextPos = sentence.nextPos
					tts.speak(str, TextToSpeech.QUEUE_FLUSH, null, "e-reader-sentence")
					break
				} else {
					readingPos = sentence.nextPos
				}
			} else {
				stop()
				stateListener?.onStop()
				break
			}
		}

	}

	fun setFileInfo(fileInfo: App.FileDetail) {
		playInfo = fileInfo
	}


	fun play(): Boolean {
		if (ttsAvailable) {
			if (playing) {
				stop()
			}
			val info = playInfo ?: return false
			playing = true
			readingPos = info.ttsPoint
			ttsPlay()
		}
		return playing
	}

	fun stop() {
		if (ttsAvailable && playing) {
			tts.stop()
			playing = false
		}
	}

	fun setListener(listener: StateListener) {
		stateListener = listener
	}

	val isAvailable: Boolean
		get () {
			return ttsAvailable
		}

	val isPlaying: Boolean
		get() {
			return playing
		}

}
