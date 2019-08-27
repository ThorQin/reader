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
import android.view.View
import android.view.View.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import java.io.File
import java.lang.ref.WeakReference
import java.util.*
import kotlin.collections.ArrayList

var TEXT_FILE = Regex(".+\\.txt$", RegexOption.IGNORE_CASE)

const val MIN_FILE_SIZE = 1024 * 200

class MainActivity : AppCompatActivity() {

	private var listView: ListView? = null
	private var searchButton: View? = null
	private var loadingBar: LinearLayout? = null
	private var loadingStatus: TextView? = null
	private var config: AppConfig? = null
	private var searching = false

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		requestWindowFeature(Window.FEATURE_NO_TITLE)
		setContentView(R.layout.activity_main)

		listView = findViewById<ListView>(R.id.fileList)
		searchButton = findViewById<View>(R.id.searchButton)
		loadingBar = findViewById<LinearLayout>(R.id.loading_bar)
		loadingStatus = findViewById<TextView>(R.id.loading_status)

		searchButton!!.setOnClickListener {
			searchBooks()
		}

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
			else -> super.onOptionsItemSelected(item)
		}
	}


	private fun searchPath(path: File, found: ArrayList<File>, level: Int, scanFile: Array<String>) {
		var files = path.listFiles() ?: return
		for (f in files) {
			scanFile[0] = f.absolutePath
			if (f.isDirectory) {
				if (level < 4)
					searchPath(f, found, level + 1, scanFile)
			} else if (TEXT_FILE.matches(f.name)) {
				if (f.length() >= MIN_FILE_SIZE)
					found.add(f)
			}
		}
	}

	private fun searchBooksInternal() {
		if (searching) return
		searching = true
		listView?.visibility = GONE
		searchButton?.visibility = GONE
		loadingBar?.visibility = VISIBLE

		var scanFile = arrayOf("")
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
					searchPath(p, found, 0, scanFile)
				}
				found.forEach {
					if (!config!!.files.containsKey(it.absolutePath)) {
						var fc = FileConfig()
						fc.initialized = false
						fc.path = it.absolutePath
						fc.name = it.nameWithoutExtension
						fc.totalLength = it.length()
						config!!.files[it.absolutePath] = fc
					} else {
						var fc = config!!.files[it.absolutePath]
						if (fc!!.totalLength != it.length()) {
							var fc = FileConfig()
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
					showSearchFile(scanFile[0].substring(rootPathLength))
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
			listView?.visibility = GONE
			searchButton?.visibility = VISIBLE
		} else {
			listView?.visibility = VISIBLE
			searchButton?.visibility = GONE

			var list = mutableListOf<FileConfig>()
			if (config != null) {
				for (m in config!!.files.entries) {
					list.add(m.value)
				}
			}
			listView?.adapter = BookListAdapter(this, list)
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
