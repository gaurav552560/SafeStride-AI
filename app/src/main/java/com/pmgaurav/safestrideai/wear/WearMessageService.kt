package com.pmgaurav.safestrideai.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WearMessageService : WearableListenerService() {

    @Inject
    lateinit var wearSyncManager: WearSyncManager

    override fun onMessageReceived(messageEvent: MessageEvent) {
        wearSyncManager.onMessageReceived(messageEvent.path)
    }
}

