package com.rboneone.stockrecord.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncConfigTest {
    @Test
    fun defaultIs24Hours() = assertEquals(24, SyncConfig().totalHours())

    @Test
    fun daysAndHoursAdd() = assertEquals(2 * 24 + 5, SyncConfig(intervalDays = 2, intervalHours = 5).totalHours())

    @Test
    fun atLeastOneHour() = assertEquals(1, SyncConfig(intervalDays = 0, intervalHours = 0).totalHours())
}
