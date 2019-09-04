package com.github.thorqin.reader


import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.widget.Toast
import com.google.gson.Gson
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.lang.Exception
import java.nio.charset.Charset
import java.security.MessageDigest
import kotlin.math.floor
import kotlin.math.roundToInt
class App : Application() {


	class Chapter {
		var name = ""
		var pages = ArrayList<Int>()
		var startPoint = 0L
		var endPoint = 0L
	}

	class FileDetail {
		var encoding = "utf-8"
		var fontSize = 14
		var chapters = ArrayList<Chapter>()
		var totalPages = 0L
		var readPage = 0L
		var readChapter = 0L
		var readPageOfChapter = 0L
	}

	class FileSummary {
		var key = ""
		var path = ""
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

		private val HEX_DIGITS =
			charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

		fun hexString(bytes: ByteArray): String {
			val str = CharArray(bytes.size * 2)
			var k = 0
			for (i in bytes.indices) {
				str[k++] = HEX_DIGITS[bytes[i].toInt().ushr(4) and 0xf]
				str[k++] = HEX_DIGITS[bytes[i].toInt() and 0xf]
			}
			return String(str)
		}

		fun digest(str: String): String {
			var digest = MessageDigest.getInstance("SHA-256")
			digest.update(str.toByteArray(Charset.forName("utf-8")))
			return hexString(digest.digest())
		}


		fun dip2px(context: Context, dipValue: Float): Int {
			val scale = context.resources.displayMetrics.density
			return (dipValue * scale + 0.5f).toInt()
		}

		fun px2dip(context: Context, pxValue: Float): Int {
			val scale = context.resources.displayMetrics.density
			return (pxValue / scale + 0.5f).toInt()
		}


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

		@Throws(IOException::class)
		fun detectCharset(inputStream: InputStream, defaultCharset: String? = "gb18030"): String {
			val pIn = PushbackInputStream(inputStream, 3)
			val bom = ByteArray(3)
			pIn.read(bom)
			var charset: String?
			if (bom[0] == 0xEF.toByte() && bom[1] == 0xBB.toByte() && bom[2] == 0xBF.toByte()) {
				charset = "utf-8"
			} else if (bom[0] == 0xFE.toByte() && bom[1] == 0xFF.toByte()) {
				charset = "utf-16be"
				pIn.unread(bom[2] as Int)
			} else if (bom[0] == 0xFF.toByte() && bom[1] == 0xFE.toByte()) {
				charset = "utf-16le"
				pIn.unread(bom[2] as Int)
			} else {
				// Do not have BOM, so, determine whether it is en UTF-8 charset.
				pIn.unread(bom)
				var utf8 = true
				var ansi = true
				val buffer = ByteArray(4096)
				var size: Int
				var checkBytes = 0
				size = pIn.read(buffer)
				for (i in 0 until size) {
					if (checkBytes > 0) {
						if (buffer[i].toInt() and 0xC0 == 0x80)
							checkBytes--
						else {
							utf8 = false
							ansi = false
							break
						}
					} else {
						if (buffer[i].toInt() and 0x0FF < 128)
							continue
						ansi = false
						if (buffer[i].toInt() and 0xE0 == 0xC0)
							checkBytes = 1
						else if (buffer[i].toInt() and 0xF0 == 0xE0)
							checkBytes = 2
						else {
							utf8 = false
							break
						}
					}
				}
				if (utf8)
					charset = "utf-8"
				else if (defaultCharset != null)
					charset = defaultCharset
				else if (ansi)
					charset = "us-ascii"
				else {
					charset = System.getProperty("file.encoding")
					if (charset == null)
						charset = "utf-8"
				}
			}
			return charset.trim { it <= ' ' }.toLowerCase()
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
			System.err.println("Save config failed: ${e.message}")
		}
	}

	fun getFileConfig(key: String): FileDetail {
		val path = filesDir.resolve("books").resolve("$key.json")
		if (path.isFile)
			return Gson().fromJson(FileUtils.readFileToString(path, "utf-8"), App.FileDetail::class.java)
		else
			throw Exception("File not exist!")
	}

	fun saveFileConfig(fileInfo: FileDetail, key: String) {
		try {
			val path = filesDir.resolve("books")
			if (!path.exists()) {
				path.mkdir()
			} else if (!path.isDirectory) {
				path.delete()
				path.mkdir()
			}
			val file = path.resolve("$key.json")
			val content = Gson().toJson(fileInfo)
			FileUtils.writeStringToFile(file, content, "utf-8")
		} catch (e: Exception) {
			System.err.println("Save book detail failed: ${e.message}")
		}
	}

	fun removeBook(key: String) {
		config.files.remove(key)
		val path = filesDir.resolve("books").resolve("$key.json")
		if (path.exists()) {
			path.delete()
		}
		saveConfig()
	}

	fun clearBook() {
		val path = filesDir.resolve("books")
		if (path.isDirectory) {
			for (f in path.listFiles()) {
				if (f.isFile)
					f.delete()
			}
		}
		config.files.clear()
		saveConfig()
	}

	fun toast(msg: String) {
		Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
	}

}




