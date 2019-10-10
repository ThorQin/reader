package com.github.thorqin.reader.activities.main

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import android.view.Window
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.View.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.view.MenuCompat
import com.github.thorqin.reader.App
import com.github.thorqin.reader.R
import com.github.thorqin.reader.activities.book.BookActivity
import com.github.thorqin.reader.activities.wifi.UploadActivity
import com.github.thorqin.reader.utils.json
import com.koushikdutta.async.ByteBufferList
import com.koushikdutta.async.http.AsyncHttpClient
import com.koushikdutta.async.http.AsyncHttpGet
import com.koushikdutta.async.http.AsyncHttpResponse
import kotlinx.android.synthetic.main.activity_main.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.Exception
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.ArrayList
import java.text.Collator
import java.util.regex.Pattern
import kotlin.concurrent.timer


const val REQUEST_OPEN_SETTING = 1
const val REQUEST_OPEN_BOOK = 2
const val REQUEST_OPEN_UPLOAD = 3

class MainActivity : AppCompatActivity() {

	companion object {
		val TEXT_FILE = Regex(".+\\.(txt|epub)$", RegexOption.IGNORE_CASE)
		const val MIN_FILE_SIZE = 1024 * 200

	}

	private val app: App get () {
		return application as App
	}

	private var searching = false
	private var scanFile: String = ""
	private lateinit var bookAdapter: BookListAdapter


	private val downloadManagerReceiver = object: BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			if (intent?.action.equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
				val downId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)

				//downId
			}
		}
	}


	private fun setScanFile(f: String) {
		synchronized(scanFile) {
			scanFile = f
		}
	}
	private fun getScanFile(): String {
		lateinit var result: String
		synchronized(scanFile) {
			result = scanFile
		}
		return result
	}


	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		if (app.config.lastRead != null && app.config.files.containsKey(app.config.lastRead!!)) {
			val intent = Intent(this, BookActivity::class.java)
			intent.putExtra("key", app.config.lastRead)
			startActivityForResult(intent, REQUEST_OPEN_BOOK)
		}

		requestWindowFeature(Window.FEATURE_NO_TITLE)
		setContentView(R.layout.activity_main)
		setSupportActionBar(toolbar)

		searchButton.setOnClickListener {
			searchBooks()
		}

		bookAdapter = BookListAdapter(this) {
			app.removeBook(it)
			showFiles()
		}

		fileList.adapter = bookAdapter


		registerReceiver(downloadManagerReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE ))
		showFiles()
	}

	override fun onDestroy() {
		super.onDestroy()
		unregisterReceiver(downloadManagerReceiver)
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		val inflater = menuInflater
		inflater.inflate(R.menu.activity_main, menu)
		MenuCompat.setGroupDividerEnabled(menu, true)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.search_book -> {
				searchBooks()
				true
			}
			R.id.empty_book -> {
				clearBooks()
				true
			}
			R.id.wifi_upload -> {
				showUpload()
				true
			}
			R.id.about -> {
				showAbout()
				true
			}
			R.id.check_update -> {
				checkUpdate()
				true
			}
			else -> super.onOptionsItemSelected(item)
		}
	}


	private val pathPattern = Pattern.compile("^[-0-9a-z]{32,}$", Pattern.CASE_INSENSITIVE)
	private fun searchPath(path: File, found: ArrayList<File>, level: Int) {
		val files = path.listFiles() ?: return
		for (f in files) {
			setScanFile(f.absolutePath)
			if (f.isDirectory) {
				if (pathPattern.matcher(f.name).matches()) {
					// println(f.name)
				} else if (level < 4) {
					searchPath(f, found, level + 1)
				}
			} else if (TEXT_FILE.matches(f.name)) {
				if (f.length() >= MIN_FILE_SIZE)
					found.add(f)
			}
		}
	}

	private fun searchBooksInternal() {
		if (searching) return
		searching = true
		fileList.visibility = GONE
		buttonBar.visibility = GONE
		loadingBar.visibility = VISIBLE

		var rootPathLength = 0
		val timer = Timer()
		val found = ArrayList<File>()
		val thread = Thread {
			try {
				@Suppress("DEPRECATION")
				val p = Environment.getExternalStorageDirectory()
				rootPathLength = p.absolutePath.length
				if (p.isDirectory) {
					searchPath(p, found, 0)
				}
			} catch (e: Exception) {
				System.err.println("Search error: ${e.message}")
			} finally {
				timer.cancel()
				searching = false
				runOnUiThread {
					found.forEach {
						val key = App.digest(it.absolutePath)
						var fc = app.config.files[key]
						if (fc == null) {
							fc = App.FileSummary()
							fc.key = key
							fc.path = it.absolutePath
							fc.name = it.nameWithoutExtension
							fc.totalLength = it.length()
							app.config.files[key] = fc
						} else {
							if (fc.totalLength != it.length()) {
								app.removeBook(key)
								fc = App.FileSummary()
								fc.path = it.absolutePath
								fc.name = it.nameWithoutExtension
								fc.totalLength = it.length()
								app.config.files[key] = fc
							}
						}
					}

					val removeKeys = arrayListOf<String>()
					app.config.files.forEach {
						if (!File(it.value.path).exists()) {
							removeKeys.add(it.key)
						}
					}
					for (k in removeKeys)
						app.config.files.remove(k)

					app.saveConfig()
					showFiles()
				}
			}
		}

		timer.schedule(object : TimerTask() {
			override fun run() {
				runOnUiThread {
					var file = getScanFile()
					if (file.length > rootPathLength)
						file = file.substring(rootPathLength)
						loadingStatus.text = file
				}
			}
		}, 0, 50)

		thread.isDaemon = true
		thread.start()

	}

	private fun searchBooks() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			val i = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
			if (i != PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(
					this,
					arrayOf(
						Manifest.permission.WRITE_EXTERNAL_STORAGE,
						Manifest.permission.READ_EXTERNAL_STORAGE
					),
					REQUEST_OPEN_SETTING
				)
			} else {
				searchBooksInternal()
			}
		} else {
			searchBooksInternal()
		}
	}

	private fun showFiles() {
		loadingBar.visibility = GONE
		if (app.config.files.isEmpty()) {
			fileList.visibility = GONE
			buttonBar.visibility = VISIBLE
		} else {
			fileList.visibility = VISIBLE
			buttonBar.visibility = GONE
			val list = mutableListOf<App.FileSummary>()
			for (m in app.config.files.entries) {
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
			bookAdapter.close()
			bookAdapter.update(list)
			fileList.invalidate()
		}
	}

	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<out String>,
		grantResults: IntArray
	) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (requestCode == 1) {
				if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
					val b = shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)
					if (!b) {
						showOpenAppSetting()
					}
				} else {
					searchBooksInternal()
				}
			}
		}
	}

	private fun showOpenAppSetting() {
		AlertDialog.Builder(this, R.style.dialogStyle).setTitle(getString(R.string.no_storage_permission))
			.setMessage(getString(R.string.should_allow_permission))
			.setPositiveButton(getString(R.string.open_now)) { _, _ ->
				openSetting()
			}.setCancelable(true).show()
	}

	private fun openSetting() {
		val intent = Intent()
		intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
		val uri = Uri.fromParts("package", packageName, null)
		intent.data = uri
		startActivityForResult(intent, 1)
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		if (requestCode == REQUEST_OPEN_SETTING) { // SETTING MAYBE CHANGED
			searchBooks()
		} else if (requestCode == REQUEST_OPEN_BOOK) { // BOOK ACTIVITY CLOSED
			showFiles()
			app.config.lastRead = null
			app.saveConfig()
		} else if (requestCode == REQUEST_OPEN_UPLOAD) {
			showFiles()
		}
	}

	private fun showAbout() {
		val pkgInfo = packageManager.getPackageInfo(packageName, 0)
		val versionName = pkgInfo.versionName

		AlertDialog.Builder(this, R.style.dialogStyle).setTitle(getString(R.string.app_name))
			.setMessage(String.format(getString(R.string.about_me), versionName))
			.setCancelable(true).show()
	}

	private fun showUpload() {
		val intent = Intent(this, UploadActivity::class.java)
		startActivityForResult(intent, REQUEST_OPEN_UPLOAD)
	}

	private fun clearBooks() {
		AlertDialog.Builder(this, R.style.dialogStyle).setTitle(getString(R.string.app_name))
			.setMessage(getString(R.string.confirm_to_clear))
			.setPositiveButton(getString(R.string.ok)) { _, _ ->
				app.clearBook()
				showFiles()
		}.setCancelable(true).show()
	}


	class ApkVersion (
		val code: Int,
		val version: String,
		val description: String,
		val download: String
	)

	private fun queryVersion(success: (result: ApkVersion) -> Unit, error: (msg: String) -> Unit) {
		var url = "http://thor.qin.gitee.io/ereader-web/version.json"
		val request = AsyncHttpGet(url)
		AsyncHttpClient.getDefaultInstance().executeByteBufferList(request,
			object: AsyncHttpClient.DownloadCallback() {
				override fun onCompleted(
					e: Exception?,
					source: AsyncHttpResponse?,
					result: ByteBufferList?
				) {
					if (e != null) {
						runOnUiThread {
							error(e.message ?: "网络错误!")
						}
						return
					}
					if (result == null) {
						runOnUiThread {
							error("网络错误!")
						}
						return
					}
					val bytes = result.allByteArray
					val appVersion = json().fromJson(String(bytes, Charset.forName("utf-8")), ApkVersion::class.java) as ApkVersion
					runOnUiThread {
						success(appVersion)
					}
				}
			}
		)
	}

	private fun download(url: String, version: String, callback: (downloadId: Long?) -> Unit) {
		val p = Environment.getExternalStorageDirectory().resolve("Download")
		if (p.isDirectory) {
			val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
			val request = DownloadManager.Request(Uri.parse(url))
			request.setDestinationInExternalPublicDir("Download", "MeiLiShuo.apk");
			request.setTitle("轻阅读");
			request.setDescription("轻阅读：$version")
			request.setMimeType("application/vnd.android.package-archive")
			request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
//			request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
			request.setVisibleInDownloadsUi(true)
			val downloadId = downloadManager.enqueue(request)
			callback(downloadId)
		} else {
			callback(null)
		}
	}

	private fun checkUpdate() {
		var cancelled = false
		var tm: Timer? = null
		val updateLayout = inflate(this, R.layout.check_update, null)
		val upToDateView = updateLayout.findViewById<TextView>(R.id.up_to_date)
		val downloadBox = updateLayout.findViewById<LinearLayout>(R.id.download_box)
		val versionTitle = updateLayout.findViewById<TextView>(R.id.version_title)
		val versionDesc = updateLayout.findViewById<TextView>(R.id.version_desc)
		val loading = updateLayout.findViewById<ProgressBar>(R.id.loading)
		val downloading = updateLayout.findViewById<ProgressBar>(R.id.downloading)
		val upgradeButton = updateLayout.findViewById<Button>(R.id.upgrade_button)
		val pkgInfo = packageManager.getPackageInfo(packageName, 0)
		val dialog = AlertDialog.Builder(this, R.style.dialogStyle)
			.setView(updateLayout)
			.setCancelable(true)
			.create()
		queryVersion( success = { appVersion ->
			if (!cancelled) {
				if (appVersion.code > pkgInfo.versionCode) {
					downloadBox.visibility = VISIBLE
					versionTitle.text = "发现新版本：${appVersion.version}"
					versionDesc.text = appVersion.description
					upgradeButton.setOnClickListener {
						download(appVersion.download, appVersion.version) {downloadId ->
							if (downloadId == null) {
								app.toast("下载失败！")
								dialog.cancel()
							} else {
								val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
								downloading.visibility = VISIBLE
								downloadBox.visibility = GONE
								tm = timer("download", true, 1000, 1000) {
									val query = DownloadManager.Query()
									query.setFilterById(downloadId)
									val cursor = downloadManager.query(query)
									downloading.progress = cursor.position
								}
							}
						}
					}
				} else {
					upToDateView.visibility = VISIBLE
				}
				loading.visibility = GONE
			}
		}, error =  { msg ->
			if (cancelled) {
				upToDateView.visibility = VISIBLE
				loading.visibility = GONE
			}
		})


		dialog.setOnCancelListener {
			cancelled = true
			tm?.cancel()
		}
		dialog.show()
	}
}
