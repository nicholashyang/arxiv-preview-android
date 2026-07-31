package com.example.arxivpreview

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.example.arxivpreview.ui.ArxivApp
import com.example.arxivpreview.ui.theme.ArxivPreviewTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ArxivPreviewTheme {
                ArxivApp(
                    application = application,
                    container = (application as ArxivApplication).container,
                )
            }
        }
    }
}
