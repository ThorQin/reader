package com.github.thorqin.reader.entity

import kotlin.math.floor
import kotlin.math.roundToInt

class FileSummary {
	var path = ""
	var initialized = false
	var name = ""
	var totalLength = 0L
	var readPoint = 0L
	var lastReadTime: Long? = null
	val desc: String get() {
		var p = if (totalLength > 0) {
			floor(readPoint.toDouble() / totalLength * 100).toInt()} else {0}
		return "大小：" + ((totalLength.toDouble() / 1024 / 1024 * 100).roundToInt() / 100.0) + "M  阅读：" + p + "%"
	}
}
