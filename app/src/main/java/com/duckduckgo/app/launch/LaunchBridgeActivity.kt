/*
 * Copyright (c) 2018 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.launch

import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.BrowserActivity
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DuckDuckGoActivity
import com.duckduckgo.app.onboarding.ui.OnboardingActivity
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.di.scopes.ActivityScope
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import kotlin.concurrent.timerTask

@InjectWith(ActivityScope::class)
class LaunchBridgeActivity : DuckDuckGoActivity() {
    // drw
    private lateinit var btnStart: Button
    private lateinit var btnBack: Button
    private lateinit var am: AudioManager
    private lateinit var txt: TextView
    private var pin: String = ""

    @Inject
    lateinit var variantManager: VariantManager

    private val viewModel: LaunchViewModel by bindViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_launch)

        // drw
        val screenActionReceiver = ScreenActionReceiver()
        registerReceiver(screenActionReceiver, screenActionReceiver.getFilter())
        am = getSystemService(AUDIO_SERVICE) as AudioManager
        // am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_VIBRATE)
        txt = findViewById(R.id.textView)

        txt.setOnClickListener {
            txt.text = "Loading ..."
            pin = ""
            findViewById<Button>(R.id.btn5).setTextColor(Color.parseColor("#FFFFFF"))
//            Handler().postDelayed(findViewById<Button>(R.id.btn5).setTextColor(Color.parseColor(R.color.white.toString())),1000)
            Timer().schedule(timerTask {
                findViewById<Button>(R.id.btn5).setTextColor(Color.parseColor("#80353535"))
            },1000)
            // Timer().schedule(1000) {
            //     findViewById<Button>(R.id.btn5).setTextColor(Color.parseColor("#80353535"))
            // }
        }

        btnStart = findViewById(R.id.btnStart)
        btnStart.setOnClickListener{ openNewActivity() }
        btnBack = findViewById(R.id.btnBack)
        btnBack.setOnClickListener {
            if(am.getStreamVolume(AudioManager.STREAM_NOTIFICATION) == 0) {
                if (!isTaskRoot) {
                    am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION) - 4, AudioManager.FLAG_SHOW_UI)
                    finish()
                }
            }
        }

        configureObservers()

        lifecycleScope.launch { viewModel.determineViewToShow() }
    }

    fun btnClicked(view: View) {
        when (view.id) {
            R.id.btn0 -> setPin("0")
            R.id.btn1 -> setPin("1")
            R.id.btn2 -> setPin("2")
            R.id.btn3 -> setPin("3")
            R.id.btn4 -> setPin("4")
            R.id.btn5 -> setPin("5")
            R.id.btn6 -> setPin("6")
            R.id.btn7 -> setPin("7")
            R.id.btn8 -> setPin("8")
            R.id.btn9 -> setPin("9")
        }
    }

    private fun setPin(p: String) {
        pin += p
        // Log.d("pin", pin)
        Timber.i(pin)
        when (pin.length) {
            0 -> txt.text = "Loading ..."
            1 -> txt.text = "Loading ...."
            2 -> txt.text = "Loading ....."
            3 -> txt.text = "Loading ......"
            else -> {
                txt.text = "Loading ....... "
                val now = Calendar.getInstance()
                val h = now.get(Calendar.HOUR_OF_DAY)
                val m = now.get(Calendar.MINUTE) + 1
                val pwd = StringBuilder().append(h.toString().padStart(2,'0')).append(m.toString().padStart(2,'0')).toString()
                val pwd2 = StringBuilder().append(h.plus(1).toString().padStart(2,'0')).append(m.toString().padStart(2,'0')).toString()
//                Log.d("pin", pin)
                Timber.i(pin)
//                Log.d("pwd", pwd)
                Timber.i(pwd)
                if (pwd2 == pin) {
                    BrowserActivity.goHome = false
                }
                if (pwd == pin || pwd2 == pin) {
                    if (!isTaskRoot) {
                        finish()
                    } else {
                        am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, AudioManager.FLAG_SHOW_UI)
                        openNewActivity()
                        // showHome()
                    }
                }
                pin = ""
                txt.text = "Loading ..."
            }
        }
    }

    private fun openNewActivity(){
        val volLevel: Int = am.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
//        val proximity = sens.getDefaultSensor(Sensor.TYPE_PROXIMITY) ?: return

        if (volLevel == 0) {
            am.setStreamVolume(AudioManager.STREAM_NOTIFICATION, am.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION) - 4, AudioManager.FLAG_SHOW_UI)
            // startActivity(Intent(this, BrowserActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            showHome()
        }
    }

    override fun onBackPressed() {
        // super.onBackPressed()
    }

    private fun configureObservers() {
        viewModel.command.observe(this) {
            processCommand(it)
        }
    }

    private fun processCommand(it: LaunchViewModel.Command) {
        when (it) {
            LaunchViewModel.Command.Onboarding -> {
                // showOnboarding()
            }
            is LaunchViewModel.Command.Home -> {
                // showHome()
            }
        }
    }

    private fun showOnboarding() {
        startActivity(OnboardingActivity.intent(this))
        finish()
    }

    private fun showHome() {
        startActivity(BrowserActivity.intent(this).addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        overridePendingTransition(0, 0)
        finish()
    }
}
