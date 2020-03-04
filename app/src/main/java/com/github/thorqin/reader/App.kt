package com.github.thorqin.reader


import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Environment
import android.widget.Toast
import com.github.thorqin.reader.utils.*
import org.apache.commons.io.FileUtils
import java.io.*
import java.lang.Exception
import java.nio.charset.Charset
import java.security.MessageDigest
import java.text.Collator
import java.util.*
import java.util.regex.Pattern
import java.util.zip.ZipInputStream
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.concurrent.thread
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
		var ttsPoint = 0L
		var screenWidth = 0
		var screenHeight = 0
		var topicRules: List<String>? = null
	}

	inner class FileDetail : FileState() {
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
				if (chapters == null) {
					chapters = arrayListOf()
				} else {
					chapters.clear()
				}
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

		fun getTopicRule(): String {
			val rules = if (this.topicRules == null) {
				RULES
			} else {
				this.topicRules!!
			}
			val r = rules.joinToString("|")
			return "$PREFIX(?:$r)$SUFFIX"
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

		fun getDescription(): String {
			val file = File(path)
			if (chapters.size == 0) {
				return ""
			}

			var idx = 0;
			val sb = StringBuilder()
			while (sb.length < 50 && idx < chapters.size) {
				@Suppress("NAME_SHADOWING")
				val chapter = chapters[idx++]
				file.inputStream().use {
					it.reader(Charset.forName(encoding)).use { it1 ->
						it1.skip(chapter.startPoint)
						val bufferSize = (chapter.endPoint - chapter.startPoint).toInt().coerceAtMost(3000)
						val buffer = CharArray(bufferSize)
						val size = it1.read(buffer)
						val content = String(buffer, 0, size).trim()
						if (!content.isNullOrEmpty()) {
							sb.append(content.replace(Regex("\\r"),"").replace(Regex("\\n{3,}"), "\n\n") + "\n\n")
						}
					}
				}
			}
			val s = sb.toString()
			val summaryPattern = Pattern.compile("(?:序[章幕]?|前言|引子?|(?:内容)?简介|契子)\\s*\\n((?:.|\\n)+)$", Pattern.MULTILINE)
			val m = summaryPattern.matcher(s)
			val result = if (m.find()) {
				val c = m.group(1)
				val p = Pattern.compile(getTopicRule(), Pattern.CASE_INSENSITIVE)
				val mEnd = p.matcher(c)
				if (mEnd.find()) {
					s.substring(0, mEnd.start())
				} else {
					c
				}
			} else {
				s
			}
			return result.substring(0, result.length.coerceAtMost(3000))
		}

		fun syncTTSPoint() {
			ttsPoint = if (chapters.size > 0) {
				val chapter = chapters[readChapter]
				val page = chapter.pages[readPageOfChapter]
				page.start
			} else {
				0
			}
		}

		inner class SentenceInfo(var sentence: String?, var nextPos: Long)

		fun setTtsPosition(pos: Long) {
			ttsPoint = pos
			var chapter = chapters[readChapter]
			var page = chapter.pages[readPageOfChapter]
			while (ttsPoint > page.start + page.length) {
				if (next()) {
					chapter = chapters[readChapter]
					page = chapter.pages[readPageOfChapter]
				} else {
					break
				}
			}
		}

		fun getTtsSentence(ttsPos: Long): SentenceInfo {
			var pos = ttsPos
			val result = SentenceInfo(null, pos)
			if (readChapter < chapters.size) {
				val file = File(path)
				file.inputStream().use {
					it.reader(Charset.forName(encoding)).use { it1 ->
						it1.skip(pos)
						val buffer = CharArray(2048)
						val size = it1.read(buffer)
						if (size > 0) {
							val str = String(buffer, 0, size)
							val sentenceToken = Pattern.compile("[.;\"：；“”。\\n]", Pattern.MULTILINE)
							val m = sentenceToken.matcher(str)
							if (m.find()) {
								result.sentence = str.substring(0, m.start() + 1)
								pos += result.sentence!!.length
							} else {
								result.sentence = str
								pos += size
							}
							result.nextPos = pos
						} else {
							return result
						}
					}
				}
			}
			return result
		}

		fun next(): Boolean {
			return if (readPageOfChapter < chapters[readChapter].pages.size - 1) {
				readPageOfChapter++
				readPage++
				true
			} else if (readChapter < chapters.size - 1) {
				readChapter++
				readPageOfChapter = 0
				readPage++
				true
			} else {
				false
			}
		}

		fun previous(): Boolean {
			return if (readPageOfChapter > 0) {
				readPageOfChapter--
				readPage--
				true
			} else if (readChapter > 0) {
				readChapter--
				readPageOfChapter = chapters[readChapter].pages.size - 1
				readPage--
				true
			} else {
				false
			}
		}
	}

	class FileSummary {
		var key = ""
		var path = ""
		var name = ""
		var hash: String? = null
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
				return "大小：${(totalLength.toDouble() / 1024 / 1024 * 100).roundToInt() / 100.0}M  阅读：$p%"
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
		var volumeFlip = false
		var topicRules: List<String>? = null

		fun getList(): List<FileSummary> {
			val list = mutableListOf<FileSummary>()
			for (m in this.files.entries) {
				list.add(m.value)
			}
			list.sortWith(Comparator { o1, o2 ->
				val t1 = if (o1.lastReadTime == null) 0L else o1.lastReadTime as Long
				val t2 = if (o2.lastReadTime == null) 0L else o2.lastReadTime as Long
				when {
					t1 == t2 -> {
						val com = Collator.getInstance(Locale.CHINA)
						com.compare(o1.name, o2.name)
					}
					t1 > t2 -> -1
					else -> 1
				}
			})
			return list
		}
	}

	companion object {
		private const val PREFIX = "^\\s*"
		val RULES = listOf(
			"《[^》]+》",
			".*第\\s*[0-9０１２３４５６７８９零一二三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+\\s*[卷章节篇部回讲话]",
			"[0-9]+",
			"[０１２３４５６７８９]+",
			"[零一二三四五六七八九十百千万]+",
			"[零壹贰叁肆伍陆柒捌玖拾佰仟万]+",
			"卷\\s*[0-9０１２３４５６７８９零一二三四五六七八九十百千万壹贰叁肆伍陆柒捌玖拾佰仟]+",
			"前言|主?目录|序[章幕篇]?|引[子言]?|楔子|(内容)?简介|后记|附录?|番外|花絮",
			"Chapter\\s+[0-9]+"
		)
		private const val SUFFIX = "(?:[、\\s]+\\S+|\\s*$)"
//		const val TOPIC_RULE = "$PREFIX(?:$RULE1|$RULE2|$RULE3|$RULE4|$RULE5|$RULE6)$SUFFIX"

		fun getTopicRule(): String {
			val r = RULES.joinToString("|")
			return "$PREFIX(?:$r)$SUFFIX"
		}

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
		*/

		fun msgbox(context: Context, msg: String, title: String?) {
			val dialog = AlertDialog.Builder(context, R.style.dialogStyle)
			if (title != null) {
				dialog.setTitle(title)
			}
			dialog.setPositiveButton(R.string.ok) { _, _ ->

			}
			dialog.setMessage(msg)
			dialog.show()
		}

		fun askbox(context: Context, msg: String, title: String?, onOk: () -> Unit, onCancel: (() -> Unit)?) {
			val dlg = AlertDialog.Builder(context, R.style.dialogStyle)
			if (title != null) {
				dlg.setTitle(title)
			}
			dlg.setMessage(msg)
			dlg.setPositiveButton(R.string.ok) { _, _ ->
				onOk()
			}
			dlg.setNegativeButton(R.string.cancel) { _, _ ->
				if (onCancel != null)
					onCancel()
			}
			dlg.setCancelable(true)
			dlg.setOnCancelListener {
				if (onCancel != null)
					onCancel()
			}
			dlg.show()
		}

		fun toast(context: Context, msg: String, showItem: Int = Toast.LENGTH_LONG) {
			Toast.makeText(context, msg, showItem).show()
		}


	}

	lateinit var config: AppConfig

	var mainPage: String? = null
	var configTime: Date? = null

	override fun onCreate() {
		super.onCreate()
		config = load()
		initChinesePhrase()
	}

	private fun load(): AppConfig {
		val c = try {
			val configContent = FileUtils.readFileToString(
				filesDir.resolve("config.json"), "utf-8"
			)
			json().fromJson(configContent, AppConfig::class.java)
		} catch (e: Exception) {
			AppConfig()
		}
		if (c.topicRules == null) {
			c.topicRules = RULES
		}
		return c
	}

	fun newDetail(): FileDetail {
		return FileDetail()
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
		val indexPath = filesDir.resolve("books").resolve("$key-chapters.json")
		if (indexPath.isFile) {
			indexPath.delete()
		}
		saveConfig()
	}

	fun deleteBook(key: String) {
		try {
			val summary = config.files[key]
			if (summary != null) {
				val bookFile = File(summary.path)
				if (bookFile.extension == "text") {
					val epubFile = bookFile.parentFile.resolve(bookFile.nameWithoutExtension + ".epub")
					if (epubFile.isFile) {
						epubFile.delete()
					}
				} else if (bookFile.extension == "epub") {
					val textFile = bookFile.parentFile.resolve(bookFile.nameWithoutExtension + ".text")
					if (textFile.isFile) {
						textFile.delete()
					}
				}
				if (bookFile.isFile) {
					 bookFile.delete()
				}
				val stateFile = filesDir.resolve("books").resolve("$key.json")
				if (stateFile.isFile) {
					stateFile.delete()
				}
				val indexPath = filesDir.resolve("books").resolve("$key-chapters.json")
				if (indexPath.isFile) {
					indexPath.delete()
				}
			}
			config.files.remove(key)
		} catch (e: Exception) {
			toast(this,"删除错误！")
		}
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

	private var phraseSet: Set<String>? = null

	private fun initChinesePhrase() {
		thread(start = true, isDaemon = true) {
			try {
				// val beginTime = System.currentTimeMillis()
				val newSet = HashSet<String>(276900)
				this.resources.openRawResource(R.raw.words).use {
					ZipInputStream(it).use { zip ->
						var out = ByteArrayOutputStream(4096)
						val buffer = ByteArray(4096)
						zip.nextEntry
						var size = zip.read(buffer)
						while (size >= 0) {
							out.write(buffer, 0, size)
							size = zip.read(buffer)
						}

						val str = String(out.toByteArray(), Charset.forName("utf-8"))
						val tokenizer = StringTokenizer(str, ",")
						while (tokenizer.hasMoreTokens()) {
							newSet.add(tokenizer.nextToken())
						}
						zip.closeEntry()
					}
				}
				phraseSet = newSet
				// println("\n\nload dict end, use time: ${System.currentTimeMillis() - beginTime}\n\n")
			} catch (e: Exception) {
				System.err.println("Load words list failed: $e")
			}
		}
	}

	fun isChinesePhrase(s: String?): Boolean {
		if (phraseSet == null) {
			return false
		}
		if (s == null) {
			return false
		}
		return try {
			 phraseSet!!.contains(s)
		} catch(e: Throwable) {
			false
		}
	}

	fun getWebSiteURL(success: (url: String) -> Unit, fail: () -> Unit) {

// 		DEBUG
//		mainPage = "http://192.168.1.5:8080/"
//		configTime = Date()

		if (mainPage == null || configTime == null || (Date().time - configTime!!.time) > 86400000) {
			getAppInfo({
				if (it?.webSite == null) {
					if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE > 0) {
						mainPage = "http://192.168.1.5:8080/"
						configTime = Date()
						success("http://192.168.1.5:8080/")
					} else {
						fail()
					}
				} else {
					val url = if (it.webSite.endsWith("/")) it.webSite else it.webSite + "/"
					mainPage = url
					configTime = Date()
					success(url)
				}
			}, {
				fail()
			})
		} else {
			success(mainPage!!)
		}
	}

	fun getExternalBookRootDir(): File {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			this.getExternalFilesDir("download")!!
		} else {
			@Suppress("DEPRECATION")
			val extRoot = Environment.getExternalStorageDirectory()
			extRoot.resolve("com.github.thorqin.reader")
		}
	}

	fun getExternalRootDir(): File {
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			this.getExternalFilesDir("download")!!
		} else {
			@Suppress("DEPRECATION")
			Environment.getExternalStorageDirectory()
		}
	}

}


