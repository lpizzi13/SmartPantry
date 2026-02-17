package it.sapienza.smartpantry.data.supermarket

import retrofit2.http.GET
import retrofit2.http.Query

interface OverpassApiService {
    @GET("api/interpreter")
    suspend fun searchPointsOfInterest(
        @Query("data") query: String
    ): OverpassResponse
}
