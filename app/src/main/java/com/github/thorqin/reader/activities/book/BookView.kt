package com.github.thorqin.reader.activities.book

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.github.thorqin.reader.App
import kotlin.math.floor
import android.text.Layout
import android.R.attr.data
import android.graphics.*
import android.text.StaticLayout
import kotlin.math.ceil


class BookView : View {
	constructor(context: Context): super(context) {
		createPaint()
	}
	constructor(context: Context, attrs: AttributeSet?): super(context, attrs) {
		createPaint()
	}

	var text = ""
	var bookName = ""
	var chapterName = ""
	var pageNo = ""
	var progressInfo = ""

	private lateinit var paint: Paint
	private lateinit var descPaint: Paint
	private var cw: Float = 0f
	private var lh: Int = 0
	private var paintWidth: Float = 0f
	private var paintHeight: Float = 0f
	private var paintLeft: Float = 0f
	private var paintTop: Float = 0f
	private var startY: Int = 0

	var textSize = 24f
		set(value) {
			field = value
			createPaint()
			invalidate()
		}

	var descColor = "#666666"
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

	fun setBookInfo(bookName: String, chapterName: String, text: String, pageNo: String, progressInfo: String) {
		this.text = text
		this.bookName = bookName
		this.chapterName = chapterName
		this.pageNo = pageNo
		this.progressInfo = progressInfo
		paint.isAntiAlias = true
		invalidate()
	}

	private fun createPaint() {
		descPaint = Paint()
		descPaint.typeface = Typeface.MONOSPACE
		descPaint.textSize = App.dip2px(context, 14f).toFloat()
		descPaint.color = Color.parseColor(descColor)

		paint = Paint()
		paint.typeface = Typeface.MONOSPACE
		paint.textSize = App.dip2px(context, textSize).toFloat()
		paint.color = Color.parseColor(textColor)
		// paint.strokeWidth = 2f
		paint.isAntiAlias = true
		lh = App.dip2px(context, (textSize * 1.5).toFloat())
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)
		if (changed) {
			val barHeight = App.dip2px(context, 24f)
			cw = ceil(paint.measureText(" "))
			paintWidth = (width - paddingLeft - paddingRight).toFloat()
			paintHeight = (height - paddingTop - paddingBottom - barHeight * 2).toFloat()
			paintLeft = paddingLeft + (paintWidth % cw)
			paintTop = paddingTop + barHeight + (paintHeight % lh)
			val rect = Rect()
			paint.getTextBounds("a中", 0, 2, rect)
			startY = -rect.top

		}
	}

	override fun onDraw(canvas: Canvas?) {
		if (canvas == null) {
			return
		}

		canvas.drawText(this.bookName, paintLeft, startY.toFloat(), descPaint)
		canvas.drawText(this.pageNo, paintLeft + App.dip2px(context, 20f), paintTop + paintHeight + startY.toFloat(), descPaint)
		val descWidth = descPaint.measureText(this.progressInfo)
		canvas.drawText(this.progressInfo, paintWidth - descWidth - App.dip2px(context, 20f), paintTop + paintHeight + startY.toFloat(), descPaint)

		val totalLines = floor(paintHeight.toDouble() / lh).toInt()
		var l = 0
		var lw = 0f
		var zero = 0.toChar()
		for (i in 0 until text.length) {
			val c = text[i]
			if (c == '\r') {
				continue
			}
			if (c == '\n') {
				if (l < totalLines - 1) {
					l++
					lw = 0f
					continue
				} else {
					return
				}
			}
			val w = if (c > zero && c < 256.toChar()) {
				cw
			} else {
				2 * cw
			}
			if (lw + w > paintWidth) {
				if (l < totalLines - 1) {
					l++
					lw = 0f
					canvas.drawText(c.toString(), paintLeft + lw, paintTop + startY + l * lh, paint)
					lw += w
				} else {
					return
				}
			} else {
				canvas.drawText(c.toString(), paintLeft + lw, paintTop + startY + l * lh, paint)
				lw += w
			}
		}
	}


	fun calcPageEnd(text: String, offset: Int, length: Int) : Int {
		if (cw <= 0) {
			return 0
		}
		val len = if (length > text.length) {
			text.length
		} else length

		val totalLines = floor(paintHeight.toDouble() / lh).toInt()
		var l = 0
		var lw = 0f
		var zero = 0.toChar()
		for (i in offset until len) {
			val c = text[i]
			if (c == '\r') {
				continue
			}
			if (c == '\n') {
				if (l < totalLines - 1) {
					l++
					lw = 0f
					continue
				} else {
					return i - offset
				}
			}
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
