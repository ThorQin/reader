package com.github.thorqin.reader.activities.book

import android.animation.Animator
import android.os.Bundle
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.github.thorqin.reader.R
import kotlinx.android.synthetic.main.activity_book.*
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.View.*
import com.github.thorqin.reader.App
import java.io.File
import java.lang.Exception
import java.nio.charset.Charset
import java.util.*
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.floor






class BookActivity : AppCompatActivity() {

	enum class Direction(var value: Int) {
		LEFT(0),
		RIGHT(1)
	}

	private lateinit var summary: App.FileSummary
	private lateinit var fileInfo: App.FileDetail
	private var boxWidth: Float = 0F
	private var atBegin = false
	private var atEnd = false
	private var showActionBar = false

	private lateinit var handler: Handler

	private val app: App
		get () {
			return application as App
		}

	fun getActionBarView(): View {
		val window = this.window
		val v = window.decorView
		val resId = resources.getIdentifier("action_bar_container", "id", "android")
		return v.findViewById(resId)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		handler = Handler(Looper.getMainLooper())

		val key = intent.getStringExtra("key")
		if (!app.config.files.containsKey(key)) {
			app.toast(getString(R.string.invalid_config))
			finish()
			return
		}

		setContentView(R.layout.activity_book)

		summary = app.config.files[key] as App.FileSummary
		toolbar.title = summary.name
		setSupportActionBar(toolbar)
		supportActionBar?.title = summary.name
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		// supportActionBar?.hide()
		appBar.visibility = INVISIBLE
		footBar.visibility = INVISIBLE
		handler.postDelayed({
			appBar.translationY = -appBar.height.toFloat()
			appBar.visibility = GONE
			footBar.translationY = footBar.height.toFloat()
			footBar.visibility = GONE
		}, 50)


		bufferView.addOnLayoutChangeListener {
			v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
			boxWidth = flipper.measuredWidth.toFloat()
			println("boxWidth: $boxWidth")
			val newW = right - left
			val newH = bottom - top
			if (::fileInfo.isInitialized) {
				if (fileInfo.screenWidth != newW && fileInfo.screenHeight != newH
					&& newW != 0 && newH != 0) {
					initBook(newW, newH)
				}
			} else {
				if (newW != 0 && newH != 0) {
					initBook(newW, newH)
				}
			}
			setPos()
		}

		var v: View? = null
		var startX : Float? = null
		var viewX: Float? = null
		var moveDirection: Direction = Direction.LEFT
		flipper.setOnTouchListener { _, event ->
			when (event.action ) {
				MotionEvent.ACTION_DOWN -> {
					startX = event.rawX
					v == null
				}
				MotionEvent.ACTION_MOVE -> {
					if (startX != null) {
						if (v == null) {
							if (event.rawX > startX!! && atBegin) {
								true
							} else if (event.rawX < startX!! && atEnd) {
								true
							} else {
								if (event.rawX > startX!! + 10f) {
									moveDirection = Direction.RIGHT
									v = flipper.getChildAt(2)
								} else if (event.rawX < startX!! - 10f) {
									moveDirection = Direction.LEFT
									v = flipper.getChildAt(1)
								}

								viewX = v?.translationX
							}
						}
						if (v != null) {
							v?.elevation = 20f
							when (moveDirection) {
								Direction.RIGHT -> {
									if (event.rawX > startX!!) {
										v!!.translationX = viewX!! + event.rawX - startX!!
									}
								}
								else -> {
									if (event.rawX < startX!!) {
										v!!.translationX = viewX!! + event.rawX - startX!!
									}
								}
							}
						}
						true
					} else {
						true
					}
				}
				MotionEvent.ACTION_UP -> {
					val diff = boxWidth / 8
					if (v != null) {
						when (moveDirection) {
							Direction.RIGHT -> {
								if (event.rawX > startX!! + diff) {
									val toPos = 0f
									moveViewTo(v!!, toPos) {
										var bottomView = flipper.getChildAt(0)
										flipper.removeViewAt(0)
										flipper.addView(bottomView)
										setPos()
										v?.elevation = 0f
										v = null
										fileInfo.previous()
										summary.readPoint = fileInfo.readPoint
										showContent(false)
										handler.postDelayed( {
											app.saveFileState(fileInfo, fileInfo.key)
											app.saveConfig()
										}, 50)
									}
								} else {
									val toPos = -boxWidth
									moveViewTo(v!!, toPos) {
										v?.elevation = 0f
										v = null
										// showContent(false)
									}
								}
							}
							else -> {
								if (event.rawX < startX!! - diff) {
									moveViewTo(v!!, -boxWidth) {
										var bottomView = flipper.getChildAt(2)
										flipper.removeViewAt(2)
										flipper.addView(bottomView, 0)
										setPos()
										v?.elevation = 0f
										v = null
										fileInfo.next()
										summary.readPoint = fileInfo.readPoint
										showContent(false)
										handler.postDelayed({
											app.saveFileState(fileInfo, fileInfo.key)
											app.saveConfig()
										}, 50)
									}
								} else {
									moveViewTo(v!!, 0f) {
										v?.elevation = 0f
										v = null
										// showContent(false)
									}
								}
							}
						}
					} else {
						if (startX != null && abs(event.rawX - startX!!) < 10 ) {
							// IS CLICK
							if (startX!! > boxWidth / 3 && startX!! < boxWidth / 3 * 2) {
								toggleActionBar()
							}
						}
					}
					startX = null
					viewX = null
					true
				}
				else -> false
			}
		}

		openBook()
	}

	private fun toggleActionBar() {
		val anim = ValueAnimator()
		if (!showActionBar) {
			appBar.visibility = VISIBLE
			footBar.visibility = VISIBLE
			anim.setFloatValues(1f, 0f)
		} else {
			anim.setFloatValues(0f, 1f)
		}
		anim.duration = 200
		anim.addUpdateListener( object: ValueAnimator.AnimatorUpdateListener {
			override fun onAnimationUpdate(p0: ValueAnimator?) {
				val value = anim.animatedValue as Float
				footBar.translationY = footBar.height * value
				appBar.translationY = -appBar.height * value
			}
		})
		anim.addListener(object: Animator.AnimatorListener {
			override fun onAnimationRepeat(p0: Animator?) {}
			override fun onAnimationEnd(p0: Animator?) {
				if (!showActionBar) {
					appBar.translationY = 0f
					footBar.translationY = 0f
					showActionBar = true
				} else {
					appBar.visibility = GONE
					footBar.translationY = -appBar.height.toFloat()
					footBar.visibility = GONE
					footBar.translationY = footBar.height.toFloat()
					showActionBar = false
				}
			}

			override fun onAnimationCancel(p0: Animator?) {}

			override fun onAnimationStart(p0: Animator?) {}
		})
		anim.start()
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		return when (item.itemId) {
			android.R.id.home -> {
				finish()
				true
			}
			else -> super.onOptionsItemSelected(item)
		}
	}

	private fun moveViewTo(v: View, pos: Float, onEndCallback: () -> Unit) {
		val transAnim = ObjectAnimator.ofFloat(v, "translationX", pos)
		transAnim.duration = 100
		transAnim.addListener( object: Animator.AnimatorListener {
			override fun onAnimationRepeat(animation: Animator?) {}
			override fun onAnimationEnd(animation: Animator?) {
				onEndCallback()
			}
			override fun onAnimationCancel(animation: Animator?) {}
			override fun onAnimationStart(animation: Animator?) {}
		})

		transAnim.start()
	}

	private fun setPos() {
		flipper.getChildAt(2).translationX = -boxWidth
		flipper.getChildAt(1).translationX = 0f
		flipper.getChildAt(0).translationX = 0f
	}


	private class PageInfo(var text: String, var chapterName: String, var readPage: Int, var chapterPage: Int)

	private fun getPrevPageInfo(): PageInfo? {
		if (fileInfo.chapters.size == 0) {
			return null
		}
		var p: Int
		var c = fileInfo.readChapter
		p = if (fileInfo.readPageOfChapter > 0) {
			fileInfo.readPageOfChapter - 1
		} else {
			if (c > 0) {
				c--
				if (fileInfo.chapters[c].pages.size > 0) {
					fileInfo.chapters[c].pages.size - 1
				} else {
					return null
				}
			} else {
				return null
			}
		}
		return PageInfo(fileInfo.getContent(c, p),
			fileInfo.chapters[c].name, fileInfo.readPage + 1, p)
	}

	private fun getCurrentPageInfo(): PageInfo {
		return PageInfo(fileInfo.getContent(fileInfo.readChapter, fileInfo.readPageOfChapter),
			fileInfo.chapters[fileInfo.readChapter].name, fileInfo.readPage, fileInfo.readPageOfChapter)
	}

	private fun getNextPageInfo(): PageInfo? {
		if (fileInfo.chapters.size == 0) {
			return null
		}
		var p: Int
		var c = fileInfo.readChapter
		p = if (fileInfo.readPageOfChapter < fileInfo.chapters[c].pages.size - 1) {
			fileInfo.readPageOfChapter + 1
		} else {
			if (c < fileInfo.chapters.size - 1) {
				c++
				if (fileInfo.chapters[c].pages.size > 0) {
					0
				} else {
					return null
				}
			} else {
				return null
			}
		}
		return PageInfo(fileInfo.getContent(c, p),
			fileInfo.chapters[c].name, fileInfo.readPage - 1, p)
	}

	private fun showContent(updateAll: Boolean) {
		val prevView = flipper.getChildAt(2) as BookView
		val currentView = flipper.getChildAt(1) as BookView
		val nextView = flipper.getChildAt(0) as BookView

		if (updateAll) {
			val current = getCurrentPageInfo()
			// currentView.text = currentText
			currentView.setBookInfo(fileInfo.name,
				current.chapterName, current.text,
				"${fileInfo.readPage + 1} / ${fileInfo.totalPages}",
				"${floor(fileInfo.readPage / fileInfo.totalPages.toDouble() * 10000) / 100}%",
				current.chapterPage
				)
		}
		val prev = getPrevPageInfo()
		if (prev == null) {
			atBegin = true
		} else {
			atBegin = false
			//prevView.text = prevText
			prevView.setBookInfo(fileInfo.name,
				prev.chapterName, prev.text,
			"${prev.readPage + 1} / ${fileInfo.totalPages}",
			"${floor(prev.readPage / fileInfo.totalPages.toDouble() * 10000) / 100}%",
			prev.chapterPage)
		}

		val next = getNextPageInfo()
		if (next == null) {
			atEnd = true
		} else {
			atEnd = false
			// nextView.text = nextText
			nextView.setBookInfo(fileInfo.name,
				next.chapterName, next.text,
				"${next.readPage + 1} / ${fileInfo.totalPages}",
				"${floor(next.readPage / fileInfo.totalPages.toDouble() * 10000) / 100}%",
				next.chapterPage)
		}
	}

	private fun openBook() {
		try {
			val fontSize = app.config.fontSize.toFloat()
			for (i in 0 until 3) {
				(flipper.getChildAt(i) as BookView).textSize = fontSize
			}
			bufferView.textSize = fontSize
			summary.lastReadTime = Date().time
			fileInfo = app.getFileConfig(summary.key)
			showContent(true)
		} catch (e: Exception) {
			println("Open index file failed, need to initialize!")
		}
	}

	private fun parseFile(file: File, pattern: Pattern): App.FileDetail {

		lateinit var charset: String
		file.inputStream().use {
			// Firstly we should detect file encoding
			charset = App.detectCharset(it)
		}

		file.reader(Charset.forName(charset)).use {
			var buffer = CharArray(8192)
			var line = StringBuilder(8192)
			var content = StringBuilder(8192)
			var lineStart = 0L
			var lineSize = 0
			var scan = 0L
			var lineEnd = true
			var beginChapter = true
			var fileInfo: App.FileDetail = App.FileDetail()
			fileInfo.encoding = charset
			fileInfo.fontSize = app.config.fontSize

			var chapter = App.Chapter()

			fun testEnd(c: Char?) {
				if (!beginChapter) {
					if (lineSize in 1..50) {
						// match line content
						if (pattern.matcher(line).find()) {
							chapter.endPoint = lineStart
							val str = content.subSequence(0, content.length - lineSize).trimEnd()
							parseChapter(fileInfo, chapter, str, str.length)
							chapter = App.Chapter()
							chapter.name = line.trim().toString()
							beginChapter = true
							content.clear()
							line.clear()
						} else if (c != null) {
							content.append(c)
							lineSize = 0
							line.clear()
						} else {
							chapter.endPoint = scan
							val str = content.trimEnd()
							parseChapter(fileInfo, chapter, str, str.length)
						}
					} else if (c != null) {
						content.append(c)
						lineSize = 0
						line.clear()
					} else {
						chapter.endPoint = scan
						val str = content.trimEnd()
						parseChapter(fileInfo, chapter, str, str.length)
					}
				}
			}


			while(true) {
				var size = it.read(buffer, 0, 8192)
				if (size <= 0) {
					break
				}
				for (i in 0 until size) {
					val c = buffer[i]
					if (c == '\n') { //  || c == '\r'
						lineEnd = true
						testEnd(c)
					} else {
						if (beginChapter) {
							chapter.startPoint = scan
							beginChapter = false
						}
						content.append(c)
						if (lineEnd) {
							lineEnd = false
							lineStart = scan
						}
						lineSize++
						if (lineSize < 50) {
							line.append(c)
						}
					}
					scan++
				}
			}
			testEnd(null)

			for (i in 0 until fileInfo.chapters.size) {
				fileInfo.totalPages += fileInfo.chapters[i].pages.size
			}

			return fileInfo

		}
	}

	private fun parseChapter(fileInfo: App.FileDetail, chapter: App.Chapter, content: CharSequence, length: Int) {
		if (content.isEmpty() && chapter.name.isEmpty()) {
			return
		}
		var offset = 0
		var index = 0
		while (offset < length) {
			val pageSize = bufferView.calcPageSize(content, offset, length, index)
			if (pageSize > 0) {
				val page = App.Page()
				page.start = chapter.startPoint + offset
				page.length = pageSize
				chapter.pages.add(page)
				index++
				offset += pageSize
			} else {
				break
			}
		}
		if (chapter.pages.isEmpty()) {
			val page = App.Page()
			page.start = chapter.startPoint + offset
			page.length = 0
			chapter.pages.add(page)
			index++
		}
		fileInfo.chapters.add(chapter)
	}

	private fun initBook(width: Int, height: Int) {
		bufferView.textSize = app.config.fontSize.toFloat()
		val pattern: Pattern = try {
			Pattern.compile(app.config.topicRule, Pattern.CASE_INSENSITIVE)
		} catch (e: Exception) {
			Pattern.compile(App.TOPIC_RULE, Pattern.CASE_INSENSITIVE)
		}
		try {
			var file = File(summary.path)
			fileInfo = parseFile(file, pattern)
			summary.lastReadTime = Date().time
			app.config.lastRead = summary.key
			fileInfo.key = summary.key
			fileInfo.name = summary.name
			fileInfo.path = summary.path
			fileInfo.screenWidth = width
			fileInfo.screenHeight = height
			app.saveFileConfig(fileInfo, summary.key)
			app.saveConfig()
			showContent(true)

		} catch (e: Exception) {
			System.err.println("Error: $e")
			app.toast(getString(R.string.cannot_init_book))
		}
	}


}
