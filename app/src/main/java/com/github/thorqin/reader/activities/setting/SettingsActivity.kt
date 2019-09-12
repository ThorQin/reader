package com.github.thorqin.reader.activities.setting

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.github.thorqin.reader.R
import kotlinx.android.synthetic.main.settings_activity.*

class SettingsActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.settings_activity)

		setSupportActionBar(toolbar)
		supportActionBar?.title = getString(R.string.settings)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
	}


}
