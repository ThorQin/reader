package com.github.thorqin.reader.utils

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.*
import java.nio.charset.Charset
import java.util.*
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream


object EPub {
	private val pullFactory = XmlPullParserFactory.newInstance()

	private fun getRootFile(bytes: ByteArray): String? {
		val parser = pullFactory.newPullParser()
		ByteArrayInputStream(bytes).use {
			InputStreamReader(it, "utf-8").use { isr ->
				parser.setInput(isr)
				var event = parser.eventType
				while (event != XmlPullParser.END_DOCUMENT) {
					when (event) {
						XmlPullParser.START_TAG -> {
							if (parser.name == "rootfile" &&
								parser.getAttributeValue(null, "media-type") == "application/oebps-package+xml") {
								return parser.getAttributeValue(null, "full-path")
							}
						}
					}
					event = parser.next()
				}
			}
		}
		return null
	}

	private fun listContent(bytes: ByteArray, callBack: (id: String, filename: String) -> Unit) : String? {
		val parser = pullFactory.newPullParser()
		var bookName: String? = null
		ByteArrayInputStream(bytes).use {
			InputStreamReader(it, "utf-8").use { isr ->
				parser.setInput(isr)
				var event = parser.next()
				var beginManifest = false
				val tagStack = Stack<String>()
				var currentNamespace: String?
				while (event != XmlPullParser.END_DOCUMENT) {
					when (event) {
						XmlPullParser.START_TAG -> {
							tagStack.push(parser.name)
							if (parser.name == "manifest") {
								beginManifest = true
							} else if (parser.name == "item" && beginManifest &&
								parser.getAttributeValue(null, "media-type") == "application/xhtml+xml") {
								callBack(parser.getAttributeValue(null, "id"), parser.getAttributeValue(null, "href"))
							}
						}
						XmlPullParser.END_TAG -> {
							tagStack.pop()
							if (parser.name == "manifest") {
								beginManifest = false
							}
						}
						XmlPullParser.TEXT -> {
							if (tagStack.peek() == "dc:title") {
								bookName = parser.text
							}
						}
					}
					event = parser.next()
				}
			}
		}
		return bookName
	}

	private fun listChapter(bytes: ByteArray, callBack: (id: String) -> Unit) {
		val parser = pullFactory.newPullParser()
		ByteArrayInputStream(bytes).use {
			InputStreamReader(it, "utf-8").use { isr ->
				parser.setInput(isr)
				var event = parser.next()
				var beginSpine = false
				while (event != XmlPullParser.END_DOCUMENT) {
					when (event) {
						XmlPullParser.START_TAG -> {
							if (parser.name == "spine") {
								beginSpine = true
							} else if (parser.name == "itemref" && beginSpine) {
								callBack(parser.getAttributeValue(null, "idref"))
							}
						}
						XmlPullParser.END_TAG -> {
							if (parser.name == "spine") {
								beginSpine = false
							}
						}
					}
					event = parser.next()
				}
			}
		}
	}
	private val newLineTag = Regex("^(h[1-7]]|p|br|li)$", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
	private val ignoreTag = Regex("^(head|script|style|title)$", setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE))
	private fun readContent(bytes: ByteArray) : String {
		val parser = pullFactory.newPullParser()
		val sb = StringBuilder()
		ByteArrayInputStream(bytes).use {
			InputStreamReader(it, "utf-8").use { isr ->
				parser.setInput(isr)
				var event = parser.next()
				val tagStack = Stack<String>()
				var newLineEnd = false
				while (event != XmlPullParser.END_DOCUMENT) {
					when (event) {
						XmlPullParser.START_TAG -> {
							tagStack.push(parser.name)
							if (newLineTag.matches(parser.name)) {
								if (!newLineEnd) sb.append("\n")
								newLineEnd = true
							}
						}
						XmlPullParser.END_TAG -> {
							tagStack.pop()
						}
						XmlPullParser.TEXT -> {
							if (!ignoreTag.matches(tagStack.peek())) {
								val t = parser.text.trim()
								if (t.isNotEmpty()) {
									sb.append(t)
									newLineEnd = false
								}
							}
						}
					}
					event = parser.next()
				}
			}
		}
		return sb.toString()
	}

	private fun readAllBytes(ins: InputStream): ByteArray {
		class Buffer {
			val buf: ByteArray = ByteArray(8192)
			var size = 0
		}
		val bufferList = mutableListOf<Buffer>()
		var total = 0
		do {
			val buffer = Buffer()
			val size = ins.read(buffer.buf)
			if (size <= 0) {
				val result = ByteArray(total)
				var offset = 0
				for (b in bufferList) {
					System.arraycopy(b.buf, 0, result, offset, b.size)
					offset += b.size
				}
				return result
			} else {
				buffer.size = size
				total += size
				bufferList.add(buffer)
			}
		} while(true)
	}

	fun epub2txt(epub: String, txt: String): String? {
		val epubFile = File(epub)
		if (!epubFile.isFile) {
			throw Error("File does not exist!")
		}
		val zf = ZipFile(epubFile)
		val fileMap = hashMapOf<String, ByteArray>()
		epubFile.inputStream().use {
			ZipInputStream(it).use { zip ->
				var entry = zip.nextEntry
				while (entry != null) {
					if (!entry.isDirectory) {
						zf.getInputStream(entry).use { es ->
							fileMap.put(entry.name, readAllBytes(es))
						}
					}
					entry = zip.nextEntry
				}
				zip.closeEntry()
			}
		}
		val meta = fileMap["META-INF/container.xml"] ?: throw Error("Invalid file format!")
		val rootFile = getRootFile(meta)
		var rootPath: String = ""
		if (rootFile != null) {
			if (rootFile.lastIndexOf("/") >= 0)
			rootPath =  rootFile.substring(0, rootFile.lastIndexOf("/") + 1)
		}
		val rootContent = fileMap[rootFile] ?: throw Error("Invalid file format!")
		val nameMap = hashMapOf<String, String>()
		val bookName = listContent(rootContent) {
			id, file ->
			nameMap[id] = file
		}
		val txtFile = File(txt)
		txtFile.outputStream().use {
			it.writer(Charset.forName("utf-8")).use {
				writer ->
				listChapter(rootContent) {id ->
					val filename = nameMap[id]
					if (filename != null) {
						val bytes = fileMap[rootPath + filename]
						if (bytes != null) {
							writer.write(readContent(bytes))
							writer.write("\n\n")
						} else {
							throw Error("Invalid file format!")
						}
					} else {
						throw Error("Invalid file format!")
					}
				}
			}
		}
		return bookName
	}
}
