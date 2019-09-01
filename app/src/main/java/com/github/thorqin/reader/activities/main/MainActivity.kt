package com.github.thorqin.reader.activities.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import android.view.Window
import android.view.Menu
import android.view.MenuItem
import android.view.View.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.github.thorqin.reader.App
import com.github.thorqin.reader.R
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.*
import kotlin.collections.ArrayList


class MainActivity : AppCompatActivity() {

	companion object {
		val TEXT_FILE = Regex(".+\\.txt$", RegexOption.IGNORE_CASE)
		const val MIN_FILE_SIZE = 1024 * 200

	}

	private val app: App get () {
		return application as App
	}

	private var searching = false
	private val scanFile = arrayOf("")
	private lateinit var bookAdapter: BookListAdapter

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
		setSupportActionBar(toolbar)

		searchButton.setOnClickListener {
			searchBooks()
		}

		bookAdapter = BookListAdapter(this) {
			app.config.files.remove(it)
			app.saveConfig()
			showFiles()
		}

		fileList.adapter = bookAdapter

		showFiles()

//		this.setTheme(R.style.MainActivityTheme)
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
				app.config.files.clear()
				app.saveConfig()
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
		fileList.visibility = GONE
		buttonBar.visibility = GONE
		loadingBar.visibility = VISIBLE


		var rootPathLength = 0
		val timer = Timer()
		val thread = Thread {
			try {
				app.config.files.forEach {
					if (!File(it.key).exists()) {
						app.config.files.remove(it.key)
					}
				}

				var found = ArrayList<File>()
				var p = Environment.getExternalStorageDirectory()
				rootPathLength = p.absolutePath.length
				if (p.isDirectory) {
					searchPath(p, found, 0)
				}
				found.forEach {
					var fc = app.config.files[it.absolutePath]
					if (fc == null) {
						fc = App.FileSummary()
						fc.initialized = false
						fc.path = it.absolutePath
						fc.name = it.nameWithoutExtension
						fc.totalLength = it.length()
						app.config.files[it.absolutePath] = fc
					} else {
						if (fc.totalLength != it.length()) {
							fc = App.FileSummary()
							fc.initialized = false
							fc.path = it.absolutePath
							fc.name = it.nameWithoutExtension
							fc.totalLength = it.length()
							app.config.files[it.absolutePath] = fc
						}
					}
				}
				app.saveConfig()
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
					val file = getScanFile().substring(rootPathLength)
					if (file != null)
						loadingStatus.text = file
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
			list.sortBy {
				it.lastReadTime
			}

			bookAdapter.close()
			bookAdapter.update(list)

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

	private fun showOpenAppSetting() {
		AlertDialog.Builder(this).setTitle("存储权限不可用")
			.setMessage("请在-应用设置-权限-中，允许读取存储权限来查找和打开图书")
			.setPositiveButton("立即开启") { _, _ ->
				openSetting()
			}.setCancelable(true).show()
	}

	private fun openSetting() {
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
