package com.github.thorqin.reader

import kotlin.math.floor
import kotlin.math.roundToInt

class Chapter {
	var name = ""
	var pages = 0L
	var startPoint = 0L
	var endPoint = 0L
}

class FileConfig {
	var path = ""
	var initialized = false
	var name = ""
	var totalPages = 0L
	var totalLength = 0L
	var readChapter = 0L
	var readPoint = 0L
	var chapters = ArrayList<Chapter>()
	var content = ""
	val desc: String get() {
		var p = if (totalLength > 0) {floor( readPoint.toDouble() / totalLength * 100).toInt()} else {0}
		return "大小：" + ((totalLength.toDouble() / 1024 / 1024 * 100).roundToInt() / 100.0) + "M  阅读：" + p + "%"
	}
	fun toMap() : Map<String, String> {
		return mapOf("name" to name, "progress" to desc)
	}
}

class AppConfig {
	var files = HashMap<String, FileConfig>()
	var fontSize = 14
	var lastRead: String? = null
}
