package com.github.thorqin.reader


import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.widget.Toast
import com.github.thorqin.reader.utils.json
import com.github.thorqin.reader.utils.Skip
import com.github.thorqin.reader.utils.hexString
import com.github.thorqin.reader.utils.makeListType
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

	class Page {
		var start: Long = 0
		var length: Int = 0
	}

	class ChapterStore {
		var name = ""
		var pages = ArrayList<Int>()
		var startPoint = 0L
		var endPoint = 0L
	}

	class Chapter {
		var name = ""
		var pages = ArrayList<Page>()
		var startPoint = 0L
		var endPoint = 0L
		@Skip
		var isEmpty = false
		@Skip
		var delete = false
	}

	open class FileState {
		var key = ""
		var name = ""
		var path = ""
		var encoding = "utf-8"
		var fontSize = FontSize.NORMAL
		var totalPages = 0
		var readPage = 0
		var readChapter = 0
		var readPageOfChapter = 0
		var screenWidth = 0
		var screenHeight = 0
		var topicRule: String? = null
	}

	class FileDetail : FileState() {
		@Skip
		var chapters = ArrayList<Chapter>()

		var chapterJson: String
			get() {
				val chapterStore = arrayListOf<ChapterStore>()
				for (i in 0 until chapters.size) {
					val c = chapters[i]
					val cs = ChapterStore()
					cs.startPoint = c.startPoint
					cs.endPoint = c.endPoint
					cs.name = c.name
					for (j in 0 until c.pages.size) {
						cs.pages.add(c.pages[j].length)
					}
					chapterStore.add(cs)
				}
				return json().toJson(chapterStore)
			}
			set(json) {
				chapters.clear()
				val store =
					json().fromJson(json, makeListType(ChapterStore::class.java)) as List<ChapterStore>
				for (i in 0 until store.size) {
					val cs = store[i]
					val c = Chapter()
					c.name = cs.name
					c.startPoint = cs.startPoint
					c.endPoint = cs.endPoint
					var start = c.startPoint
					for (j in 0 until cs.pages.size) {
						val p = Page()
						p.length = cs.pages[j]
						p.start = start
						start += p.length
						c.pages.add(p)
					}
					chapters.add(c)
				}
			}

		fun setNewReadPage(value: Int) {
			if (value in 0 until totalPages) {
				readPage = value
				var sum = 0
				for (i in 0 until chapters.size) {
					if (sum + chapters[i].pages.size - 1 >= readPage) {
						readChapter = i
						readPageOfChapter = readPage - sum
						break
					} else {
						sum += chapters[i].pages.size
					}
				}
			}
		}

		fun calcReadPage() {
			var sum = 0
			for (i in 0 until readChapter) {
				sum += chapters[i].pages.size
			}
			sum += readPageOfChapter
			readPage = sum
		}

		fun prevTopic() {
			if (readChapter > 0) {
				readChapter--
				readPageOfChapter = 0
				var sum = 0
				for (i in 0 until readChapter - 1) {
					sum += chapters[i].pages.size
				}
				readPage = sum
			}
		}

		fun nextTopic() {
			if (readChapter < chapters.size - 1) {
				readChapter++
				readPageOfChapter = 0
				var sum = 0
				for (i in 0 until readChapter - 1) {
					sum += chapters[i].pages.size
				}
				readPage = sum
			}
		}

		fun getContent(chapter: Int, page: Int): String {
			val file = File(path)
			if (chapters.size == 0 || chapter < 0 || chapter >= chapters.size) {
				return ""
			}
			@Suppress("NAME_SHADOWING")
			val chapter = chapters[chapter]
			if (chapter.pages.size == 0 || page < 0 || page >= chapter.pages.size) {
				return ""
			}
			@Suppress("NAME_SHADOWING")
			val page = chapter.pages[page]
			file.inputStream().use {
				it.reader(Charset.forName(encoding)).use { it1 ->
					it1.skip(page.start)
					val buffer = CharArray(page.length)
					it1.read(buffer)
					return String(buffer)
				}
			}
		}

		fun next() {
			if (readPageOfChapter < chapters[readChapter].pages.size - 1) {
				readPageOfChapter++
				readPage++
			} else if (readChapter < chapters.size - 1) {
				readChapter++
				readPageOfChapter = 0
				readPage++
			}
		}

		fun previous() {
			if (readPageOfChapter > 0) {
				readPageOfChapter--
				readPage--
			} else if (readChapter > 0) {
				readChapter--
				readPageOfChapter = chapters[readChapter].pages.size - 1
				readPage--
			}
		}
	}

	class FileSummary {
		var key = ""
		var path = ""
		var name = ""
		var totalLength = 0L
		var progress = 0f
		var lastReadTime: Long? = null
		val desc: String
			get() {
				val p = if (totalLength > 0L) {
					floor(progress * 10000f) / 100
				} else {
					0f
				}
				return if (totalLength < 10486) {
					"大小：${(totalLength.toDouble() / 1024 * 100).roundToInt() / 100.0}K  阅读：$p%"
				} else {
					"大小：${(totalLength.toDouble() / 1024 / 1024 * 100).roundToInt() / 100.0}M  阅读：$p%"
				}
			}
	}

	enum class FontSize(val value: Int) {
		SMALL(20),
		NORMAL(24),
		BIG(28)
	}

	class AppConfig {
		var files = HashMap<String, FileSummary>()
		var fontSize = FontSize.NORMAL
		var lastRead: String? = null
		var sunshineMode = false
		var eyeCareMode = false
		var clickToFlip = false
		var neverLock = false
	}

	companion object {
		private const val PREFIX = "^\\s*"
		private const val RULE1 = ".*第\\s*[0-9零一二三四五六七八九十百千万]+\\s*[卷章节篇部]"
		private const val RULE2 = "[0-9]+"
		private const val RULE3 = "[零一二三四五六七八九十百千万]+"
		private const val RULE4 = "卷\\s*[0-9零一二三四五六七八九十百千万]+"
		private const val RULE5 = "主?目录|序章?|引子|返回主?目录"
		private const val RULE6 = "Chapter\\s+[0-9]+"
		private const val SUFFIX = "(?:[、，\\s]+\\S+|\\s*$)"
		const val TOPIC_RULE = "$PREFIX(?:$RULE1|$RULE2|$RULE3|$RULE4|$RULE5|$RULE6)$SUFFIX"


		@JvmStatic
		fun digest(str: String): String {
			val digest = MessageDigest.getInstance("SHA-256")
			digest.update(str.toByteArray(Charset.forName("utf-8")))
			return hexString(digest.digest())
		}

		@JvmStatic
		fun dip2px(context: Context, dipValue: Float): Int {
			val scale = context.resources.displayMetrics.density
			return (dipValue * scale + 0.5f).toInt()
		}

		/*
		@JvmStatic
		fun px2dip(context: Context, pxValue: Float): Int {
			val scale = context.resources.displayMetrics.density
			return (pxValue / scale + 0.5f).toInt()
		}

		@JvmStatic
		fun msgbox(context: Context, msg: String, title: String?) {
			val dialog = AlertDialog.Builder(context)
			if (title != null) {
				dialog.setTitle(title)
			}
			dialog.setMessage(msg)
			dialog.show()
		}
		*/

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

	private fun load(): AppConfig {
		return try {
			val configContent = FileUtils.readFileToString(
				filesDir.resolve("config.json"), "utf-8"
			)
			json().fromJson(configContent, AppConfig::class.java)
		} catch (e: Exception) {
			AppConfig()
		}
	}

	fun saveConfig() {
		try {
			val content = json().toJson(config)
			FileUtils.writeStringToFile(filesDir.resolve("config.json"), content, "utf-8")
		} catch (e: Exception) {
			System.err.println("Save config failed: ${e.message}")
		}
	}

	fun getFileConfig(key: String): FileDetail {
		val stateFile = filesDir.resolve("books").resolve("$key.json")
		val detail: FileDetail
		if (stateFile.isFile)
			detail = json().fromJson(FileUtils.readFileToString(stateFile, "utf-8"), FileDetail::class.java)
		else
			throw Exception("Book info not exist: $key")
		val indexPath = filesDir.resolve("books").resolve("$key-chapters.json")
		if (indexPath.isFile)
			detail.chapterJson = FileUtils.readFileToString(indexPath, "utf-8")
		else
			throw Exception("Book index not exist: $key")
		return detail
	}

	fun saveFileIndex(fileInfo: FileDetail, key: String) {
		try {
			val path = filesDir.resolve("books")
			if (!path.exists()) {
				path.mkdir()
			} else if (!path.isDirectory) {
				path.delete()
				path.mkdir()
			}
			val chaptersFile = path.resolve("$key-chapters.json")
			FileUtils.writeStringToFile(chaptersFile, fileInfo.chapterJson, "utf-8")
		} catch (e: Exception) {
			System.err.println("Save book detail failed: ${e.message}")
		}
	}

	fun saveFileState(fileInfo: FileDetail, key: String) {
		try {
			val path = filesDir.resolve("books")
			if (!path.exists()) {
				path.mkdir()
			} else if (!path.isDirectory) {
				path.delete()
				path.mkdir()
			}
			val stateFile = path.resolve("$key.json")
			val content = json().toJson(fileInfo as FileState)
			FileUtils.writeStringToFile(stateFile, content, "utf-8")
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
			for (f in path.listFiles()!!) {
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


