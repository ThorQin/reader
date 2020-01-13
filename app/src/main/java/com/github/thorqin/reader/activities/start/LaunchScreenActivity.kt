package com.github.thorqin.reader.activities.start

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.github.thorqin.reader.activities.main.MainActivity

class LaunchScreenActivity : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		val it = Intent(this, MainActivity::class.java)
		if (intent?.data?.host == "community") {
			it.putExtra("openView", "community")
		}
		this.startActivity(it)
		this.finish()
		//this.overridePendingTransition(R.anim.right_in, R.anim.left_out)
	}
}
