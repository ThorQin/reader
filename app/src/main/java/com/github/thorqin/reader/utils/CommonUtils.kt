package com.github.thorqin.reader.utils

import android.annotation.SuppressLint
import android.content.Context
import com.google.gson.ExclusionStrategy
import com.google.gson.FieldAttributes
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.koushikdutta.async.ByteBufferList
import com.koushikdutta.async.http.AsyncHttpClient
import com.koushikdutta.async.http.AsyncHttpGet
import com.koushikdutta.async.http.AsyncHttpPost
import com.koushikdutta.async.http.AsyncHttpResponse
import com.koushikdutta.async.http.body.AsyncHttpRequestBody
import com.koushikdutta.async.http.body.StringBody
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import java.lang.Exception
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.net.NetworkInterface.getNetworkInterfaces
import java.net.SocketException
import java.nio.charset.Charset


@Target(AnnotationTarget.FIELD)
annotation class Skip

class Exclusion: ExclusionStrategy {
	override fun shouldSkipClass(clazz: Class<*>?): Boolean {
		return false
	}
	override fun shouldSkipField(f: FieldAttributes?): Boolean {
		if (f == null) {
			return true
		}
		return f.getAnnotation(Skip::class.java) != null
	}
}

fun json(): Gson {
	return GsonBuilder()
		.addSerializationExclusionStrategy(Exclusion())
		.addDeserializationExclusionStrategy(Exclusion())
		.create()
}

private class ArrayTypeImpl(internal var clazz: Class<*>) : ParameterizedType {
	override fun getActualTypeArguments(): Array<Type> {
		return arrayOf(clazz)
	}
	override fun getRawType(): Type {
		return List::class.java
	}
	override fun getOwnerType(): Type? {
		return null
	}
}

fun <T> makeListType(type: Class<T>): Type {
	return ArrayTypeImpl(type)
}

/*
private class MapTypeImpl(internal var keyClazz: Class<*>, internal var valueClazz: Class<*>) :
	ParameterizedType {
	override fun getActualTypeArguments(): Array<Type> {
		return arrayOf(keyClazz, valueClazz)
	}
	override fun getRawType(): Type {
		return Map::class.java
	}
	override fun getOwnerType(): Type? {
		return null
	}
}

fun <K, V> makeMapType(keyType: Class<K>, valueType: Class<V>): Type {
	return MapTypeImpl(keyType, valueType)
}


fun getLocalIpAddress(): String? {
	try {
		val infos = getNetworkInterfaces()
		while (infos.hasMoreElements()) {
			val niFace = infos.nextElement()
			val enumIpAddr = niFace.inetAddresses
			while (enumIpAddr.hasMoreElements()) {
				val mInetAddress = enumIpAddr.nextElement()
				if (!mInetAddress.isLoopbackAddress) {
					return mInetAddress.hostAddress.toString()
				}
			}
		}
	} catch (e: SocketException) {

	}
	return null
}
*/

@SuppressLint("DefaultLocale")
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
		pIn.unread(bom[2].toInt())
	} else if (bom[0] == 0xFF.toByte() && bom[1] == 0xFE.toByte()) {
		charset = "utf-16le"
		pIn.unread(bom[2].toInt())
	} else {
		// Do not have BOM, so, determine whether it is en UTF-8 charset.
		pIn.unread(bom)
		var utf8 = true
		var ansi = true
		val buffer = ByteArray(4096)
		val size: Int
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

fun readTextResource(context: Context, resId: Int): String {
	context.resources.openRawResource(resId).use {
		it.reader(Charset.forName("utf-8")).use { textReader ->
			return textReader.readText()
		}
	}
}

class AppInfo (
	val code: Int,
	val version: String,
	val description: String,
	val download: String,
	val webSite: String?
)

fun getAppInfo(success: (appInfo: AppInfo?) -> Unit, error: (msg: String) -> Unit ) {
	// val url = "http://thor.qin.gitee.io/ereader-web/version.json"
	val url = "https://gitee.com/thor.qin/EReader-Web/raw/master/version.json"
	httpGet(url, AppInfo::class.java, success, error)
}

class JsonStringBody(content: String): StringBody(content) {
	override fun getContentType(): String {
		return "application/json"
	}
}

fun <T>httpPost(url: String, data: Any, resultType: Class<T>, success: (result: T?) -> Unit, error: (msg: String) -> Unit) {

	val request = AsyncHttpPost(url)

	val content = json().toJson(data)
	request.body = JsonStringBody(content)

	AsyncHttpClient.getDefaultInstance().executeByteBufferList(request,
		object : AsyncHttpClient.DownloadCallback() {
			override fun onCompleted(
				e: Exception?,
				source: AsyncHttpResponse?,
				result: ByteBufferList?
			) {
				if (e != null) {
					error(if (e.message != null ) e.message!! else e.toString())
					return
				}
				if (result == null) {
					success(null)
					return
				}
				val bytes = result.allByteArray
				if (bytes.isEmpty()) {
					success(null)
					return
				}
				try {
					val result = json().fromJson(
						String(bytes, Charset.forName("utf-8")),
						resultType
					) as T
					success(result)
				} catch (e: Exception) {
					error(if (e.message != null ) e.message!! else e.toString())
				}
			}
		}
	)
}


fun <T>httpGet(url: String, resultType: Class<T>, success: (result: T?) -> Unit, error: (msg: String) -> Unit) {
	val request = AsyncHttpGet(url)
	AsyncHttpClient.getDefaultInstance().executeByteBufferList(request,
		object : AsyncHttpClient.DownloadCallback() {
			override fun onCompleted(
				e: Exception?,
				source: AsyncHttpResponse?,
				result: ByteBufferList?
			) {
				if (e != null) {
					error(if (e.message != null ) e.message!! else e.toString())
					return
				}
				if (result == null) {
					success(null)
					return
				}
				val bytes = result.allByteArray
				if (bytes.isEmpty()) {
					success(null)
					return
				}
				try {
					val result = json().fromJson(
						String(bytes, Charset.forName("utf-8")),
						resultType
					) as T
					success(result)
				} catch (e: Exception) {
					error(if (e.message != null ) e.message!! else e.toString())
				}
			}
		}
	)
}
