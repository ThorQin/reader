package com.github.thorqin.reader

class Chapter {
    var name = ""
    var pages = 0
    var startPoint = 0
    var endPoint = 0
}

class FileConfig {
    var initialized = false
    var name = ""
    var totalPages = 0
    var readChapter = 0
    var readPoint = 0
    var chapters = ArrayList<Chapter>()
    var content = ""
}

class AppConfig {
    var files = HashMap<String, FileConfig>()
    var fontSize = 14
    var lastRead :String? = null
}