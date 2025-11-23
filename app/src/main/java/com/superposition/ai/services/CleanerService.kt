package com.superposition.ai.services

import android.app.Service
import android.content.Intent
import android.os.IBinder

class CleanerService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
