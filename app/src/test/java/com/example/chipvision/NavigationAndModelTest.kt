package com.example.chipvision

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.chipvision.inference.ModelManager
import com.example.chipvision.ui.AppScreen
import com.example.chipvision.ui.ChipVisionViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationAndModelTest {

    @Test
    fun testAppScreenNavigationEnum() {
        val defaultScreen = AppScreen.HOME
        assertEquals(AppScreen.HOME, defaultScreen)

        val inspectionScreen = AppScreen.INSPECTION
        val historyScreen = AppScreen.HISTORY
        val settingsScreen = AppScreen.SETTINGS
        val diagnosticsScreen = AppScreen.DIAGNOSTICS

        assertNotNull(inspectionScreen)
        assertNotNull(historyScreen)
        assertNotNull(settingsScreen)
        assertNotNull(diagnosticsScreen)
    }

    @Test
    fun testViewModelInitialScreenIsHome() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = ChipVisionViewModel(application)

        // Strict requirement: Home must be the initial screen on startup
        assertEquals("Home must be the default screen upon launch", AppScreen.HOME, viewModel.currentScreen.value)
    }

    @Test
    fun testModelManagerDiagnostics_whenAssetsNotPresent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val modelManager = ModelManager(context)

        val diagnostics = modelManager.initialize()

        // When physical models are not yet deployed in assets, the manager must report failure/missing gracefully
        assertFalse("Cannot report all models loaded when files are missing", diagnostics.allModelsLoaded)
        assertFalse("Detector cannot report loaded when file is missing", diagnostics.detectorLoaded)
        assertFalse("Classifier cannot report loaded when file is missing", diagnostics.classifierLoaded)
        assertTrue("Detector error must be reported", diagnostics.detectorError != null)
        assertTrue("Classifier error must be reported", diagnostics.classifierError != null)
    }

    @Test
    fun testVerifyFlatBufferIdentifier_validTFL3() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val modelManager = ModelManager(context)

        // Valid TFLite header: 4 bytes offset, then ASCII 'T', 'F', 'L', '3'
        val validBuffer = ByteBuffer.allocate(16)
        validBuffer.put(0, 0)
        validBuffer.put(1, 0)
        validBuffer.put(2, 0)
        validBuffer.put(3, 0)
        validBuffer.put(4, 'T'.code.toByte())
        validBuffer.put(5, 'F'.code.toByte())
        validBuffer.put(6, 'L'.code.toByte())
        validBuffer.put(7, '3'.code.toByte())

        val result = modelManager.verifyFlatBufferIdentifier(validBuffer, "test_model.tflite")
        assertNull("Valid TFL3 header must return null error", result)
    }

    @Test
    fun testVerifyFlatBufferIdentifier_invalidHeader() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val modelManager = ModelManager(context)

        // Corrupted header: has 'ABCD' instead of 'TFL3'
        val corruptBuffer = ByteBuffer.allocate(16)
        corruptBuffer.put(4, 'A'.code.toByte())
        corruptBuffer.put(5, 'B'.code.toByte())
        corruptBuffer.put(6, 'C'.code.toByte())
        corruptBuffer.put(7, 'D'.code.toByte())

        val result = modelManager.verifyFlatBufferIdentifier(corruptBuffer, "corrupted.tflite")
        assertNotNull("Invalid header must be rejected", result)
        assertTrue(result!!.contains("expected 'TFL3'"))
    }

    @Test
    fun testVerifyFlatBufferIdentifier_tooSmall() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val modelManager = ModelManager(context)

        val tinyBuffer = ByteBuffer.allocate(4) // Less than 8 bytes
        val result = modelManager.verifyFlatBufferIdentifier(tinyBuffer, "empty.tflite")
        assertNotNull("Under-sized buffer must be rejected", result)
    }
}
