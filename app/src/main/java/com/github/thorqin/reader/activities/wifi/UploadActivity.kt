package com.github.thorqin.reader.activities.wifi

import android.content.BroadcastReceiver
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import com.github.thorqin.reader.R
import android.net.wifi.WifiManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.content.IntentFilter
import kotlinx.android.synthetic.main.activity_upload.*
import kotlinx.android.synthetic.main.settings_activity.toolbar
import java.net.InetAddress
import java.nio.ByteOrder
import java.math.BigInteger


class UploadActivity : AppCompatActivity() {

	private var running = false

	private val wifiStateReceiver = object: BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent?) {
			checkWIFIState()
		}

	}

	override fun onStop() {
		stopWebServer()
		super.onStop()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_upload)

		setSupportActionBar(toolbar)
		supportActionBar?.title = getString(R.string.upload_book)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		init()

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

	private fun checkWIFIState() {
		val connManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
		val networks = connManager.allNetworks
		for (n in networks) {
			val info = connManager.getNetworkInfo(n)
			if (info.type == ConnectivityManager.TYPE_WIFI && info.isConnected) {
				startWebServer()
				return
			}
		}
		stopWebServer()
	}

	private fun startWebServer() {
		if (running) {
			return
		}
		running = true
		println("Web Server Started ...")
		val wifiMgr = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
		if (wifiMgr.wifiState == WifiManager.WIFI_STATE_ENABLED) {

		}
		val wifiInfo = wifiMgr.connectionInfo
		var ip = wifiInfo.ipAddress
		if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
			ip = Integer.reverseBytes(ip)
		}
		val ipByteArray = BigInteger.valueOf(ip.toLong()).toByteArray()
		val ipStr = InetAddress.getByAddress(ipByteArray).hostAddress

		ipText.text = "请用电脑浏览器访问：\nhttp://$ipStr:12345"

	}

	private fun init() {


		val filter = IntentFilter()
		filter.addAction(WifiManager.RSSI_CHANGED_ACTION)
		filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
		this.registerReceiver(wifiStateReceiver, filter)

		checkWIFIState()

	}

	private fun stopWebServer() {
		ipText.text = "请连接到局域网WIFI，以上传图书！"
		if (!running) {
			return
		}
		running = false
	}

}
