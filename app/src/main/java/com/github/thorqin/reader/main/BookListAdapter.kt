package com.github.thorqin.reader.main

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.chauthai.swipereveallayout.SwipeRevealLayout
import com.github.thorqin.reader.R
import com.github.thorqin.reader.entity.FileSummary




class BookListAdapter(
	val context: Context,
	private val onDeleteItem: (path: String) -> Unit
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
		context.startActivity(Intent(context, Main2Activity::class.java))
	}
	private var data: List<FileSummary>? = null
	private val inflater: LayoutInflater = LayoutInflater.from(context)

	fun update(data: List<FileSummary>) {
		this.data = data
		this.notifyDataSetChanged()
	}

	fun close() {
		if (openedView != null) {
			openedView!!.close(false)
		}
	}

	override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
		var view: SwipeRevealLayout = if (convertView != null) {
			if (convertView.tag == data!![position].path) {
				return convertView as SwipeRevealLayout
			} else {
				convertView as SwipeRevealLayout
			}
		} else {
			inflater.inflate(R.layout.book_item, null) as SwipeRevealLayout
		}
		view.close(false)
		view.setSwipeListener(swipeListener)
		val bookItem = view.findViewById(R.id.bookItem) as View
		bookItem.setOnClickListener(clickListener)
		val nameText = view.findViewById(R.id.book_name) as TextView
		nameText.text = data!![position].name
		val descText = view.findViewById(R.id.reading_progress) as TextView
		descText.text = data!![position].desc
		val btnDelete = view.findViewById(R.id.delete_book) as View
		btnDelete.setOnClickListener {
			onDeleteItem(data!![position].path)
		}
		view.tag = data!![position].path
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
