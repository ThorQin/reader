package com.github.thorqin.reader.activities.main

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.github.thorqin.reader.App
import com.github.thorqin.reader.R
import com.github.thorqin.reader.utils.getAppInfo
import com.github.thorqin.reader.utils.httpPost
import com.koushikdutta.async.http.AsyncHttpPost


class FeedbackManager(private val activity: MainActivity) {

	private val handler: Handler = Handler(Looper.getMainLooper())

	fun showFeedback() {
		val feedbackLayout = View.inflate(activity, R.layout.feedback_layout, null)
		val input = feedbackLayout.findViewById<EditText>(R.id.feedbackInput)
		val bug = feedbackLayout.findViewById<RadioButton>(R.id.rbBugReport)
		val suggestion = feedbackLayout.findViewById<RadioButton>(R.id.rbSuggestion)
		val dialog = AlertDialog.Builder(activity, R.style.dialogStyle)
			.setView(feedbackLayout)
			.setCancelable(true)
			.setPositiveButton(activity.getString(R.string.ok), null)
			.create()
		dialog.setOnShowListener {
			it as AlertDialog
			val button = it.getButton(AlertDialog.BUTTON_POSITIVE)
			button.setOnClickListener {
				if (input.text.trim().isEmpty()) {
					App.toast(activity, "请输入内容描述！", Toast.LENGTH_SHORT)
				} else {
					dialog.dismiss()
					submit(FeedbackRequest(if (bug.isChecked) "bug" else "suggestion", input.text.trim()))
				}
			}
		}
		dialog.show()
	}

	class FeedbackRequest(val type: String, val content: CharSequence)

	private fun submit(content: FeedbackRequest) {
		val loadingLayout = View.inflate(activity, R.layout.loading, null)
		val dialog = AlertDialog.Builder(activity, R.style.loadingDialogStyle)
			.setView(loadingLayout)
			.setCancelable(false)
			.create()
		val dialogWindow = dialog.window
		val attrs = dialogWindow?.attributes
		attrs?.width = 300
		dialogWindow?.attributes = attrs
		// dialogWindow?.setLayout(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
		dialog.show()
		getAppInfo({
			appInfo ->
			if (appInfo?.webSite == null) {
				activity.runOnUiThread {
					App.toast(activity, "网络错误！")
					dialog.dismiss()
				}
			} else {
				httpPost(appInfo.webSite, content, Unit::class.java, {
					activity.runOnUiThread {
						App.toast(activity, "感谢您的反馈！")
					}
					dialog.dismiss()
				}, {
					activity.runOnUiThread {
						App.toast(activity, "错误：$it")
						dialog.dismiss()
					}
				})
			}
		}, {
			errMsg ->
			activity.runOnUiThread {
				App.toast(activity, errMsg)
				dialog.dismiss()
			}
		})
	}
}
