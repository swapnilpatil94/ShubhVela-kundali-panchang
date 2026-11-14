package app.parivaar.panchang.core.geo

import app.parivaar.panchang.core.geo.OffsetPickerModel.ErrorCode
import app.parivaar.panchang.core.geo.OffsetPickerModel.Result
import kotlin.test.Test
import kotlin.test.assertEquals

class OffsetPickerModelTest {
    private fun ok(text: String): Int = (OffsetPickerModel.parse(text) as Result.Ok).offset.seconds
    private fun err(text: String): ErrorCode = (OffsetPickerModel.parse(text) as Result.Error).code

    @Test fun parsesValid() {
        assertEquals(17_460, ok("+04:51"))
        assertEquals(-12_600, ok("-3:30"))
        assertEquals(21_200, ok("+05:53:20"))
        assertEquals(19_800, ok("5:30"))
        assertEquals(19_800, ok("  5:30 "))
        assertEquals(50_400, ok("+14:00"))
        assertEquals(-43_200, ok("-12:00"))
    }

    @Test fun rejectsInvalid() {
        assertEquals(ErrorCode.OUT_OF_RANGE, err("+14:01"))
        assertEquals(ErrorCode.OUT_OF_RANGE, err("-12:01"))
        assertEquals(ErrorCode.FORMAT, err("+5:60"))
        assertEquals(ErrorCode.FORMAT, err("+5:30:60"))
        assertEquals(ErrorCode.EMPTY, err(""))
        assertEquals(ErrorCode.EMPTY, err("   "))
        assertEquals(ErrorCode.FORMAT, err("abc"))
        assertEquals(ErrorCode.FORMAT, err("5"))
        assertEquals(ErrorCode.FORMAT, err("+٠٥:30"))
    }

    @Test fun formats() {
        assertEquals("+05:30", OffsetPickerModel.format(19_800))
        assertEquals("-03:30", OffsetPickerModel.format(-12_600))
        assertEquals("+05:53:20", OffsetPickerModel.format(21_200))
    }
}
