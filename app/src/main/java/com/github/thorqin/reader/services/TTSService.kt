package com.github.thorqin.reader.services

import android.app.*
import com.github.thorqin.reader.R
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.github.thorqin.reader.App
import java.util.*
import android.os.*
import android.content.Context
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.PlaybackStateCompat
import com.github.thorqin.reader.activities.book.BookActivity
import com.github.thorqin.reader.activities.main.MainActivity


class TTSService : Service() {

	companion object {
		const val MEDIA_SESSION_ACTIONS = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE
		const val NOTIFICATIONS_ID = 1
		const val CHANNEL_ID = "TTS-SERVICE-CHANNEL"
		const val CHANNEL_NAME = "EReader TTS"
	}

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

	private lateinit var mediaSession: MediaSessionCompat


//	private var mediaButtonReceiver = object: BroadcastReceiver() {
//		override fun onReceive(context: Context, intent: Intent) {
//			// KeyEvent.KEYCODE_MEDIA_NEXT、KeyEvent.KEYCODE_MEDIA_PREVIOUS、KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
//			if (intent.action != Intent.ACTION_MEDIA_BUTTON)
//				return
//			val event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as KeyEvent? ?: return
//			if (event.action != KeyEvent.ACTION_UP)
//				return
//
//			val keyCode = event.keyCode
//			// val eventTime = event.eventTime - event.downTime //按键按下到松开的时长
//			if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
//				abortBroadcast()
//				handler.post {
//					if (playing) {
//						stop()
//						stateListener?.onStop()
//					} else {
//						play()
//						stateListener?.onStart()
//					}
//				}
//			}
//		}
//	}


	private fun createMediaSession() {
		mediaSession = MediaSessionCompat(this, "EReader")
		mediaSession.setCallback(object: MediaSessionCompat.Callback() {
			override fun onPlay() {
				super.onPlay()
				println("play")
			}
			override fun onStop() {
				super.onPlay()
				println("stop")
			}
		})
		mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
		mediaSession.isActive = true
	}

	override fun onCreate() {
		super.onCreate()
		handler = Handler(Looper.getMainLooper())

		createMediaSession()

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
				// println("start: $p0")
			}

		})


	}

	override fun onDestroy() {
		try {
			tts.shutdown()
			mediaSession.release()
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
				stop(true)
				stateListener?.onStop()
				break
			}
		}

	}

	fun setFileInfo(fileInfo: App.FileDetail) {
		playInfo = fileInfo
	}


	fun play(setup: Boolean = false): Boolean {
		if (ttsAvailable) {
			if (playing) {
				stop(setup)
			}
			val info = playInfo ?: return false
			playing = true
			readingPos = info.ttsPoint
			ttsPlay()

			if (setup) {
				mediaSession.setPlaybackState(PlaybackStateCompat.Builder()
					.setActions(MEDIA_SESSION_ACTIONS)
					.setState(PlaybackStateCompat.STATE_PLAYING, 0L, 1f).build())

				mediaSession.setMetadata(MediaMetadataCompat.Builder()
					.putText(MediaMetadataCompat.METADATA_KEY_TITLE, "Ereader TTS")
					.build())

				val notificationIntent = Intent(this, BookActivity::class.java)
				val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
				val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
				val builder = Notification.Builder(this)
					.setContentTitle(this.getString(R.string.app_name))
					.setContentText("正在使用语音播放图书...")
					.setSmallIcon(R.drawable.ic_book)
					.setContentIntent(pendingIntent)
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
					val notificationChannel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_MIN)
					notificationChannel.enableLights(false)
					notificationChannel.setShowBadge(false)
					notificationChannel.lockscreenVisibility = Notification.VISIBILITY_SECRET
					notificationManager.createNotificationChannel(notificationChannel)
					builder.setChannelId(CHANNEL_ID)
				}
				val notification = builder.build()
				notificationManager.notify(NOTIFICATIONS_ID, notification)
				startForeground(NOTIFICATIONS_ID, notification)
			}
		}

		return playing
	}

	fun stop(setup: Boolean = false) {
		if (ttsAvailable && playing) {
			tts.stop()
			playing = false
			if (setup) {
				mediaSession.setPlaybackState(
					PlaybackStateCompat.Builder()
						.setActions(MEDIA_SESSION_ACTIONS)
						.setState(PlaybackStateCompat.STATE_PAUSED, 0L, 1f).build()
				)
				stopForeground(false)
				val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
				notificationManager.cancel(NOTIFICATIONS_ID)
			}
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
