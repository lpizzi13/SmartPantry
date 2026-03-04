package it.sapienza.smartpantry.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenFoodFactsMappingTest {
    private val gson = Gson()

    @Test
    fun parses_flat_macros_from_search_response() {
        val raw = """
            {"query":"pasta","products":[{"code":"123456","product_name":"Test Pasta","kcal":350,"prot":12.5,"fat":1.8,"carbs":70.2,"packageWeightGrams":1000}]}
        """.trimIndent()
        val parsed = gson.fromJson(raw, OpenFoodFactsSearchResponse::class.java)
        val product = parsed.products.first()

        assertEquals(350.0, product.resolvedKcal() ?: -1.0, 0.0001)
        assertEquals(12.5, product.resolvedProt() ?: -1.0, 0.0001)
        assertEquals(1.8, product.resolvedFat() ?: -1.0, 0.0001)
        assertEquals(70.2, product.resolvedCarbs() ?: -1.0, 0.0001)
        assertEquals(1000.0, product.resolvedPackageWeightGrams() ?: -1.0, 0.0001)
    }

    @Test
    fun parses_nested_macros_from_nutriments() {
        val raw = """
            {"status":1,"product":{"code":"987654","product_name":"Test Cereal","nutriments":{"energy-kcal_100g":410,"proteins_100g":9.7,"fat_100g":6.3,"carbohydrates_100g":74.4}}}
        """.trimIndent()
        val parsed = gson.fromJson(raw, OpenFoodFactsProductResponse::class.java)
        val product = parsed.product!!

        assertEquals(410.0, product.resolvedKcal() ?: -1.0, 0.0001)
        assertEquals(9.7, product.resolvedProt() ?: -1.0, 0.0001)
        assertEquals(6.3, product.resolvedFat() ?: -1.0, 0.0001)
        assertEquals(74.4, product.resolvedCarbs() ?: -1.0, 0.0001)
    }

    @Test
    fun prefers_flat_value_when_both_flat_and_nested_exist() {
        val raw = """
            {"code":"42","product_name":"Mixed Source","kcal":222,"prot":10,"fat":5,"carbs":30,"nutriments":{"energy-kcal_100g":999,"proteins_100g":999,"fat_100g":999,"carbohydrates_100g":999}}
        """.trimIndent()
        val product = gson.fromJson(raw, OpenFoodFactsProduct::class.java)

        assertEquals(222.0, product.resolvedKcal() ?: -1.0, 0.0001)
        assertEquals(10.0, product.resolvedProt() ?: -1.0, 0.0001)
        assertEquals(5.0, product.resolvedFat() ?: -1.0, 0.0001)
        assertEquals(30.0, product.resolvedCarbs() ?: -1.0, 0.0001)
    }

    @Test
    fun returns_null_when_macros_absent() {
        val raw = """{"code":"no-macros","product_name":"No Macros"}"""
        val product = gson.fromJson(raw, OpenFoodFactsProduct::class.java)

        assertNull(product.resolvedKcal())
        assertNull(product.resolvedProt())
        assertNull(product.resolvedFat())
        assertNull(product.resolvedCarbs())
        assertNull(product.resolvedPackageWeightGrams())
    }
}
