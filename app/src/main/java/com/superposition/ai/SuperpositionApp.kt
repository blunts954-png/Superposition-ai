package com.superposition.ai

import android.app.Application
import android.content.Context

class SuperpositionApp : Application() {
    
    companion object {
        private lateinit var instance: SuperpositionApp
        
        fun getAppContext(): Context = instance.applicationContext
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
