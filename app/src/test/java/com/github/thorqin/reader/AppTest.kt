package com.github.thorqin.reader

import org.junit.Test

class AppTest {

	@Test
	fun testRegex() {
		val reg = Regex("\\s+")
		println(reg.matches("  \n  "))
		println(App.TOPIC_RULE.replace(reg, ""))
	}

}
