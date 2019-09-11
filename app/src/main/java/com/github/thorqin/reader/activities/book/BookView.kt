package com.github.thorqin.reader.activities.book

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.github.thorqin.reader.App
import kotlin.math.floor
import android.graphics.*
import kotlin.math.ceil


class BookView : View {
	constructor(context: Context): super(context) {
		createPaint()
	}
	constructor(context: Context, attrs: AttributeSet?): super(context, attrs) {
		createPaint()
	}

	private var text = ""
	private var bookName = ""
	private var chapterName = ""
	private var pageNo = ""
	private var progressInfo = ""
	private var pageIndex = 0

	private lateinit var paint: Paint
	private lateinit var descPaint: Paint
	private lateinit var titlePaint: Paint
	private var cw = 0f
	private var lh = 0
	private var paintWidth = 0f
	private var paintHeight = 0f
	private var paintLeft = 0f
	private var paintTop = 0f
	private var startY = 0
	private var pxPerDp = 0

	var textSize = 24
		set(value) {
			field = value
			createPaint()
			initValues()
			invalidate()
		}

	private var descColor = "#666666"
		set(value) {
			field = value
			createPaint()
			invalidate()
		}

	var textColor = "#aaaaaa"
		set(value) {
			field = value
			createPaint()
			invalidate()
		}

	fun setBookInfo(bookName: String, chapterName: String, text: String, pageNo: String, progressInfo: String, pageIndex: Int) {
		this.text = text
		this.bookName = bookName
		this.chapterName = chapterName
		this.pageNo = pageNo
		this.progressInfo = progressInfo
		this.pageIndex = pageIndex
		paint.isAntiAlias = true
		invalidate()
	}

	private fun createPaint() {
		descPaint = Paint()
		descPaint.typeface = Typeface.MONOSPACE
		descPaint.textSize = App.dip2px(context, 14f).toFloat()
		descPaint.color = Color.parseColor(descColor)
		descPaint.isAntiAlias = true

		paint = Paint()
		paint.typeface = Typeface.MONOSPACE
		paint.textSize = App.dip2px(context, textSize.toFloat()).toFloat()
		paint.color = Color.parseColor(textColor)
		paint.isAntiAlias = true

		titlePaint = Paint()
		titlePaint.typeface = Typeface.MONOSPACE
		titlePaint.textSize = App.dip2px(context, textSize * 1.5f).toFloat()
		titlePaint.color = Color.parseColor(textColor)
		titlePaint.isAntiAlias = true

		lh = App.dip2px(context, (textSize * 1.5).toFloat())
	}

	private val _rect = Rect()
	private val _titleRect = Rect()
	private var barHeight: Int = 0
	private var titleCw: Float = 0f
	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)
		if (changed) {
			initValues()
		}
	}

	private fun initValues() {
		barHeight = App.dip2px(context, 24f)
		cw = ceil(paint.measureText(" "))
		paintWidth = (width - paddingLeft - paddingRight).toFloat()
		paintHeight = height - paddingTop - paddingBottom - barHeight * 2f
		paintLeft = paddingLeft + (paintWidth % cw) / 2
		paintTop = paddingTop + barHeight + (paintHeight % lh)

		paint.getTextBounds("a中", 0, 2, _rect)
		startY = -_rect.top
		pxPerDp = App.dip2px(context, 1f)

		titlePaint.getTextBounds("a中", 0, 2, _titleRect)
		titleCw = ceil(titlePaint.measureText(" "))
	}


	private val zero = 0.toChar()

	private fun drawTitle(canvas: Canvas, title: String) {

		var t = paintTop
		val l = paintLeft
		val lh = _titleRect.bottom - _titleRect.top

		var lw = 0f
		var line = 1
		for (c in title) {
			val w = if (c > zero && c < 256.toChar()) {
				titleCw
			} else {
				2 * titleCw
			}
			if (lw + w > paintWidth) {
				if (line < 2) {
					lw = 0f
					t += lh + 3 * pxPerDp
					line++
				} else {
					break
				}
			}
			canvas.drawText(c.toString(), paintLeft + lw, t -_titleRect.top, titlePaint)
			lw += w
		}
		t += lh + 3 * pxPerDp
		canvas.drawRect(l, t,
			l + paintWidth, t + 1 * pxPerDp.toFloat(), titlePaint)
	}

	override fun onDraw(canvas: Canvas?) {
		if (canvas == null) {
			return
		}

		val caption = if (chapterName.isEmpty()) this.bookName else chapterName
		val title = if (pageIndex == 0) this.bookName else caption
		canvas.drawText(title , paintLeft, startY.toFloat() + 2 * pxPerDp, descPaint)
		canvas.drawText(this.pageNo, paintLeft + pxPerDp * 20, paintTop + paintHeight + startY.toFloat() - 5 * pxPerDp, descPaint)

		val descWidth = descPaint.measureText(this.progressInfo)
		canvas.drawText(this.progressInfo, paintWidth - descWidth - pxPerDp * 20, paintTop + paintHeight + startY.toFloat() - 5 * pxPerDp, descPaint)

		if (pageIndex == 0) {
			drawTitle(canvas, caption)
		}

		val totalLines = floor(paintHeight.toDouble() / lh).toInt()
		var l = if (pageIndex == 0) 3 else 0
		var lw = 0f

		var begin = true
		for (c in text) {
			if (c == '\r') {
				continue
			}
			if (c == '\n') {
				if (begin) {
					continue
				}
				if (l < totalLines - 1) {
					l++
					lw = 0f
					continue
				} else {
					return
				}
			}
			begin = false
			val w = if (c > zero && c < 256.toChar()) {
				cw
			} else {
				2 * cw
			}
			if (lw + w > paintWidth) {
				if (l < totalLines - 1) {
					l++
					lw = 0f
				} else {
					return
				}
			}
			canvas.drawText(c.toString(), paintLeft + lw, paintTop + startY + l * lh, paint)
			lw += w
		}
	}


	fun calcPageSize(text: CharSequence, offset: Int, length: Int, pageIndex: Int) : Int {
		if (cw <= 0) {
			return 0
		}
		val len = if (length > text.length) {
			text.length
		} else length

		var totalLines = floor(paintHeight.toDouble() / lh).toInt()
		if (pageIndex == 0) {
			totalLines -= 3
		}
		var l = 0
		var lw = 0f
		val zero = 0.toChar()
		var begin = true
		for (i in offset until len) {
			val c = text[i]
			if (c == '\r') {
				continue
			}
			if (c == '\n') {
				if (begin) {
					continue
				}
				if (l < totalLines - 1) {
					l++
					lw = 0f
					continue
				} else {
					return i - offset
				}
			}
			begin = false
			val w = if (c > zero && c < 256.toChar()) {
				cw
			} else {
				2 * cw
			}
			if (lw + w > paintWidth) {
				if (l < totalLines - 1) {
					l++
					lw = w
				} else {
					return i - offset
				}
			} else {
				lw += w
			}
		}
		return len - offset
	}
}
