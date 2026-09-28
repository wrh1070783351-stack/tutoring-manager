package com.wrh.keshiguanjia

import android.app.Application
import com.wrh.keshiguanjia.data.Graph

class KeshiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
