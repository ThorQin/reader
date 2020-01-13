package com.github.thorqin.reader.activities.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.github.thorqin.reader.App
import com.github.thorqin.reader.R
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.lang.Exception
import java.text.Collator
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList

class BookManager(private var activity: MainActivity) {

	companion object {
		val TEXT_FILE = Regex(".+\\.(txt|epub)$", RegexOption.IGNORE_CASE)
		const val MIN_FILE_SIZE = 1024 * 200
	}

	private var bookAdapter: BookListAdapter
	init {
		bookAdapter = BookListAdapter(activity) {

			val rootView = View.inflate(activity, R.layout.delete_book, null)
			val removeButton = rootView.findViewById<Button>(R.id.removeFromList)
			val deleteButton = rootView.findViewById<Button>(R.id.deleteCompletely)
			val dialog = AlertDialog.Builder(activity, R.style.dialogStyle)
				.setView(rootView)
				.setCancelable(true)
				.create()

			removeButton.setOnClickListener { _ ->
				dialog.hide()
				app.removeBook(it)
				showFiles()
			}
			deleteButton.setOnClickListener { _ ->
				dialog.hide()
				app.deleteBook(it)
				showFiles()
			}

			dialog.show()

		}
		activity.fileList.adapter = bookAdapter
	}

	private var searching = false
	private var scanFile: String = ""


	private val app: App
		get() {
			return activity.application as App
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
		activity.fileList.visibility = View.GONE
		activity.buttonBar.visibility = View.GONE
		activity.loadingBar.visibility = View.VISIBLE

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
				activity.runOnUiThread {
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
				activity.runOnUiThread {
					var file = getScanFile()
					if (file.length > rootPathLength)
						file = file.substring(rootPathLength)
					activity.loadingStatus.text = file
				}
			}
		}, 0, 50)

		thread.isDaemon = true
		thread.start()

	}

	fun searchBooks() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			val i = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
			if (i != PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(
					activity,
					arrayOf(
						Manifest.permission.WRITE_EXTERNAL_STORAGE,
						Manifest.permission.READ_EXTERNAL_STORAGE
					),
					REQUEST_SEARCH_BOOK
				)
			} else {
				searchBooksInternal()
			}
		} else {
			searchBooksInternal()
		}
	}

	fun showFiles() {
		activity.loadingBar.visibility = View.GONE
		if (app.config.files.isEmpty()) {
			activity.fileList.visibility = View.GONE
			activity.buttonBar.visibility = View.VISIBLE
		} else {
			activity.fileList.visibility = View.VISIBLE
			activity.buttonBar.visibility = View.GONE
			val list = app.config.getList()
			bookAdapter.close()
			bookAdapter.update(list)
			activity.fileList.invalidate()
		}
	}

	fun clearBooks() {
		AlertDialog.Builder(activity, R.style.dialogStyle).setTitle(activity.getString(R.string.app_name))
			.setMessage(activity.getString(R.string.confirm_to_clear))
			.setPositiveButton(activity.getString(R.string.ok)) { _, _ ->
				app.clearBook()
				showFiles()
			}.setCancelable(true).show()
	}

	fun showOpenAppSetting() {
		AlertDialog.Builder(activity, R.style.dialogStyle)
			.setTitle(activity.getString(R.string.no_storage_permission))
			.setMessage(activity.getString(R.string.should_allow_permission))
			.setPositiveButton(activity.getString(R.string.open_now)) { _, _ ->
				openSetting()
			}.setCancelable(true).show()
	}

	private fun openSetting() {
		val intent = Intent()
		intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
		val uri = Uri.fromParts("package", activity.packageName, null)
		intent.data = uri
		activity.startActivityForResult(intent, REQUEST_SEARCH_BOOK)
	}
}
