/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.impl.di

import android.content.Context
import androidx.room.Room
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.autofill.api.InternalTestUserChecker
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.api.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.autofill.store.ALL_MIGRATIONS
import com.duckduckgo.autofill.store.AutofillDatabase
import com.duckduckgo.autofill.store.InternalTestUserStore
import com.duckduckgo.autofill.store.RealAutofillPrefsStore
import com.duckduckgo.autofill.store.RealInternalTestUserStore
import com.duckduckgo.autofill.store.RealLastUpdatedTimeProvider
import com.duckduckgo.autofill.store.SecureStoreBackedAutofillStore
import com.duckduckgo.autofill.store.feature.AutofillFeatureRepository
import com.duckduckgo.autofill.store.feature.RealAutofillFeatureRepository
import com.duckduckgo.autofill.store.urlmatcher.AutofillDomainNameUrlMatcher
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.securestorage.api.SecureStorage
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope

@Module
@ContributesTo(AppScope::class)
class AutofillModule {

    @Provides
    fun provideInternalTestUserStore(applicationContext: Context): InternalTestUserStore = RealInternalTestUserStore(applicationContext)

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun autofillStore(
        secureStorage: SecureStorage,
        context: Context,
        internalTestUserChecker: InternalTestUserChecker,
        autofillUrlMatcher: AutofillUrlMatcher,
    ): AutofillStore {
        return SecureStoreBackedAutofillStore(
            secureStorage = secureStorage,
            lastUpdatedTimeProvider = RealLastUpdatedTimeProvider(),
            autofillPrefsStore = RealAutofillPrefsStore(context, internalTestUserChecker),
            autofillUrlMatcher = autofillUrlMatcher,
        )
    }

    @Provides
    fun provideAutofillUrlMatcher(): AutofillUrlMatcher = AutofillDomainNameUrlMatcher()

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideAutofillDatabase(context: Context): AutofillDatabase {
        return Room.databaseBuilder(context, AutofillDatabase::class.java, "autofill.db")
            .fallbackToDestructiveMigration()
            .addMigrations(*ALL_MIGRATIONS)
            .build()
    }

    @SingleInstanceIn(AppScope::class)
    @Provides
    fun provideAutofillRepository(
        database: AutofillDatabase,
        @AppCoroutineScope coroutineScope: CoroutineScope,
        dispatcherProvider: DispatcherProvider,
    ): AutofillFeatureRepository {
        return RealAutofillFeatureRepository(database, coroutineScope, dispatcherProvider)
    }
}
