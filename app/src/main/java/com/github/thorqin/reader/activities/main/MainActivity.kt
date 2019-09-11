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
import com.github.thorqin.reader.activities.book.BookActivity
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList
import java.text.Collator


class MainActivity : AppCompatActivity() {

	companion object {
		val TEXT_FILE = Regex(".+\\.txt$", RegexOption.IGNORE_CASE)
		const val MIN_FILE_SIZE = 1024 * 200

	}

	private val app: App get () {
		return application as App
	}

	private var searching = false
	private var scanFile: String = ""
	private lateinit var bookAdapter: BookListAdapter

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
			startActivityForResult(intent, 2)
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


		showFiles()
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
				app.clearBook()
				showFiles()
				true
			}
			else -> super.onOptionsItemSelected(item)
		}
	}


	private fun searchPath(path: File, found: ArrayList<File>, level: Int) {
		val files = path.listFiles() ?: return
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
		val found = ArrayList<File>()
		val thread = Thread {
			try {
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
		AlertDialog.Builder(this).setTitle(getString(R.string.no_storage_permission))
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
		if (requestCode == 1) {
			searchBooks()
		} else if (requestCode == 2) {
			showFiles()
			app.config.lastRead = null
			app.saveConfig()
		}
	}

}
