package com.github.thorqin.reader.activities.main

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import com.github.thorqin.reader.App
import com.github.thorqin.reader.R
import com.github.thorqin.reader.utils.json
import com.koushikdutta.async.ByteBufferList
import com.koushikdutta.async.http.AsyncHttpClient
import com.koushikdutta.async.http.AsyncHttpGet
import com.koushikdutta.async.http.AsyncHttpResponse
import java.io.File
import java.lang.Exception
import java.nio.charset.Charset
import java.util.*
import kotlin.concurrent.timer

class UpdateManager(private var activity: MainActivity) {
	private var downloadId: Long? = null
	private val downloadManager: DownloadManager = activity.getSystemService(AppCompatActivity.DOWNLOAD_SERVICE) as DownloadManager
	private val downloadManagerReceiver: BroadcastReceiver
	private val handler: Handler = Handler(Looper.getMainLooper())
	init {
		downloadManagerReceiver = object : BroadcastReceiver() {
			override fun onReceive(context: Context?, intent: Intent?) {
				if (intent == null) {
					return
				}
				if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
					return
				}

				val downId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
				if (downId != downloadId) {
					return
				}

				downloadId = null
				try {
					val query = DownloadManager.Query()
					query.setFilterById(downId)
					val cursor = downloadManager.query(query)
					cursor.use {
						if (cursor.moveToFirst()) {
							val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
							val path = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
							val contentUri = downloadManager.getUriForDownloadedFile(downId)
							println("COLUMN_LOCAL_URI: $path")
							println("getUriForDownloadedFile Uri: $contentUri")
							if (status == DownloadManager.STATUS_SUCCESSFUL) {
								val installIntent = Intent(Intent.ACTION_VIEW)
								when {
									Build.VERSION.SDK_INT < Build.VERSION_CODES.N -> installIntent.setDataAndType(
										Uri.parse(path),
										"application/vnd.android.package-archive"
									)
									else -> {
										installIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
										val packageName = activity.packageName
										val filePath = Uri.parse(path).path!!
										val file = File(filePath)
										val uri =
											FileProvider.getUriForFile(activity, "$packageName.fileprovider", file)
										installIntent.setDataAndType(
											uri,
											"application/vnd.android.package-archive"
										)
									}
								}
								installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
								activity.startActivity(installIntent)
							}
						}
					}
				} catch (e: Throwable) {
					e.printStackTrace(System.err)
				}
			}
		}
		activity.registerReceiver(
			downloadManagerReceiver,
			IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
		)
	}

	fun destroy() {
		activity.unregisterReceiver(downloadManagerReceiver)
	}

	private val app: App
		get() {
			return activity.application as App
		}

	class ApkVersion(
		val code: Int,
		val version: String,
		val description: String,
		val download: String
	)

	private fun queryVersion(success: (result: ApkVersion) -> Unit, error: (msg: String) -> Unit) {
		val url = "http://thor.qin.gitee.io/ereader-web/version.json"
		val request = AsyncHttpGet(url)
		AsyncHttpClient.getDefaultInstance().executeByteBufferList(request,
			object : AsyncHttpClient.DownloadCallback() {
				override fun onCompleted(
					e: Exception?,
					source: AsyncHttpResponse?,
					result: ByteBufferList?
				) {
					if (e != null) {
						activity.runOnUiThread {
							error(e.message ?: "网络错误!")
						}
						return
					}
					if (result == null) {
						activity.runOnUiThread {
							error("网络错误!")
						}
						return
					}
					val bytes = result.allByteArray
					val appVersion = json().fromJson(
						String(bytes, Charset.forName("utf-8")),
						ApkVersion::class.java
					) as ApkVersion
					activity.runOnUiThread {
						success(appVersion)
					}
				}
			}
		)
	}

	private fun download(
		url: String,
		version: String,
		fileName: String,
		callback: (downloadId: Long?) -> Unit
	) {
		val request = DownloadManager.Request(Uri.parse(url))
		request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
		request.setTitle("轻阅读")
		request.setDescription("轻阅读：$version")
		request.setMimeType("application/vnd.android.package-archive")
		request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
		@Suppress("DEPRECATION")
		request.setVisibleInDownloadsUi(true)
		val downloadId = downloadManager.enqueue(request)
		callback(downloadId)
	}

	private fun queryDownloadTimer(
		downloadId: Long,
		callback: (status: Int, savedSize: Long, totalSize: Long) -> Unit
	): Timer {
		return timer("download", true, 100, 300) {
			try {
				val query = DownloadManager.Query()
				query.setFilterById(downloadId)
				val cursor = downloadManager.query(query)
				cursor.use {
					if (!cursor.moveToFirst()) {
						activity.runOnUiThread {
							callback(DownloadManager.STATUS_FAILED, 0, 0)
						}
					} else {
						val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
						val savedSize =
							cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
						val totalSize =
							cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
						activity.runOnUiThread {
							callback(status, savedSize, totalSize)
						}
					}
				}
			} catch (e: Throwable) {
				e.printStackTrace(System.err)
			}
		}
	}

	fun checkUpdate() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			val i = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
			if (i != PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(
					activity,
					arrayOf(
						Manifest.permission.WRITE_EXTERNAL_STORAGE,
						Manifest.permission.READ_EXTERNAL_STORAGE
					),
					REQUEST_CHECK_UPDATE
				)
			} else {
				checkUpdateInternal()
			}
		} else {
			checkUpdateInternal()
		}
	}

	private fun checkUpdateInternal() {
		var cancelled = false
		var tm: Timer? = null
		val updateLayout = View.inflate(activity, R.layout.check_update, null)
		val messageView = updateLayout.findViewById<TextView>(R.id.message)
		val downloadBox = updateLayout.findViewById<LinearLayout>(R.id.download_box)
		val versionTitle = updateLayout.findViewById<TextView>(R.id.version_title)
		val versionDesc = updateLayout.findViewById<TextView>(R.id.version_desc)
		val loading = updateLayout.findViewById<ProgressBar>(R.id.loading)
		val downloadingBox = updateLayout.findViewById<LinearLayout>(R.id.downloading_box)
		val downloading = updateLayout.findViewById<ProgressBar>(R.id.downloading)
		val upgradeButton = updateLayout.findViewById<Button>(R.id.upgrade_button)
		val pkgInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
		val dialog = AlertDialog.Builder(activity, R.style.dialogStyle)
			.setView(updateLayout)
			.setCancelable(true)
			.create()

		fun showState(status: Int, savedSize: Long, totalSize: Long) {
			try {
				when (status) {
					DownloadManager.STATUS_SUCCESSFUL -> {
						downloading.progress = 100
						downloading.max = 100
						handler.postDelayed({
							dialog.cancel()
						}, 200)
					}
					DownloadManager.STATUS_FAILED -> {
						app.toast("下载失败！")
						dialog.cancel()
					}
					else -> {
						downloading.progress = ((savedSize / totalSize.toDouble()) * 100).toInt()
						downloading.max = 100
					}
				}
			} catch (e: Throwable) {
				e.printStackTrace(System.err)
			}
		}

		if (this.downloadId == null) {
			queryVersion(success = { appVersion ->
				if (!cancelled) {
					@Suppress("DEPRECATION")
					if (appVersion.code > pkgInfo.versionCode) {
						downloadBox.visibility = View.VISIBLE
						versionTitle.text = "发现新版本：${appVersion.version}"
						versionDesc.text = appVersion.description
						upgradeButton.setOnClickListener {
							download(
								appVersion.download,
								appVersion.version,
								"EReader-${appVersion.version}.apk"
							) { downloadId ->
								if (downloadId == null) {
									this.downloadId = null
									app.toast("下载失败！")
									dialog.cancel()
								} else {
									this.downloadId = downloadId
									downloadingBox.visibility = View.VISIBLE
									downloadBox.visibility = View.GONE
									tm = queryDownloadTimer(downloadId) { status, savedSize, totalSize ->
										showState(status, savedSize, totalSize)
									}
								}
							}
						}
					} else {
						messageView.text = activity.getString(R.string.upToDate)
						messageView.visibility = View.VISIBLE
					}
					loading.visibility = View.GONE
				}
			}, error = { msg ->
				if (cancelled) {
					messageView.text = msg
					messageView.visibility = View.VISIBLE
					loading.visibility = View.GONE
				}
			})
		} else {
			downloadingBox.visibility = View.VISIBLE
			downloadBox.visibility = View.GONE
			loading.visibility = View.GONE
			tm = queryDownloadTimer(this.downloadId!!) { status, savedSize, totalSize ->
				showState(status, savedSize, totalSize)
			}
		}


		dialog.setOnCancelListener {
			cancelled = true
			tm?.cancel()
			tm = null
		}
		dialog.show()
	}
}
