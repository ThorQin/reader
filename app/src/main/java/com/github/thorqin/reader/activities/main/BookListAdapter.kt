package com.github.thorqin.reader.activities.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.chauthai.swipereveallayout.SwipeRevealLayout
import com.github.thorqin.reader.App
import com.github.thorqin.reader.R
import com.github.thorqin.reader.activities.book.BookActivity
import java.io.File


class BookListAdapter(
	private val context: Context,
	private val onDeleteItem: (key: String) -> Unit
) : BaseAdapter() {

	private var openedView: SwipeRevealLayout? = null
	private val swipeListener = object:SwipeRevealLayout.SwipeListener {
		override fun onClosed(view: SwipeRevealLayout?) {
			if (openedView == view) {
				openedView = null
			}
		}

		override fun onSlide(view: SwipeRevealLayout?, slideOffset: Float) {

		}

		override fun onOpened(view: SwipeRevealLayout?) {
			if (openedView != null && openedView != view) {
				openedView!!.close(true)
			}
			openedView = view
		}
	}
	private val clickListener = View.OnClickListener {
		context as MainActivity
		val item = it.tag as App.FileSummary

		if (!File(item.path).exists()) {
			App.askbox(context, context.getString(R.string.file_not_exists), null, {
				onDeleteItem(item.key)
			}, null)
			return@OnClickListener
		}
		val intent = Intent(context, BookActivity::class.java)
		intent.putExtra("key", item.key)
		context.startActivityForResult(intent, 2)
//		context.overridePendingTransition(R.anim.right_in,R.anim.left_out)
	}
	private var data: List<App.FileSummary>? = null
	private val inflater: LayoutInflater = LayoutInflater.from(context)

	fun update(data: List<App.FileSummary>) {
		this.data = data
		this.notifyDataSetChanged()
	}

	fun close() {
		if (openedView != null) {
			openedView!!.close(false)
		}
	}

	@SuppressLint("InflateParams")
	override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
		val item = data!![position]
		val view: SwipeRevealLayout = if (convertView != null) {
			if (convertView.tag == item.key) {
				convertView as SwipeRevealLayout
			} else {
				inflater.inflate(R.layout.book_item, null) as SwipeRevealLayout
			}
		} else {
			inflater.inflate(R.layout.book_item, null) as SwipeRevealLayout
		}
//		val view = inflater.inflate(R.layout.book_item, null) as SwipeRevealLayout
		view.close(false)
		view.setSwipeListener(swipeListener)
		val bookItem = view.findViewById(R.id.bookItem) as View
		bookItem.tag = item
		bookItem.setOnClickListener(clickListener)
		val nameText = view.findViewById(R.id.book_name) as TextView
		nameText.text = item.name
		val descText = view.findViewById(R.id.reading_progress) as TextView
		descText.text = item.desc
		val btnDelete = view.findViewById(R.id.delete_book) as View
		btnDelete.setOnClickListener {
			view.close(true)
			onDeleteItem(item.key)
		}
		view.tag = item.key
//		view.visibility = View.GONE
//		view.visibility = View.VISIBLE
//			view.requestLayout()
		return view
	}

	override fun getItem(position: Int): Any {
		return data!![position]
	}

	override fun getItemId(position: Int): Long {
		return position.toLong()
	}

	override fun getCount(): Int {
		if (data == null) {
			return 0
		}
		return data!!.size
	}

}
