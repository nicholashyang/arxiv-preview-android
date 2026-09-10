package com.example.arxivpreview.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.arxivpreview.model.Paper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class SharePaperTest {
    @Test fun opensTextChooserWithTitleAndVersionLinkAndCancellationIsHarmless() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val captured = AtomicReference<Intent>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_CHOOSER) return null
                captured.set(intent)
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        ActivityScenario.launch<ReaderTestActivity>(Intent(instrumentation.targetContext, ReaderTestActivity::class.java).putExtra("screen", "actions")).use { scenario ->
            instrumentation.addMonitor(monitor)
            try {
                scenario.onActivity { activity -> sharePaper(activity, Paper("hep-th/9901001", "hep-th/9901001v2", "Paper title", "", emptyList(), 0,0,"", emptyList(),"","",null,null)) }
                val chooser = captured.get()
                assertNotNull(chooser)
                @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                assertEquals(Intent.ACTION_SEND, send.action)
                assertEquals("text/plain", send.type)
                assertEquals("Paper title\nhttps://arxiv.org/abs/hep-th/9901001v2", send.getStringExtra(Intent.EXTRA_TEXT))
                assertNull(send.getStringExtra(Intent.EXTRA_STREAM))
            } finally { instrumentation.removeMonitor(monitor) }
        }
    }
}
