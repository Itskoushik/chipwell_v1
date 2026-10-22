package com.example.chipvision

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.example.chipvision.inference.ModelManager
import com.example.chipvision.ui.AppScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationAndModelTest {
    @Test fun appStartsAtHome() = assertEquals(AppScreen.HOME, AppScreen.HOME)

    @Test fun onlyNewInspectionModelIsPackaged() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.assets.open(ModelManager.MODEL_FILE_NAME).use { model ->
            assertTrue("Single inspection model must be packaged and non-empty", model.available() > 0)
            val header = ByteArray(8)
            assertEquals(8, model.read(header))
            assertEquals("TFL3", String(header, 4, 4, Charsets.US_ASCII))
        }
        val assets = context.assets.list("")!!.toSet()
        assertTrue(ModelManager.MODEL_FILE_NAME in assets)
        assertEquals(1, assets.count { it.endsWith(".tflite") })
    }
}
