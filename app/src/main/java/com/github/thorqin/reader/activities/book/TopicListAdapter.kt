package com.github.thorqin.reader.activities.book

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.github.thorqin.reader.App
import com.github.thorqin.reader.R

class TopicListAdapter(val context: Context, var chapters: ArrayList<App.Chapter>): BaseAdapter() {

	fun update(chapters: ArrayList<App.Chapter>) {
		this.chapters = chapters
		notifyDataSetChanged()
	}

	var onSelectTopic: ((chapterIndex: Int) -> Unit)? = null
	private val inflater: LayoutInflater = LayoutInflater.from(context)

	override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
		val item = chapters!![position]
		var view: TextView = if (convertView != null) {
			if (convertView.tag == position) {
				return convertView
			} else {
				convertView as TextView
			}
		} else {
			inflater.inflate(R.layout.topic_list_item, null) as TextView
		}
		view.tag = position
		view.text = item.name
		view.setOnClickListener {
			onSelectTopic?.invoke(it.tag as Int)
		}

		return view
	}

	override fun getItem(position: Int): Any {
		return chapters!![position]
	}

	override fun getItemId(position: Int): Long {
		return position.toLong()
	}

	override fun getCount(): Int {
		return chapters.size
	}

}