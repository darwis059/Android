package com.duckduckgo.app.browser.speak

import android.webkit.JavascriptInterface
import android.content.Context
import android.content.Intent

class SpeakJavascriptInterface(private val speakText: (text: String) -> Unit) {

    @JavascriptInterface
    fun speak(content: String) {
        speakText(content)
        // val i = Intent(Intent.ACTION_SEND)
        // i.setClassName("hesoft.T2S", "hesoft.T2S.share2speak.ShareSpeakActivity")
        // i.type = "text/plain"
        // i.addCategory(Intent.CATEGORY_DEFAULT)
        // i.putExtra(Intent.EXTRA_TEXT, content)
        // i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // ctx.startActivity(i)
    }

    companion object {
        const val JAVASCRIPT_INTERFACE_NAME = "Android"
    }
}
