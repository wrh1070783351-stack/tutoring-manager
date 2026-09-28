package com.wrh.keshiguanjia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wrh.keshiguanjia.ui.KeshiApp
import com.wrh.keshiguanjia.ui.theme.KeshiGuanjiaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KeshiGuanjiaTheme {
                KeshiApp()
            }
        }
    }
}
