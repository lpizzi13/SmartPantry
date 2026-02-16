package it.sapienza.smartpantry.data.openfoodfacts

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenFoodFactsApiService {
    @GET("cgi/search.pl")
    suspend fun searchProducts(
        @Query("search_terms") searchTerms: String,
        @Query("search_simple") searchSimple: Int = 1,
        @Query("action") action: String = "process",
        @Query("json") json: Int = 1,
        @Query("page_size") pageSize: Int = 50
    ): OpenFoodFactsSearchResponse
}
