package com.github.thorqin.reader.component

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.Gravity
import android.widget.Button


class CenterButton : Button {
	constructor(context: Context?) : super(context)
	constructor(context: Context?, attrs: AttributeSet?) : super(
		context,
		attrs
	)
	constructor(
		context: Context?,
		attrs: AttributeSet?,
		defStyleAttr: Int
	) : super(context, attrs, defStyleAttr)

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		val drawables = compoundDrawables
		val drawable = drawables[0]
		val gravity = gravity
		var left = 0
		if (gravity == Gravity.CENTER) {
			left =
				((width - drawable.intrinsicWidth - paint.measureText(text.toString())).toInt()
					/ 2)
		}
		drawable.setBounds(left, 0, left + drawable.intrinsicWidth, drawable.intrinsicHeight)
	}
}
