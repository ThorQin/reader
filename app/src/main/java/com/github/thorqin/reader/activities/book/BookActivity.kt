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
import android.widget.Toast
import com.github.thorqin.reader.App
import com.google.gson.Gson
import org.apache.commons.io.FileUtils
import java.io.File
import java.lang.Exception
import java.nio.charset.Charset
import java.util.*


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

	private val app: App
		get () {
			return application as App
		}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val key = intent.getStringExtra("key")
		if (!app.config.files.containsKey(key)) {
			app.toast(getString(R.string.invalid_config))
			finish()
			return
		}
		summary = app.config.files[key] as App.FileSummary

		setContentView(R.layout.activity_book)
		setSupportActionBar(toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)

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
					true
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
									}
								} else {
									val toPos = -boxWidth
									moveViewTo(v!!, toPos) {
										// Do nothing
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
									}
								} else {
									moveViewTo(v!!, 0f) {
										// Do nothing
									}
								}
							}
						}
					}
					startX = null
					viewX = null
					v = null
					true
				}
				else -> false
			}
		}

		openBook()
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

	private fun getPrevPageContent(): String? {
		if (fileInfo.chapters.size == 0) {
			return null
		}
		var p: Int
		var c = fileInfo.readChapter
		if (fileInfo.readPageOfChapter > 0) {
			p = fileInfo.readPageOfChapter - 1
		} else {
			if (c > 0) {
				c--
				if (fileInfo.chapters[c].pages.size > 0) {
					p = fileInfo.chapters[c].pages.size - 1
				} else {
					return null
				}
			} else {
				return null
			}
		}
		return fileInfo.getContent(c, p)
	}

	private fun getCurrentPageContent(): String {
		return fileInfo.getContent(fileInfo.readChapter, fileInfo.readPageOfChapter)
	}

	private fun getNextPageContent(): String? {
		if (fileInfo.chapters.size == 0) {
			return null
		}
		var p: Int
		var c = fileInfo.readChapter
		if (fileInfo.readPageOfChapter < fileInfo.chapters[c].pages.size - 1) {
			p = fileInfo.readPageOfChapter + 1
		} else {
			if (c < fileInfo.chapters.size - 1) {
				c++
				if (fileInfo.chapters[c].pages.size > 0) {
					p = 0
				} else {
					return null
				}
			} else {
				return null
			}
		}
		return fileInfo.getContent(c, p)
	}

	private fun showContent() {
		val prevView = flipper.getChildAt(2) as BookView
		val currentView = flipper.getChildAt(1) as BookView
		val nextView = flipper.getChildAt(0) as BookView

		currentView.text = getCurrentPageContent()
		val prevText = getPrevPageContent()
		if (prevText == null) {
			atBegin = true
		} else {
			atBegin = false
			prevView.text = prevText
		}

		val nextText = getNextPageContent()
		if (nextText == null) {
			atEnd = true
		} else {
			atEnd = false
			nextView.text = nextText
		}
	}

	private fun openBook() {
		try {
			summary.lastReadTime = Date().time
			fileInfo = app.getFileConfig(summary.key)
			showContent()
		} catch (e: Exception) {
			System.err.println("打开索引失败，需要重新初始化: $e")
			e.printStackTrace()
		}
	}

	private fun parseFile(file: File): App.FileDetail {

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
			var lineSize = 0L
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
						if (false) { //TODO: CHANGE TO MATCH LOGIC
							chapter.endPoint = lineStart
							parseChapter(fileInfo, chapter, content.toString())
							chapter = App.Chapter()
							chapter.name = line.toString()
							beginChapter = true
							content.clear()
							line.clear()
						} else if (c != null) {
							content.append(c)
							lineSize = 0
							line.clear()
						} else {
							chapter.endPoint = scan
							parseChapter(fileInfo, chapter, content.toString())
						}
					} else if (c != null) {
						content.append(c)
						lineSize = 0
						line.clear()
					} else {
						chapter.endPoint = scan
						parseChapter(fileInfo, chapter, content.toString())
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
					if (c == '\n' || c == '\r') {
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

	private fun parseChapter(fileInfo: App.FileDetail, chapter: App.Chapter, content: String) {
		if (content.isEmpty() && chapter.name.isEmpty()) {
			return
		}
		var offset = 0
		while (offset < content.length) {
			val pageSize = bufferView.calcPageEnd(content, offset, content.length)
			if (pageSize > 0) {
				val page = App.Page()
				page.start = chapter.startPoint + offset
				page.length = pageSize
				chapter.pages.add(page)
				offset += pageSize
			} else {
				break
			}
		}
		fileInfo.chapters.add(chapter)
	}

	private fun initBook(width: Int, height: Int) {
		bufferView.textSize = app.config.fontSize.toFloat()
		try {
			var file = File(summary.path)
			fileInfo = parseFile(file)
			summary.lastReadTime = Date().time
			app.config.lastRead = summary.key
			fileInfo.key = summary.key
			fileInfo.name = summary.name
			fileInfo.path = summary.path
			fileInfo.screenWidth = width
			fileInfo.screenHeight = height
			app.saveFileConfig(fileInfo, summary.key)
			app.saveConfig()
			showContent()
		} catch (e: Exception) {
			System.err.println("Error: $e")
			app.toast(getString(R.string.cannot_init_book))
		}
	}

}
