package com.github.thorqin.reader.activities.book

import android.animation.Animator
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.github.thorqin.reader.R
import kotlinx.android.synthetic.main.activity_book.*
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.View.*
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.github.thorqin.reader.App
import com.github.thorqin.reader.activities.setting.SettingsActivity
import java.io.File
import java.lang.Exception
import java.nio.charset.Charset
import java.util.*
import java.util.regex.Pattern
import kotlin.math.abs
import kotlin.math.floor


class BookActivity : AppCompatActivity() {

	companion object {
		private const val TITLE_LINE_SIZE = 25
	}

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
	private var showSceneBar = false
	private var showFontSizeBar = false

	private lateinit var handler: Handler

	private val app: App
		get() {
			return application as App
		}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		val inflater = menuInflater
		inflater.inflate(R.menu.activity_book, menu)
		return true
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
		drawerBox.addDrawerListener(object : DrawerLayout.DrawerListener {
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
			(topicList.adapter as TopicListAdapter).readChapter = fileInfo.readChapter
			topicList.requestLayout()
			topicList.setSelection(fileInfo.readChapter)
		}

		brightness.setOnClickListener {
			toggleActionBar {
				toggleSceneBar()
			}
		}

		fontSize.setOnClickListener {
			toggleActionBar {
				toggleFontSizeBar()
			}
		}

		smallSize.setOnClickListener {
			if (app.config.fontSize != App.FontSize.SMALL) {
				app.config.fontSize = App.FontSize.SMALL
				resizeFont()
			}
		}

		normalSize.setOnClickListener {
			if (app.config.fontSize != App.FontSize.NORMAL) {
				app.config.fontSize = App.FontSize.NORMAL
				resizeFont()
			}
		}

		bigSize.setOnClickListener {
			if (app.config.fontSize != App.FontSize.BIG) {
				app.config.fontSize = App.FontSize.BIG
				resizeFont()
			}
		}

		floatButton.setOnClickListener {
			app.config.sunshineMode = sunshineMode.tag != R.drawable.radius_button_checked
			applySceneMode()
			app.saveConfig()
		}

		sunshineMode.setOnClickListener {
			app.config.sunshineMode = sunshineMode.tag != R.drawable.radius_button_checked
			applySceneMode()
			app.saveConfig()
		}

		eyeCareMode.setOnClickListener {
			app.config.eyeCareMode = eyeCareMode.tag != R.drawable.radius_button_checked
			applySceneMode()
			app.saveConfig()
		}

		setting.setOnClickListener {
			toggleActionBar {
				val intent = Intent(this, SettingsActivity::class.java)
				this.startActivityForResult(intent, 1)
			}
		}

		applySceneMode()

		summary = app.config.files[key] as App.FileSummary
		toolbar.title = summary.name
		setSupportActionBar(toolbar)
		supportActionBar?.title = summary.name
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		// supportActionBar?.hide()
		appBar.visibility = INVISIBLE
		footBar.visibility = INVISIBLE
		sceneBar.visibility = INVISIBLE
		fontSizeBar.visibility = INVISIBLE
		handler.postDelayed({
			appBar.translationY = -appBar.height.toFloat()
			appBar.visibility = GONE
			footBar.translationY = footBar.height.toFloat()
			footBar.visibility = GONE
			sceneBar.translationY = sceneBar.height.toFloat()
			sceneBar.visibility = GONE
			fontSizeBar.translationY = fontSizeBar.height.toFloat()
			fontSizeBar.visibility = GONE
		}, 50)

		bufferView.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
			boxWidth = flipper.measuredWidth.toFloat()
			println("boxWidth: $boxWidth")
			val newW = right - left
			val newH = bottom - top
			if (::fileInfo.isInitialized) {
				if (fileInfo.screenWidth != newW && fileInfo.screenHeight != newH
					&& newW != 0 && newH != 0
				) {
					initBook(newW, newH)
				}
			} else {
				if (newW != 0 && newH != 0) {
					initBook(newW, newH)
				}
			}
			setPos()
		}


		var startX: Float? = null
		var viewX: Float? = null
		var moveDirection: Direction = Direction.LEFT
		flipper.setOnTouchListener { _, event ->
			when (event.action) {
				MotionEvent.ACTION_DOWN -> {
					when {
						showFontSizeBar -> {
							toggleFontSizeBar()
							false
						}
						showSceneBar -> {
							toggleSceneBar()
							false
						}
						showActionBar -> {
							toggleActionBar()
							false
						}
						else -> {
							startX = event.rawX
							surface == null
						}
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
							Direction.RIGHT -> { // flip to previous page
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
							else -> { // flip to next page
								if (event.rawX < startX!! - diff) {
									moveViewTo(surface!!, -boxWidth) {
										val topView = flipper.getChildAt(2)
										flipper.removeViewAt(2)
										flipper.addView(topView, 0)
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
						if (startX != null && abs(event.rawX - startX!!) < 10) {
							// IS CLICK
							if (startX!! > boxWidth / 4 && startX!! < boxWidth / 4 * 3) {
								toggleActionBar()
							} else if (startX!! < boxWidth / 4) {
								if (app.config.clickToFlip) {
									// flip to previous page
									val moveView = flipper.getChildAt(2)
									moveView.elevation = 20f
									moveViewTo(moveView, 0f) {
										val bottomView = flipper.getChildAt(0)
										flipper.removeViewAt(0)
										flipper.addView(bottomView)
										setPos()
										moveView.elevation = 0f
										fileInfo.previous()
										showContent(false)
									}
								}
							} else if (startX!! > boxWidth / 4 * 3) {
								if (app.config.clickToFlip) {
									val moveView = flipper.getChildAt(1)
									moveView.elevation = 20f
									moveViewTo(moveView, -boxWidth) {
										val topView = flipper.getChildAt(2)
										flipper.removeViewAt(2)
										flipper.addView(topView, 0)
										setPos()
										moveView.elevation = 0f
										fileInfo.next()
										showContent(false)
									}
								}
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

		seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
			override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
				pageNoText.text = (seekBar.progress + 1).toString()
			}

			override fun onStartTrackingTouch(p0: SeekBar?) {
				pageNo.visibility = VISIBLE
			}

			override fun onStopTrackingTouch(p0: SeekBar?) {
				pageNo.visibility = GONE
				fileInfo.setNewReadPage(seekBar.progress)
				showContent(true)
			}

		})

		prevTopic.setOnClickListener {
			fileInfo.prevTopic()
			showContent(true)
		}

		nextTopic.setOnClickListener {
			fileInfo.nextTopic()
			showContent(true)
		}

		openBook()
		keepScreenOn()
	}

	@SuppressLint("RtlHardcoded")
	override fun onBackPressed() {
		when {
			drawerBox.isDrawerOpen(Gravity.LEFT) -> drawerBox.closeDrawer(Gravity.LEFT)
			showActionBar -> toggleActionBar()
			showSceneBar -> toggleSceneBar()
			showFontSizeBar -> toggleFontSizeBar()
			else -> super.onBackPressed()
		}
	}


	private fun applySceneMode() {
		val sunshine =
			if (app.config.sunshineMode) R.drawable.radius_button_checked else R.drawable.radius_button_normal
		sunshineMode.setBackgroundResource(sunshine)
		sunshineMode.tag = sunshine

		val eyeCare =
			if (app.config.eyeCareMode) R.drawable.radius_button_checked else R.drawable.radius_button_normal
		eyeCareMode.setBackgroundResource(eyeCare)
		eyeCareMode.tag = eyeCare

		val drawable = if (app.config.sunshineMode) {
			ContextCompat.getDrawable(this, R.drawable.ic_wb_sunny_white_24dp)
		} else {
			ContextCompat.getDrawable(this, R.drawable.ic_brightness_2_white_24dp)
		}
		floatButton.setImageDrawable(drawable)

		if (floatButton.isShown) {
			floatButton.hide()
			floatButton.show()
		}


		val color = if (app.config.sunshineMode) {
			if (app.config.eyeCareMode) {
				"#fff0a0"
			} else {
				"#ffffff"
			}
		} else {
			if (app.config.eyeCareMode) {
				"#887740"
			} else {
				"#888888"
			}
		}

		for (i in 0 until flipper.childCount) {
			val bookView = flipper.getChildAt(i) as BookView
			bookView.textColor = color
		}
	}


	private fun toggleFontSizeBar(callback: (() -> Unit)? = null) {
		val anim = ValueAnimator()
		if (!showFontSizeBar) {
			fontSizeBar.visibility = VISIBLE
			anim.setFloatValues(1f, 0f)
		} else {
			anim.setFloatValues(0f, 1f)
		}
		anim.duration = 200
		anim.addUpdateListener {
			val value = anim.animatedValue as Float
			fontSizeBar.translationY = fontSizeBar.height * value
		}
		anim.addListener(object : Animator.AnimatorListener {
			override fun onAnimationRepeat(p0: Animator?) {}
			override fun onAnimationEnd(p0: Animator?) {
				if (!showFontSizeBar) {
					fontSizeBar.translationY = 0f
					showFontSizeBar = true
					callback?.invoke()
				} else {
					fontSizeBar.visibility = GONE
					showFontSizeBar = false
					callback?.invoke()
				}
			}

			override fun onAnimationCancel(p0: Animator?) {}

			override fun onAnimationStart(p0: Animator?) {}
		})
		anim.start()
	}

	private fun toggleSceneBar(callback: (() -> Unit)? = null) {
		val anim = ValueAnimator()
		if (!showSceneBar) {
			sceneBar.visibility = VISIBLE
			anim.setFloatValues(1f, 0f)
		} else {
			anim.setFloatValues(0f, 1f)
		}
		anim.duration = 200
		anim.addUpdateListener {
			val value = anim.animatedValue as Float
			sceneBar.translationY = sceneBar.height * value
		}
		anim.addListener(object : Animator.AnimatorListener {
			override fun onAnimationRepeat(p0: Animator?) {}
			override fun onAnimationEnd(p0: Animator?) {
				if (!showSceneBar) {
					sceneBar.translationY = 0f
					showSceneBar = true
					callback?.invoke()
				} else {
					sceneBar.visibility = GONE
					showSceneBar = false
					callback?.invoke()
				}
			}

			override fun onAnimationCancel(p0: Animator?) {}

			override fun onAnimationStart(p0: Animator?) {}
		})
		anim.start()
	}

	private fun toggleActionBar(callback: (() -> Unit)? = null) {
		val anim = ValueAnimator()
		if (!showActionBar) {
			appBar.visibility = VISIBLE
			footBar.visibility = VISIBLE
			floatButton.show()
			anim.setFloatValues(1f, 0f)
		} else {
			anim.setFloatValues(0f, 1f)
		}
		anim.duration = 200
		anim.addUpdateListener {
			val value = anim.animatedValue as Float
			footBar.translationY = footBar.height * value
			appBar.translationY = -appBar.height * value
			floatButton.alpha = 1 - value
		}
		anim.addListener(object : Animator.AnimatorListener {
			override fun onAnimationRepeat(p0: Animator?) {}
			override fun onAnimationEnd(p0: Animator?) {
				if (!showActionBar) {
					appBar.translationY = 0f
					footBar.translationY = 0f
					showActionBar = true
					callback?.invoke()
				} else {
					floatButton.hide()
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
			R.id.split_topic -> {
				inputTopicRule()
				true
			}
			else -> super.onOptionsItemSelected(item)
		}
	}

	private fun moveViewTo(v: View, pos: Float, onEndCallback: () -> Unit) {
		val transAnim = ObjectAnimator.ofFloat(v, "translationX", pos)
		transAnim.duration = 100
		transAnim.addListener(object : Animator.AnimatorListener {
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


	private class PageInfo(
		var text: String,
		var chapterName: String,
		var readPage: Int,
		var chapterPage: Int
	)

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
		return PageInfo(
			fileInfo.getContent(c, p),
			fileInfo.chapters[c].name, fileInfo.readPage - 1, p
		)
	}

	private fun getCurrentPageInfo(): PageInfo {
		return PageInfo(
			fileInfo.getContent(fileInfo.readChapter, fileInfo.readPageOfChapter),
			fileInfo.chapters[fileInfo.readChapter].name, fileInfo.readPage, fileInfo.readPageOfChapter
		)
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
		return PageInfo(
			fileInfo.getContent(c, p),
			fileInfo.chapters[c].name, fileInfo.readPage + 1, p
		)
	}

	private fun showContent(updateAll: Boolean) {
		val prevView = flipper.getChildAt(2) as BookView
		val currentView = flipper.getChildAt(1) as BookView
		val nextView = flipper.getChildAt(0) as BookView

		if (updateAll) {
			val current = getCurrentPageInfo()
			// currentView.text = currentText
			currentView.setBookInfo(
				fileInfo.name,
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
			prevView.setBookInfo(
				fileInfo.name,
				prev.chapterName, prev.text,
				"${prev.readPage + 1} / ${fileInfo.totalPages}",
				"${floor((prev.readPage + 1) / fileInfo.totalPages.toDouble() * 10000) / 100}%",
				prev.chapterPage
			)
		}

		val next = getNextPageInfo()
		if (next == null) {
			atEnd = true
		} else {
			atEnd = false
			// nextView.text = nextText
			nextView.setBookInfo(
				fileInfo.name,
				next.chapterName, next.text,
				"${next.readPage + 1} / ${fileInfo.totalPages}",
				"${floor((next.readPage + 1) / fileInfo.totalPages.toDouble() * 10000) / 100}%",
				next.chapterPage
			)
		}
		summary.lastReadTime = Date().time
		summary.progress = (fileInfo.readPage + 1).toFloat() / fileInfo.totalPages
		handler.postDelayed({
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
			fileInfo = app.getFileConfig(summary.key)
			app.config.lastRead = summary.key
			val adapter = TopicListAdapter(this, fileInfo.chapters)
			adapter.onSelectTopic = this::onSelectTopic
			topicList.adapter = adapter
			seekBar.max = fileInfo.totalPages - 1
			if (fileInfo.fontSize != app.config.fontSize) {
				resizeFont()
				return
			}
			applySize()
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
					when {
						lineSize in 1..TITLE_LINE_SIZE -> // match line content
							when {
								pattern.matcher(line).find() -> {
									chapter.endPoint = lineStart
									val str = content.subSequence(0, content.length - lineSize).trimEnd()
									parseChapter(chapter, str, str.length)
									fileInfo.chapters.add(chapter)
									chapter = App.Chapter()
									chapter.name = line.trim().toString()
									beginChapter = true
									content.clear()
									line.clear()
								}
								c != null -> {
									content.append(c)
									lineSize = 0
									line.clear()
								}
								else -> {
									chapter.endPoint = scan
									val str = content.trimEnd()
									parseChapter(chapter, str, str.length)
									fileInfo.chapters.add(chapter)
								}
							}
						c != null -> {
							content.append(c)
							lineSize = 0
							line.clear()
						}
						else -> {
							chapter.endPoint = scan
							val str = content.trimEnd()
							parseChapter(chapter, str, str.length)
							fileInfo.chapters.add(chapter)
						}
					}
				}
			}


			while (true) {
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
						if (lineSize < TITLE_LINE_SIZE) {
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

	private fun parseChapter(
		chapter: App.Chapter,
		content: CharSequence,
		length: Int
	) {
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

	}

	private fun initBook(width: Int, height: Int, topicRule: String = App.TOPIC_RULE) {
		bufferView.textSize = app.config.fontSize.value
		val pattern: Pattern = try {
			Pattern.compile(topicRule, Pattern.CASE_INSENSITIVE)
		} catch (e: Exception) {
			Pattern.compile(App.TOPIC_RULE, Pattern.CASE_INSENSITIVE)
		}
		try {
			applySize()
			val file = File(summary.path)
			fileInfo = parseFile(file, pattern)
			app.config.lastRead = summary.key
			fileInfo.key = summary.key
			fileInfo.name = summary.name
			fileInfo.path = summary.path
			fileInfo.screenWidth = width
			fileInfo.screenHeight = height
			seekBar.max = fileInfo.totalPages - 1
			app.saveFileIndex(fileInfo, summary.key)

			showContent(true)
			val adapter = TopicListAdapter(this, fileInfo.chapters)
			adapter.onSelectTopic = this::onSelectTopic
			topicList.adapter = adapter
		} catch (e: Exception) {
			System.err.println("Error: $e")
			app.toast(getString(R.string.cannot_init_book))
			finish()
		}
	}

	private fun applySize() {
		smallSize.setBackgroundResource(R.drawable.radius_button_normal)
		normalSize.setBackgroundResource(R.drawable.radius_button_normal)
		bigSize.setBackgroundResource(R.drawable.radius_button_normal)

		when (app.config.fontSize) {
			App.FontSize.SMALL -> {
				smallSize.setBackgroundResource(R.drawable.radius_button_checked)
			}
			App.FontSize.NORMAL -> {
				normalSize.setBackgroundResource(R.drawable.radius_button_checked)
			}
			else -> {
				bigSize.setBackgroundResource(R.drawable.radius_button_checked)
			}
		}
		bufferView.textSize = app.config.fontSize.value
		for (i in 0 until 3) {
			(flipper.getChildAt(i) as BookView).textSize = app.config.fontSize.value
		}
	}

	private fun resizeFont() {
		bufferView.textSize = app.config.fontSize.value
		handler.postDelayed({
			if (fileInfo.chapters.size > 0) {
				val readPoint =
					fileInfo.chapters[fileInfo.readChapter].pages[fileInfo.readPageOfChapter].start
				val file = File(summary.path)
				file.reader(Charset.forName(fileInfo.encoding)).use {
					var lastEnd = 0L
					for (i in 0 until fileInfo.chapters.size) {
						val c = fileInfo.chapters[i]
						it.skip(c.startPoint - lastEnd)
						val buffer = CharArray((c.endPoint - c.startPoint).toInt())
						val size = it.read(buffer)
						val str = String(buffer, 0, size).trimEnd()
						val chapter = App.Chapter()
						chapter.name = c.name
						chapter.startPoint = c.startPoint
						chapter.endPoint = c.endPoint

						parseChapter(chapter, str, str.length)
						fileInfo.chapters[i] = chapter
						lastEnd = c.endPoint
					}
				}
				val readChapter = fileInfo.chapters[fileInfo.readChapter]
				var i = readChapter.pages.size - 1
				while (i >= 0) {
					if (readPoint >= readChapter.pages[i].start) {
						fileInfo.readPageOfChapter = i
						break
					}
					i--
				}
				var total = 0
				@Suppress("NAME_SHADOWING")
				for (i in 0 until fileInfo.chapters.size) {
					total += fileInfo.chapters[i].pages.size
				}
				fileInfo.totalPages = total
				fileInfo.calcReadPage()
				seekBar.max = fileInfo.totalPages - 1
			}
			fileInfo.fontSize = app.config.fontSize
			app.saveFileIndex(fileInfo, fileInfo.key)
			app.saveFileState(fileInfo, fileInfo.key)
			applySize()
			showContent(true)
		}, 50)
	}

	private fun keepScreenOn() {
		if (app.config.neverLock) {
			window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
			window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
		} else {
			window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
			window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
		}
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		if (requestCode == 1) { // SETTING ACTIVITY CLOSED
			keepScreenOn()
		}
	}

	private fun inputTopicRule() {
		val et = EditText(this)
		et.setText(if (fileInfo.topicRule == null) App.TOPIC_RULE else fileInfo.topicRule!!)
		val dialog = AlertDialog.Builder(this).setTitle("定义分章规则")
			.setView(et)
			.setPositiveButton("确定", null)
			.setNegativeButton("取消", null)
			.create()
		dialog.setOnShowListener {
			it as AlertDialog
			val button = it.getButton(AlertDialog.BUTTON_POSITIVE)
			button.setOnClickListener {
				val input = et.text.toString()
				if (input.trim() == "") {
					Toast.makeText(this, "无效的分章规则！", Toast.LENGTH_LONG).show()
				} else {
					try {
						Pattern.compile(input)
						fileInfo.topicRule = input
						app.saveFileState(fileInfo, fileInfo.key)
						dialog.dismiss()
					} catch (e: Exception) {
						Toast.makeText(this, "无效的分章规则！", Toast.LENGTH_LONG).show()
					}
				}
			}
		}
		dialog.show()
	}
}
