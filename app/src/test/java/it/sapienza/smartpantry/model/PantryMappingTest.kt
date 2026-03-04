package it.sapienza.smartpantry.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PantryMappingTest {
    private val gson = Gson()

    @Test
    fun parses_nested_nutrients_from_pantry_payload() {
        val raw = """
            {"status":"ok","items":[{"openFoodFactsId":"123","productName":"Test Food","quantity":2,"nutrients":{"kcal":350,"carbs":70.2,"protein":12.5,"fat":1.8}}]}
        """.trimIndent()

        val parsed = gson.fromJson(raw, PantryResponse::class.java)
        val item = parsed.items.first()

        assertEquals(350.0, item.resolvedKcal() ?: -1.0, 0.0001)
        assertEquals(70.2, item.resolvedCarbs() ?: -1.0, 0.0001)
        assertEquals(12.5, item.resolvedProt() ?: -1.0, 0.0001)
        assertEquals(1.8, item.resolvedFat() ?: -1.0, 0.0001)
    }

    @Test
    fun falls_back_to_flat_macros_when_nested_nutrients_missing() {
        val raw = """
            {"status":"ok","items":[{"openFoodFactsId":"123","productName":"Flat Food","quantity":1,"kcal":200,"carbs":20,"prot":5,"fat":3}]}
        """.trimIndent()

        val parsed = gson.fromJson(raw, PantryResponse::class.java)
        val item = parsed.items.first()

        assertEquals(200.0, item.resolvedKcal() ?: -1.0, 0.0001)
        assertEquals(20.0, item.resolvedCarbs() ?: -1.0, 0.0001)
        assertEquals(5.0, item.resolvedProt() ?: -1.0, 0.0001)
        assertEquals(3.0, item.resolvedFat() ?: -1.0, 0.0001)
    }

    @Test
    fun returns_null_when_macros_absent() {
        val raw = """
            {"status":"ok","items":[{"openFoodFactsId":"123","productName":"No Macros","quantity":1}]}
        """.trimIndent()

        val parsed = gson.fromJson(raw, PantryResponse::class.java)
        val item = parsed.items.first()

        assertNull(item.resolvedKcal())
        assertNull(item.resolvedCarbs())
        assertNull(item.resolvedProt())
        assertNull(item.resolvedFat())
    }
}
