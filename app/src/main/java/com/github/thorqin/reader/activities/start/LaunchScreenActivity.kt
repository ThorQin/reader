package com.github.thorqin.reader.activities.start

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.github.thorqin.reader.App
import com.github.thorqin.reader.R
import com.github.thorqin.reader.activities.book.BookActivity
import com.github.thorqin.reader.activities.main.MainActivity

class LaunchScreenActivity : AppCompatActivity() {
	private val app: App
		get () {
			return application as App
		}
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		this.startActivity(Intent(this, MainActivity::class.java))
		this.finish()
		//this.overridePendingTransition(R.anim.right_in, R.anim.left_out)
	}
}
