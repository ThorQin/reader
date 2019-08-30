package com.github.thorqin.reader


import android.app.AlertDialog
import android.app.Application
import android.content.Context
import com.github.thorqin.reader.entity.AppConfig
import com.google.gson.Gson
import org.apache.commons.io.FileUtils
import java.lang.Exception


class App : Application() {

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

	var _config: AppConfig? = null
	val config: AppConfig get() {
		return _config as AppConfig
	}

	override fun onCreate() {
		super.onCreate()
		_config = load()
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


