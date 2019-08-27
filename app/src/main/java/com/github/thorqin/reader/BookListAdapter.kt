package com.github.thorqin.reader

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class BookListAdapter(
	private val context: Context,
	private val data: List<FileConfig>
) : BaseAdapter() {

	val inflater: LayoutInflater = LayoutInflater.from(context)

	override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
		if (convertView != null && convertView.tag == position) {
			return convertView
		}
		val view = inflater.inflate(R.layout.book_item, null)
		val nameText = view.findViewById(R.id.book_name) as TextView
		nameText.text = data[position].name
		val descText = view.findViewById(R.id.reading_progress) as TextView
		descText.text = data[position].desc
		view.tag = position
		return view
	}

	override fun getItem(position: Int): Any {
		return data[position]
	}

	override fun getItemId(position: Int): Long {
		return position.toLong()
	}

	override fun getCount(): Int {
		return data.size
	}

}
