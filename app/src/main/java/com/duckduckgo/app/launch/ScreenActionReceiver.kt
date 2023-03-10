/*
 * Copyright (c) 2023 DuckDuckGo
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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.duckduckgo.app.browser.BrowserActivity

class ScreenActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        fun openMainActivity() {
            context.startActivity(Intent(context, LaunchBridgeActivity::class.java)) //.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
            if (BrowserActivity.goHome) {
                context.startActivity(Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) })
            }
        }
        val action = intent.action

        if (Intent.ACTION_SCREEN_ON == action) {
            openMainActivity()
        }

        if (Intent.ACTION_SCREEN_OFF == action) {
            openMainActivity()
        }

        if (Intent.ACTION_USER_PRESENT == action) {
            openMainActivity()
        }
    }

    fun getFilter(): IntentFilter {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        return filter
    }
}
