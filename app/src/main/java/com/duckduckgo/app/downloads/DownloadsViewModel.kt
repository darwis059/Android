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

package com.duckduckgo.app.downloads

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.downloads.DownloadViewItem.Empty
import com.duckduckgo.app.downloads.DownloadViewItem.Header
import com.duckduckgo.app.downloads.DownloadViewItem.Item
import com.duckduckgo.app.downloads.DownloadViewItem.NotifyMe
import com.duckduckgo.app.downloads.DownloadsViewModel.Command.CancelDownload
import com.duckduckgo.app.downloads.DownloadsViewModel.Command.DisplayMessage
import com.duckduckgo.app.downloads.DownloadsViewModel.Command.DisplayUndoMessage
import com.duckduckgo.app.downloads.DownloadsViewModel.Command.OpenFile
import com.duckduckgo.app.downloads.DownloadsViewModel.Command.ShareFile
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.formatters.time.TimeDiffFormatter
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.NOTIFY_ME_FROM_SCREEN
import com.duckduckgo.app.statistics.pixels.Pixel.PixelValues.NOTIFY_ME_DOWNLOADS_SCREEN
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.downloads.api.DownloadsRepository
import com.duckduckgo.downloads.api.model.DownloadItem
import com.duckduckgo.downloads.store.DownloadStatus
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.threeten.bp.LocalDateTime

@ContributesViewModel(ActivityScope::class)
class DownloadsViewModel @Inject constructor(
    private val timeDiffFormatter: TimeDiffFormatter,
    private val downloadsRepository: DownloadsRepository,
    private val dispatcher: DispatcherProvider,
    private val pixel: Pixel,
) : ViewModel(), DownloadsItemListener {

    data class ViewState(
        val enableSearch: Boolean = false,
        val downloadItems: List<DownloadViewItem> = emptyList(),
        val filteredItems: List<DownloadViewItem> = emptyList(),
    )

    sealed class Command {
        data class DisplayMessage(@StringRes val messageId: Int, val arg: String = "") : Command()
        data class DisplayUndoMessage(@StringRes val messageId: Int, val arg: String = "", val items: List<DownloadItem> = emptyList()) : Command()
        data class OpenFile(val item: DownloadItem) : Command()
        data class ShareFile(val item: DownloadItem) : Command()
        data class CancelDownload(val item: DownloadItem) : Command()
    }

    private val command = Channel<Command>(1, DROP_OLDEST)

    private val downloadItems: Flow<List<DownloadViewItem>> = downloadsRepository.getDownloadsAsFlow().map {
        it.mapToDownloadViewItems()
    }

    private val showNotifyMe = MutableStateFlow(true)

    private val filterText = MutableStateFlow("")

    val viewState: StateFlow<ViewState> = combine(
        flow = downloadItems,
        flow2 = showNotifyMe,
        flow3 = filterText,
    ) { items, showNotifyMe, filter ->
        val downloadItemsList = when {
            showNotifyMe -> if (items.isEmpty()) listOf(NotifyMe, Empty) else listOf(NotifyMe).plus(items)
            else -> items.ifEmpty { listOf(Empty) }
        }
        val filteredItemsList = if (filter.isEmpty()) downloadItemsList else filtered(items, filter)
        ViewState(enableSearch = items.isNotEmpty(), downloadItems = downloadItemsList, filteredItems = filteredItemsList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ViewState())

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun deleteAllDownloadedItems() {
        viewModelScope.launch(dispatcher.io()) {
            val itemsToDelete = downloadsRepository.getDownloads()
            if (itemsToDelete.isNotEmpty()) {
                downloadsRepository.deleteAll()
                command.send(DisplayUndoMessage(messageId = R.string.downloadsAllFilesDeletedMessage, items = itemsToDelete))
            }
        }
    }

    fun delete(item: DownloadItem) {
        viewModelScope.launch(dispatcher.io()) {
            downloadsRepository.delete(item.downloadId)
            command.send(DisplayMessage(R.string.downloadsFileNotFoundErrorMessage))
        }
    }

    fun insert(items: List<DownloadItem>) {
        viewModelScope.launch(dispatcher.io()) {
            downloadsRepository.insertAll(items)
        }
    }

    fun removeFromDiskAndFromDownloadManager(items: List<DownloadItem>) {
        items.forEach {
            File(it.filePath).delete()
        }

        // Remove all unfinished downloads from DownloadManager.
        items.filter { it.downloadStatus != DownloadStatus.FINISHED }.forEach { removeFromDownloadManager(it.downloadId) }
    }

    fun removeFromDownloadManager(downloadId: Long) {
        viewModelScope.launch(dispatcher.io()) {
            downloadsRepository.delete(downloadId)
        }
    }

    fun onQueryTextChange(newText: String) {
        viewModelScope.launch {
            filterText.emit(newText)
        }
    }

    private fun filtered(items: List<DownloadViewItem>, newText: String): List<DownloadViewItem> {
        val filtered = LinkedHashMap<Header, List<Item>>()
        items.forEach { item ->
            if (item is Header) {
                filtered[item] = mutableListOf()
            } else if (item is Item) {
                if (item.downloadItem.fileName.lowercase().contains(newText.lowercase())) {
                    val list = filtered[filtered.keys.last()]
                    val newList = list?.plus(item) ?: listOf(item)
                    filtered[filtered.keys.last()] = newList
                }
            }
        }

        val list = mutableListOf<DownloadViewItem>()
        filtered.forEach {
            if (it.value.isNotEmpty()) {
                list.add(it.key)
                list.addAll(it.value)
            }
        }

        if (list.isEmpty()) {
            list.add(Empty)
        }

        return list
    }

    override fun onItemClicked(item: DownloadItem) {
        viewModelScope.launch { command.send(OpenFile(item)) }
    }

    override fun onShareItemClicked(item: DownloadItem) {
        viewModelScope.launch { command.send(ShareFile(item)) }
    }

    override fun onDeleteItemClicked(item: DownloadItem) {
        viewModelScope.launch(dispatcher.io()) {
            downloadsRepository.delete(item.downloadId)
            command.send(DisplayUndoMessage(messageId = R.string.downloadsFileDeletedMessage, arg = item.fileName, items = listOf(item)))
        }
    }

    override fun onCancelItemClicked(item: DownloadItem) {
        viewModelScope.launch(dispatcher.io()) {
            downloadsRepository.delete(item.downloadId)
            command.send(CancelDownload(item))
        }
    }

    override fun onItemVisibilityChanged(visible: Boolean) {
        viewModelScope.launch {
            showNotifyMe.emit(visible)
        }
    }

    override fun onNotifyMeButtonClicked() {
        pixel.fire(AppPixelName.NOTIFY_ME_BUTTON_PRESSED, mapOf(NOTIFY_ME_FROM_SCREEN to NOTIFY_ME_DOWNLOADS_SCREEN))
    }

    override fun onNotifyMeDismissButtonClicked() {
        pixel.fire(AppPixelName.NOTIFY_ME_DISMISS_BUTTON_PRESSED, mapOf(NOTIFY_ME_FROM_SCREEN to NOTIFY_ME_DOWNLOADS_SCREEN))
    }

    private fun DownloadItem.mapToDownloadViewItem(): DownloadViewItem = Item(this)

    private fun List<DownloadItem>.mapToDownloadViewItems(): List<DownloadViewItem> {
        if (this.isEmpty()) return listOf(Empty)

        val itemViews = mutableListOf<DownloadViewItem>()
        var previousDate = timeDiffFormatter.formatTimePassedInDaysWeeksMonthsYears(
            startLocalDateTime = LocalDateTime.parse(this[0].createdAt),
        )

        this.forEachIndexed { index, downloadItem ->
            if (index == 0) {
                itemViews.add(Header(previousDate))
                itemViews.add(downloadItem.mapToDownloadViewItem())
            } else {
                val thisDate = timeDiffFormatter.formatTimePassedInDaysWeeksMonthsYears(
                    startLocalDateTime = LocalDateTime.parse(downloadItem.createdAt),
                )
                if (previousDate == thisDate) {
                    itemViews.add(downloadItem.mapToDownloadViewItem())
                } else {
                    itemViews.add(Header(thisDate))
                    itemViews.add(downloadItem.mapToDownloadViewItem())
                    previousDate = thisDate
                }
            }
        }

        return itemViews
    }
}
