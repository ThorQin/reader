package com.github.thorqin.reader

import org.junit.Test

class AppTest {

	@Test
	fun testRegex() {
		val reg = Regex("\\s+")
		println(reg.matches("  \n  "))
		println(App.TOPIC_RULE.replace(reg, ""))
	}

	@Test
	fun testLoop() {
		for (i in 0..5) {
			println(i)
		}

		for (i in (0..5).reversed()) {
			println(i)
		}
	}
}
