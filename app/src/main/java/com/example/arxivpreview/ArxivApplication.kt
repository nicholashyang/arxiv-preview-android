package com.example.arxivpreview

import android.app.Application

class ArxivApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
