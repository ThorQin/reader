package com.github.thorqin.reader.activities.book

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.github.thorqin.reader.App
import kotlin.math.floor
import android.text.Layout
import android.R.attr.data
import android.text.StaticLayout



class BookView : View {
	constructor(context: Context): super(context)
	constructor(context: Context, attrs: AttributeSet?): super(context, attrs)

	var text = ""
		set(value) {
			field = value
			invalidate()
		}

	private var cw: Float = 0f
	private var paintWidth: Int = 0
	private var paintHeight: Int = 0

	var textSize = 14f
		set(value) {
			field = value
			invalidate()
		}

	var lineHeight = 20f
		set(value) {
			field = value
			val p = Paint()
			cw = p.measureText(" ")
			invalidate()
		}


	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)
		if (changed) {
			paintWidth = width - paddingLeft - paddingRight
			paintHeight = height - paddingTop - paddingBottom
			val p = Paint()
			p.textSize =  App.dip2px(context, textSize).toFloat()
			cw = p.measureText(" ")
		}
	}

	override fun onDraw(canvas: Canvas?) {
		if (canvas == null) {
			return
		}
		val p = Paint()
		p.isAntiAlias = true
		p.color = Color.BLACK
		p.strokeWidth = 2f
		p.textSize =  App.dip2px(context, textSize).toFloat()

		canvas.drawText(text, 0f, 200f, p)
	}


	fun calcPageEnd(text: String, offset: Int, length: Int) : Int {
		if (cw <= 0) {
			return 0
		}
		val len = if (length > text.length) {
			text.length
		} else length

		val lh = App.dip2px(context, lineHeight)
		val totalLines = floor(paintHeight.toDouble() / lh).toInt()
		var l = 0
		var lw = 0f
		var zero = 0.toChar()
		for (i in offset until len) {
			if (text[i] == '\r') {
				continue
			}
			if (text[i] == '\n') {
				l++
				lw = 0f
				continue
			}
			val w = if (text[i] > zero && text[i] < 256.toChar()) {
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
