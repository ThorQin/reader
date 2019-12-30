package com.github.thorqin.reader.activities.community

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_BACK
import android.webkit.*
import com.github.thorqin.reader.App
import com.github.thorqin.reader.R
import com.github.thorqin.reader.utils.getAppInfo
import kotlinx.android.synthetic.main.activity_community.*
import java.lang.Exception

class CommunityActivity : AppCompatActivity() {

	lateinit var mainPage: String
//
//	val handler = Handler(Looper.getMainLooper())

	companion object {
		val inject = """
			(function() {
				"use strict";
				try {
					let parent = document.getElementsByTagName('head')[0] || document.body || document.documentElement;
					let s = document.createElement('style');
					s.innerHTML = `
						body {
							color: #ccc;
						}
					`;
					parent.insertBefore(s, parent.firstChild);
					console.log(document.documentElement.innerHTML);
				} catch (e) {
					console.error(e)
				}
			})()
		""".trimIndent()

		val showError = """
			(function() {
				"use strict";
				window.location.replace('res://error.html')
			})()
		""".trimIndent()

		val fileMap = hashMapOf(
			"js" to "application/javascript",
			"css" to "text/css",
			"htm" to "text/html",
			"html" to "text/html",
			"png" to "image/png",
			"jpg" to "image/jpeg",
			"jpeg" to "image/jpeg",
			"svg" to "image/svg+xml",
			"json" to "application/json"
		)

		fun getMimeByName(name: String): String {
			val dot = name.lastIndexOf(".")
			return if (dot < 0) {
				"application/octet-stream"
			} else {
				fileMap[name.substring(dot + 1)] ?: "application/octet-stream"
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_community)
		webView.settings.allowFileAccess = true
		webView.settings.allowContentAccess = true
		webView.settings.allowFileAccessFromFileURLs = true
		webView.settings.javaScriptEnabled = true
		webView.settings.setSupportZoom(false)
		webView.settings.defaultTextEncodingName = "utf-8"
		webView.settings.domStorageEnabled = true
		webView.settings.databaseEnabled = true
		webView.settings.setAppCacheEnabled(true)
		webView.settings.loadsImagesAutomatically = true
		webView.setBackgroundColor(getColor(R.color.colorBlack))

		webView.addJavascriptInterface(object {

			@JavascriptInterface
			fun showToast(message: String) {
				App.toast(this@CommunityActivity, message)
			}

			@JavascriptInterface
			fun msgbox(message: String) {
				App.msgbox(this@CommunityActivity, message, this@CommunityActivity.getString(R.string.app_name))
			}

			@JavascriptInterface
			fun askbox(message: String, callbackKey: String) {
				App.askbox(this@CommunityActivity, message, this@CommunityActivity.getString(R.string.app_name), {
					runOnUiThread {
						webView.evaluateJavascript("eReaderClient.invokeCallback('$callbackKey', true)", null)
					}
				}, {
					runOnUiThread {
						webView.evaluateJavascript("eReaderClient.invokeCallback('$callbackKey', false)", null)
					}
				})
			}

			@JavascriptInterface
			fun close() {
				finish()
			}
		}, "eReader")

		webView.webChromeClient = object: WebChromeClient() {
//			override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
//				return super.onConsoleMessage(consoleMessage)
//			}
		}
		webView.webViewClient = object: WebViewClient() {
//			override fun onPageFinished(view: WebView?, url: String?) {
//				super.onPageFinished(view, url)
//			}

			override fun onReceivedError(
				view: WebView?,
				request: WebResourceRequest?,
				error: WebResourceError?
			) {
				super.onReceivedError(view, request, error)
				if (request?.url.toString() == mainPage) {
					webView.evaluateJavascript(showError) {}
				}
			}

			override fun shouldInterceptRequest(
				view: WebView?,
				request: WebResourceRequest?
			): WebResourceResponse? {
				println("Request: ${request?.url}")
				return if (request?.url?.scheme == "res") {
					val filename = request.url.toString().substring(6)
					try {
						val inputStream = resources.assets.open(filename)
						val contentType = getMimeByName(filename)
						WebResourceResponse(contentType, "utf-8", inputStream)
					} catch (e: Exception){
						WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, null)
					}
				} else {
					super.shouldInterceptRequest(view, request)
				}
			}
		}

		getAppInfo({
			runOnUiThread {
				mainPage = if (it?.webSite == null) "http://192.168.1.5:8080/" else it.webSite
				webView.loadUrl(mainPage)
			}
		}, {
			runOnUiThread {
				App.toast(this, "网络图书暂不可用!")
			}
		})
	}

	override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
		if (keyCode == KEYCODE_BACK) {
			webView.evaluateJavascript("eReaderClient.popWindow()") {
				if (it == "false") {
					this.finish()
				}
			}

			return true
		} else {
			return super.onKeyDown(keyCode, event)
		}
	}
}
