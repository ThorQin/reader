package com.github.thorqin.reader.activities.setting

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.github.thorqin.reader.App
import com.github.thorqin.reader.R
import kotlinx.android.synthetic.main.settings_activity.*

class SettingsActivity : AppCompatActivity() {

	private val app: App
		get() {
			return application as App
		}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.settings_activity)

		setSupportActionBar(toolbar)
		supportActionBar?.title = getString(R.string.settings)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)

		// Click to flip
		clickToFlipSwitch.isChecked = app.config.clickToFlip
		clickToFlipItem.setOnClickListener {
			clickToFlipSwitch.toggle()
			app.config.clickToFlip = clickToFlipSwitch.isChecked
			app.saveConfig()
		}
		clickToFlipSwitch.setOnClickListener {
			app.config.clickToFlip = clickToFlipSwitch.isChecked
			app.saveConfig()
		}

		// Never lock screen
		screenLockSwitch.isChecked = app.config.neverLock
		screenLockItem.setOnClickListener {
			screenLockSwitch.toggle()
			app.config.neverLock = screenLockSwitch.isChecked
			app.saveConfig()
		}
		screenLockSwitch.setOnClickListener {
			app.config.neverLock = screenLockSwitch.isChecked
			app.saveConfig()
		}

	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		return when (item.itemId) {
			android.R.id.home -> {
				finish()
				true
			}
			else -> super.onOptionsItemSelected(item)
		}
	}

}
