package com.rboneone.stockrecord.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncConfigTest {
    @Test fun backupOnChange_defaultsOff() {
        assertEquals(false, SyncConfig().backupOnChange)
        assertEquals(false, SyncConfig(auto = true, autoFileUri = "content://x").backupOnChangeActive())
    }

    @Test fun backupOnChange_needsAutoAndFileAndSwitch() {
        val on = SyncConfig(auto = true, autoFileUri = "content://x", backupOnChange = true)
        assertEquals(true, on.backupOnChangeActive())
        assertEquals(false, on.copy(auto = false).backupOnChangeActive())
        assertEquals(false, on.copy(autoFileUri = null).backupOnChangeActive())
        assertEquals(false, on.copy(backupOnChange = false).backupOnChangeActive())
    }
}
