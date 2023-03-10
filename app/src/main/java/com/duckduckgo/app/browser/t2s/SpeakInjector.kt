package com.duckduckgo.app.browser.speak

import android.webkit.WebView
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import android.content.Context

interface SpeakInjector {
    fun addJsInterface(
        webView: WebView,
        speakText: (text: String) -> Unit,
        // ctx: Context,
    )

    // fun injectPrint(
    //     webView: WebView,
    // )
}

@ContributesBinding(AppScope::class)
class SpeakInjectorJS @Inject constructor() : SpeakInjector {
    override fun addJsInterface(
        webView: WebView,
        speakText: (text: String) -> Unit,
        // ctx: Context,
    ) {
        webView.addJavascriptInterface(SpeakJavascriptInterface(speakText), SpeakJavascriptInterface.JAVASCRIPT_INTERFACE_NAME)
    }

    // override fun injectPrint(webView: WebView) {
    //     webView.loadUrl("javascript:window.print = function() { ${PrintJavascriptInterface.JAVASCRIPT_INTERFACE_NAME}.print() }")
    // }
}
