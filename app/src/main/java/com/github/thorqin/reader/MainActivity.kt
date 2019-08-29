package com.github.thorqin.reader

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import android.view.Window

import com.google.gson.Gson

import org.apache.commons.io.FileUtils
import java.lang.Exception
import android.view.Menu
import android.view.MenuItem
import android.view.View.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

var TEXT_FILE = Regex(".+\\.txt$", RegexOption.IGNORE_CASE)

const val MIN_FILE_SIZE = 1024 * 200

class MainActivity : AppCompatActivity() {

	private var config: AppConfig? = null
	private var searching = false
	private var scanFile = arrayOf("")
	private var bookAdapter: BookListAdapter? = null

	private fun setScanFile(f: String) {
		synchronized(scanFile) {
			scanFile[0] = f
		}
	}
	private fun getScanFile(): String {
		synchronized(scanFile) {
			return scanFile[0]
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		requestWindowFeature(Window.FEATURE_NO_TITLE)

		setContentView(R.layout.activity_main)

		bookAdapter = BookListAdapter(this) {
			config!!.files.remove(it)
			saveConfig()
			showFiles()
		}

		searchButton!!.setOnClickListener {
			searchBooks()
		}

		fileList?.adapter = bookAdapter

		config = try {

			val configContent = FileUtils.readFileToString(
				application.filesDir.resolve("config.json"), "utf-8"
			)
			val gson = Gson()
			gson.fromJson(configContent, AppConfig::class.java)

		} catch (e: Exception) {
			AppConfig()
		}
		showFiles()
		this.setTheme(R.style.MainActivityTheme)
	}

	private fun saveConfig() {
		try {
			val gson = Gson()
			val content = gson.toJson(config)
			FileUtils.writeStringToFile(application.filesDir.resolve("config.json"), content, "utf-8")
		} catch (e: Exception) {
			System.err.println("Save config failed: " + e.message)
		}
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		val inflater = menuInflater
		inflater.inflate(R.menu.activity_main, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.search_book -> {
				searchBooks()
				true
			}
			R.id.empty_book -> {
				config?.files?.clear()
				saveConfig()
				showFiles()
				true
			}
			else -> super.onOptionsItemSelected(item)
		}
	}


	private fun searchPath(path: File, found: ArrayList<File>, level: Int) {
		var files = path.listFiles() ?: return
		for (f in files) {
			setScanFile(f.absolutePath)
			if (f.isDirectory) {
				if (level < 4)
					searchPath(f, found, level + 1)
			} else if (TEXT_FILE.matches(f.name)) {
				if (f.length() >= MIN_FILE_SIZE)
					found.add(f)
			}
		}
	}

	private fun searchBooksInternal() {
		if (searching) return
		searching = true
		fileList?.visibility = GONE
		searchButton?.visibility = GONE
		loadingBar?.visibility = VISIBLE


		var rootPathLength = 0
		var timer = Timer()
		var thread = Thread {
			try {
				config!!.files.forEach {
					if (!File(it.key).exists()) {
						config!!.files.remove(it.key)
					}
				}

				var found = ArrayList<File>()
				var p = Environment.getExternalStorageDirectory()
				rootPathLength = p.absolutePath.length
				if (p.isDirectory) {
					searchPath(p, found, 0)
				}
				found.forEach {
					if (!config!!.files.containsKey(it.absolutePath)) {
						var fc = FileSummary()
						fc.initialized = false
						fc.path = it.absolutePath
						fc.name = it.nameWithoutExtension
						fc.totalLength = it.length()
						config!!.files[it.absolutePath] = fc
					} else {
						var fc = config!!.files[it.absolutePath]
						if (fc!!.totalLength != it.length()) {
							var fc = FileSummary()
							fc.initialized = false
							fc.path = it.absolutePath
							fc.name = it.nameWithoutExtension
							fc.totalLength = it.length()
							config!!.files[it.absolutePath] = fc
						}
					}
				}
				saveConfig()
			} finally {
				timer.cancel()
				searching = false
				runOnUiThread {
					showFiles()
				}
			}
		}

		timer.schedule(object : TimerTask() {
			override fun run() {
				runOnUiThread {
					showSearchFile(getScanFile().substring(rootPathLength))
				}
			}
		}, 0, 50)

		thread.isDaemon = true
		thread.start()

	}

	private fun searchBooks() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			var i = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
			if (i != PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(
					this,
					arrayOf(
						Manifest.permission.WRITE_EXTERNAL_STORAGE,
						Manifest.permission.READ_EXTERNAL_STORAGE
					),
					1
				)
			} else {
				searchBooksInternal()
			}
		} else {
			searchBooksInternal()
		}
	}

	private fun showFiles() {
		loadingBar?.visibility = GONE
		if (config!!.files.isEmpty()) {
			fileList?.visibility = GONE
			searchButton?.visibility = VISIBLE
		} else {
			fileList?.visibility = VISIBLE
			searchButton?.visibility = GONE

			var list = mutableListOf<FileSummary>()
			if (config != null) {
				for (m in config!!.files.entries) {
					list.add(m.value)
				}
			}

			list.sortBy {
				it.lastReadTime
			}

			bookAdapter?.close()
			bookAdapter?.update(list)

		}
	}

	private fun showSearchFile(file: String?) {
		if (file != null)
			loadingStatus?.text = file
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
					var b = shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)
					if (!b) {
						showOpenAppSetting()
					}
				} else {
					searchBooksInternal()
				}
			}
		}
	}

	fun showOpenAppSetting() {
		AlertDialog.Builder(this).setTitle("存储权限不可用")
			.setMessage("请在-应用设置-权限-中，允许读取存储权限来查找和打开图书")
			.setPositiveButton("立即开启") { _: DialogInterface, _: Int ->
				openSetting()
			}.setCancelable(true).show()
	}

	fun openSetting() {
		var intent = Intent()
		intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
		var uri = Uri.fromParts("package", packageName, null)
		intent.data = uri
		startActivityForResult(intent, 1)
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		if (requestCode == 1) {
			searchBooks()
		}
	}

}
