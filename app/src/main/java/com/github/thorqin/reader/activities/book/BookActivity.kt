package com.github.thorqin.reader.activities.book

import android.animation.Animator
import android.os.Bundle
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.github.thorqin.reader.R
import kotlinx.android.synthetic.main.activity_book.*
import android.animation.ObjectAnimator
import com.github.thorqin.reader.App


class BookActivity : AppCompatActivity() {

	enum class Direction(var value: Int) {
		LEFT(0),
		RIGHT(1)
	}

	private var boxWidth: Float = 0F
	private var startX : Float? = null
	private var viewX: Float? = null
	private var v: View? = null
	private var moveDirection: Direction = Direction.LEFT


	private val app: App
		get () {
			return application as App
		}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_book)
		setSupportActionBar(toolbar)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)

		flipper.addOnLayoutChangeListener {
			v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
			boxWidth = flipper.measuredWidth.toFloat()
			println("boxWidth: $boxWidth")
			setPos()
		}

		flipper.setOnTouchListener { _, event ->
			when (event.action ) {
				MotionEvent.ACTION_DOWN -> {
					startX = event.rawX
					true
				}
				MotionEvent.ACTION_MOVE -> {
					if (startX != null) {
						if (v == null) {
							if (event.rawX > startX!! + 10f) {
								moveDirection = Direction.RIGHT
								v = flipper.getChildAt(2)
							} else if (event.rawX < startX!! - 10f) {
								moveDirection = Direction.LEFT
								v = flipper.getChildAt(1)
							}
							viewX = v?.translationX
						}
						if (v != null) {
							when (moveDirection) {
								Direction.RIGHT -> {
									if (event.rawX > startX!!) {
										v!!.translationX = viewX!! + event.rawX - startX!!
									}
								}
								else -> {
									if (event.rawX < startX!!) {
										v!!.translationX = viewX!! + event.rawX - startX!!
									}
								}
							}
						}
						true
					} else {
						true
					}
				}
				MotionEvent.ACTION_UP -> {
					val diff = boxWidth / 8
					if (v != null) {
						when (moveDirection) {
							Direction.RIGHT -> {
								if (event.rawX > startX!! + diff) {
									val toPos = 0f
									moveViewTo(v!!, toPos) {
										var bottomView = flipper.getChildAt(0)
										flipper.removeViewAt(0)
										flipper.addView(bottomView)
										setPos()
									}
								} else {
									val toPos = -boxWidth
									moveViewTo(v!!, toPos) {
										// Do nothing
									}
								}
							}
							else -> {
								if (event.rawX < startX!! - diff) {
									moveViewTo(v!!, -boxWidth) {
										var bottomView = flipper.getChildAt(2)
										flipper.removeViewAt(2)
										flipper.addView(bottomView, 0)
										setPos()
									}
								} else {
									moveViewTo(v!!, 0f) {
										// Do nothing
									}
								}
							}
						}
					}
					startX = null
					viewX = null
					v = null
					true
				}
				else -> false
			}
		}

		openBook()
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

	private fun moveViewTo(v: View, pos: Float, onEndCallback: () -> Unit) {
		val transAnim = ObjectAnimator.ofFloat(v, "translationX", pos)
		transAnim.duration = 100
		transAnim.addListener( object: Animator.AnimatorListener {
			override fun onAnimationRepeat(animation: Animator?) {}
			override fun onAnimationEnd(animation: Animator?) {
				onEndCallback()
			}
			override fun onAnimationCancel(animation: Animator?) {}
			override fun onAnimationStart(animation: Animator?) {}
		})

		transAnim.start()
	}

	private fun setPos() {
		flipper.getChildAt(2).translationX = -boxWidth
		flipper.getChildAt(1).translationX = 0f
		flipper.getChildAt(0).translationX = 0f
	}

	private fun openBook() {
		val key = intent.getStringExtra("key")
		val fileInfo = app.config.files[key] as App.FileSummary
		if (fileInfo.initialized) {

		} else {

		}
	}

}
