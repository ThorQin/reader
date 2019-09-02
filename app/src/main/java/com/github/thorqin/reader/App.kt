package com.github.thorqin.reader


import android.app.AlertDialog
import android.app.Application
import android.content.Context
import com.google.gson.Gson
import org.apache.commons.io.FileUtils
import java.lang.Exception
import kotlin.math.floor
import kotlin.math.roundToInt


class App : Application() {


	class Chapter {
		var name = ""
		var pages = arrayListOf(ULong)
		var startPoint = 0L
	}

	class FileDetail {
		var chapters = ArrayList<Chapter>()
		var totalChapters = 0L
		var readChapter = 0L
		var totalPages = 0L
		var readPage = 0L
		var readPageOfChapter = 0L
	}

	class FileSummary {
		var path = ""
		var initialized = false
		var name = ""
		var totalLength = 0L
		var readPoint = 0L
		var lastReadTime: Long? = null
		val desc: String get() {
			var p = if (totalLength > 0L) {
				floor(readPoint.toDouble() / (totalLength * 100L) ).toInt()} else {0}
			return "大小：" + ((totalLength.toDouble() / 1024 / 1024 * 100).roundToInt() / 100.0) + "M  阅读：" + p + "%"
		}
	}

	class AppConfig {
		var files = HashMap<String, FileSummary>()
		var fontSize = 14
		var lastRead: String? = null
	}

	companion object {
		fun msgbox(context: Context, msg: String, title: String?) {
			val dialog = AlertDialog.Builder(context)
			if (title != null) {
				dialog.setTitle(title)
			}
			dialog.setMessage(msg)
			dialog.show()
		}

		fun askbox(context: Context, msg: String, title: String?, onOk: () -> Unit) {
			val dlg = AlertDialog.Builder(context)
			if (title != null) {
				dlg.setTitle(title)
			}
			dlg.setMessage(msg)
			dlg.setPositiveButton("确定") { _, _ ->
				onOk()
			}
			dlg.setCancelable(true)
			dlg.show()
		}
	}

	lateinit var config: AppConfig

	override fun onCreate() {
		super.onCreate()
		config = load()
	}

	private fun load() : AppConfig {
		return try {
			val configContent = FileUtils.readFileToString(
				filesDir.resolve("config.json"), "utf-8"
			)
			val gson = Gson()
			gson.fromJson(configContent, AppConfig::class.java)
		} catch (e: Exception) {
			AppConfig()
		}
	}

	fun saveConfig() {
		try {
			val gson = Gson()
			val content = gson.toJson(config)
			FileUtils.writeStringToFile(filesDir.resolve("config.json"), content, "utf-8")
		} catch (e: Exception) {
			System.err.println("Save config failed: " + e.message)
		}
	}

}


