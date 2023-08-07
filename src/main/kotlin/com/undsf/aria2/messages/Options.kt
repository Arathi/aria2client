package com.undsf.aria2.messages

class Options(
    var dir: String? = null,
    var out: String? = null,
    var proxy: String? = null,
) {
    fun toMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (dir != null) map["dir"] = dir!!
        if (out != null) map["out"] = out!!
        if (proxy != null) map["all-proxy"] = proxy!!
        return map
    }
}