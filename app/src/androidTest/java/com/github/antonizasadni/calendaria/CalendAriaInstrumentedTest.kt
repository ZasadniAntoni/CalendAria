package com.github.antonizasadni.calendaria

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * CalendAria instrumented test.
 */
@RunWith(AndroidJUnit4::class)
class CalendAriaInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.github.antonizasadni.calendaria", appContext.packageName)
    }
}