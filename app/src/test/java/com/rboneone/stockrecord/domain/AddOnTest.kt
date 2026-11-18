package com.rboneone.stockrecord.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddOnTest {
    private fun view() = AllocationView(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, emptyList(), false)

    @Test fun compute_usesCostAndPlan() {
        val items = listOf(
            AllocationItem(pos(), null, 1_200_000.0, 1_000_000.0),
        )
        val v = AllocationView(1_200_000.0, 0.0, 0.0, 0.0, 0.0, 0.0, items, false)
        val r = AddOn.compute(v, "433", 100_000.0, 5.0)
        assertEquals(1_000_000.0, r.invested, 0.0)
        assertEquals(1_200_000.0, r.marketValue, 0.0)
        assertEquals(400_000.0, r.coreAmount, 0.001)
        assertEquals(300_000.0, r.leveragedAmount, 0.001)
        assertEquals(300_000.0, r.cashLikeAmount, 0.001)
        assertEquals(200_000.0, r.addCapital, 0.001)
        assertEquals(40_000.0, r.perTranche!!, 0.001)
        assertEquals(20_000.0, r.perReduce!!, 0.001)
    }

    @Test fun compute_reserveTooLarge_orNoTranches() {
        val r = AddOn.compute(view(), "433", 10.0, 0.0)
        assertEquals(-10.0, r.addCapital, 0.0)
        assertNull(r.perTranche)
        assertNull(r.perReduce)
        assertNull(AddOn.compute(view(), "433", 0.0, 2.5).tranches)
    }

    private fun pos(): Position = Position(
        Holding(com.rboneone.stockrecord.data.Market.TW, "0050", "", 1.0, 1_000_000.0, 0.0), "0050", null, null, null,
    )
}
