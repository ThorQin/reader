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
import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View.*
import android.widget.SeekBar
import androidx.drawerlayout.widget.DrawerLayout
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


	@SuppressLint("RtlHardcoded")
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

		var surface: View? = null
		drawerBox.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
		drawerBox.addDrawerListener(object: DrawerLayout.DrawerListener {
			override fun onDrawerStateChanged(newState: Int) {}

			override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
				surface = null
				if (showActionBar) {
					toggleActionBar()
				}
			}

			override fun onDrawerClosed(drawerView: View) {
				surface = null
			}

			override fun onDrawerOpened(drawerView: View) {
				surface = null
			}

		})

		topicButton.setOnClickListener {
			toggleActionBar()
			drawerBox.openDrawer(Gravity.LEFT, true)
		}

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


		var startX : Float? = null
		var viewX: Float? = null
		var moveDirection: Direction = Direction.LEFT
		flipper.setOnTouchListener { _, event ->
			when (event.action ) {
				MotionEvent.ACTION_DOWN -> {
					if (showActionBar) {
						toggleActionBar()
						false
					} else {
						startX = event.rawX
						surface == null
					}
				}
				MotionEvent.ACTION_MOVE -> {
					if (startX != null) {
						if (surface == null) {
							if (event.rawX > startX!! && atBegin) {
								// do nothing
							} else if (event.rawX < startX!! && atEnd) {
								// do nothing
							} else {
								if (event.rawX > startX!! + 10f) {
									moveDirection = Direction.RIGHT
									surface = flipper.getChildAt(2)
								} else if (event.rawX < startX!! - 10f) {
									moveDirection = Direction.LEFT
									surface = flipper.getChildAt(1)
								}

								viewX = surface?.translationX
							}
						}
						if (surface != null) {
							surface?.elevation = 20f
							when (moveDirection) {
								Direction.RIGHT -> {
									if (event.rawX > startX!!) {
										surface!!.translationX = viewX!! + event.rawX - startX!!
									}
								}
								else -> {
									if (event.rawX < startX!!) {
										surface!!.translationX = viewX!! + event.rawX - startX!!
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
					if (surface != null) {
						when (moveDirection) {
							Direction.RIGHT -> {
								if (event.rawX > startX!! + diff) {
									val toPos = 0f
									moveViewTo(surface!!, toPos) {
										val bottomView = flipper.getChildAt(0)
										flipper.removeViewAt(0)
										flipper.addView(bottomView)
										setPos()
										surface?.elevation = 0f
										surface = null
										fileInfo.previous()
										showContent(false)
									}
								} else {
									val toPos = -boxWidth
									moveViewTo(surface!!, toPos) {
										surface?.elevation = 0f
										surface = null
										// Restore, no change
									}
								}
							}
							else -> {
								if (event.rawX < startX!! - diff) {
									moveViewTo(surface!!, -boxWidth) {
										val bottomView = flipper.getChildAt(2)
										flipper.removeViewAt(2)
										flipper.addView(bottomView, 0)
										setPos()
										surface?.elevation = 0f
										surface = null
										fileInfo.next()
										showContent(false)
									}
								} else {
									moveViewTo(surface!!, 0f) {
										surface?.elevation = 0f
										surface = null
										// Restore, no change
									}
								}
							}
						}
					} else {
						if (startX != null && abs(event.rawX - startX!!) < 10 ) {
							// IS CLICK
							if (startX!! > boxWidth / 4 && startX!! < boxWidth / 4 * 3) {
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

		seekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {

			}

			override fun onStartTrackingTouch(p0: SeekBar?) {

			}

			override fun onStopTrackingTouch(p0: SeekBar?) {
				fileInfo.updateReadPage(seekBar.progress)
				showContent(true)
			}

		})



		openBook()
	}

	private fun toggleActionBar(callback: (() -> Unit)? = null) {
		val anim = ValueAnimator()
		if (!showActionBar) {
			appBar.visibility = VISIBLE
			footBar.visibility = VISIBLE
			anim.setFloatValues(1f, 0f)
		} else {
			anim.setFloatValues(0f, 1f)
		}
		anim.duration = 200
		anim.addUpdateListener {
			val value = anim.animatedValue as Float
			footBar.translationY = footBar.height * value
			appBar.translationY = -appBar.height * value
		}
		anim.addListener(object: Animator.AnimatorListener {
			override fun onAnimationRepeat(p0: Animator?) {}
			override fun onAnimationEnd(p0: Animator?) {
				if (!showActionBar) {
					appBar.translationY = 0f
					footBar.translationY = 0f
					showActionBar = true
					callback?.invoke()
				} else {
					appBar.visibility = GONE
					footBar.translationY = -appBar.height.toFloat()
					footBar.visibility = GONE
					footBar.translationY = footBar.height.toFloat()
					showActionBar = false
					callback?.invoke()
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
		val p: Int
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
			fileInfo.chapters[c].name, fileInfo.readPage - 1, p)
	}

	private fun getCurrentPageInfo(): PageInfo {
		return PageInfo(fileInfo.getContent(fileInfo.readChapter, fileInfo.readPageOfChapter),
			fileInfo.chapters[fileInfo.readChapter].name, fileInfo.readPage, fileInfo.readPageOfChapter)
	}

	private fun getNextPageInfo(): PageInfo? {
		if (fileInfo.chapters.size == 0) {
			return null
		}
		val p: Int
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
			fileInfo.chapters[c].name, fileInfo.readPage + 1, p)
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
				"${floor((fileInfo.readPage + 1) / fileInfo.totalPages.toDouble() * 10000) / 100}%",
				current.chapterPage
				)
		}

		seekBar.progress = fileInfo.readPage
		val prev = getPrevPageInfo()
		if (prev == null) {
			atBegin = true
		} else {
			atBegin = false
			//prevView.text = prevText
			prevView.setBookInfo(fileInfo.name,
				prev.chapterName, prev.text,
			"${prev.readPage + 1} / ${fileInfo.totalPages}",
			"${floor((prev.readPage + 1) / fileInfo.totalPages.toDouble() * 10000) / 100}%",
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
				"${floor((next.readPage + 1) / fileInfo.totalPages.toDouble() * 10000) / 100}%",
				next.chapterPage)
		}
		summary.lastReadTime = Date().time
		summary.progress = (fileInfo.readPage + 1) / fileInfo.totalPages.toFloat()
		handler.postDelayed( {
			app.saveFileState(fileInfo, fileInfo.key)
			app.saveConfig()
		}, 50)
	}

	@SuppressLint("RtlHardcoded")
	private fun onSelectTopic(chapterIndex: Int) {
		fileInfo.readChapter = chapterIndex
		fileInfo.readPageOfChapter = 0
		var p = 0
		for (i in 0 until chapterIndex) {
			p += fileInfo.chapters[i].pages.size
		}
		fileInfo.readPage = p
		showContent(true)
		drawerBox.closeDrawer(Gravity.LEFT, true)
	}

	private fun openBook() {
		try {
			val fontSize = app.config.fontSize.toFloat()
			for (i in 0 until 3) {
				(flipper.getChildAt(i) as BookView).textSize = fontSize
			}
			bufferView.textSize = fontSize
			fileInfo = app.getFileConfig(summary.key)
			seekBar.max = fileInfo.totalPages - 1
			app.config.lastRead = summary.key
			showContent(true)
			val adapter = TopicListAdapter(this, fileInfo.chapters)
			adapter.onSelectTopic = this::onSelectTopic
			topicList.adapter = adapter
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
			val buffer = CharArray(8192)
			val line = StringBuilder(8192)
			val content = StringBuilder(8192)
			var lineStart = 0L
			var lineSize = 0
			var scan = 0L
			var lineEnd = true
			var beginChapter = true
			val fileInfo: App.FileDetail = App.FileDetail()
			fileInfo.encoding = charset
			fileInfo.name = summary.name
			fileInfo.fontSize = app.config.fontSize

			var chapter = App.Chapter()
			chapter.name = fileInfo.name

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
				val size = it.read(buffer, 0, 8192)
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
			val file = File(summary.path)
			fileInfo = parseFile(file, pattern)
			app.config.lastRead = summary.key
			fileInfo.key = summary.key
			fileInfo.name = summary.name
			fileInfo.path = summary.path
			fileInfo.screenWidth = width
			fileInfo.screenHeight = height
			seekBar.max = fileInfo.totalPages - 1
			showContent(true)
			val adapter = TopicListAdapter(this, fileInfo.chapters)
			adapter.onSelectTopic = this::onSelectTopic
			topicList.adapter = adapter
		} catch (e: Exception) {
			System.err.println("Error: $e")
			app.toast(getString(R.string.cannot_init_book))
		}
	}


}
