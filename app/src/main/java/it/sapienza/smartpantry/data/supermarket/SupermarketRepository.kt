package it.sapienza.smartpantry.data.supermarket

import android.location.Location
import java.util.Locale
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SupermarketRepository {
    private val api: OverpassApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OverpassApiService::class.java)
    }

    suspend fun findNearbySupermarkets(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int = DEFAULT_RADIUS_METERS
    ): List<NearbySupermarket> {
        val response = api.searchPointsOfInterest(
            query = buildSupermarketQuery(
                latitude = latitude,
                longitude = longitude,
                radiusMeters = radiusMeters
            )
        )

        return response.elements
            .mapNotNull { element ->
                val elementLatitude = element.lat ?: element.center?.lat
                val elementLongitude = element.lon ?: element.center?.lon
                if (elementLatitude == null || elementLongitude == null) {
                    null
                } else {
                    val idPrefix = element.type?.takeIf { it.isNotBlank() } ?: "unknown"
                    val elementId = element.id?.toString() ?: "$elementLatitude,$elementLongitude"
                    NearbySupermarket(
                        id = "$idPrefix-$elementId",
                        name = element.tags?.get("name").orEmpty().ifBlank { DEFAULT_SUPERMARKET_NAME },
                        latitude = elementLatitude,
                        longitude = elementLongitude
                    )
                }
            }
            .distinctBy { it.id }
            .sortedBy { supermarket ->
                distanceMeters(
                    startLatitude = latitude,
                    startLongitude = longitude,
                    endLatitude = supermarket.latitude,
                    endLongitude = supermarket.longitude
                )
            }
    }

    private fun buildSupermarketQuery(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int
    ): String {
        val lat = String.format(Locale.US, "%.6f", latitude)
        val lon = String.format(Locale.US, "%.6f", longitude)
        return """
            [out:json][timeout:25];
            (
              node["shop"="supermarket"](around:$radiusMeters,$lat,$lon);
              way["shop"="supermarket"](around:$radiusMeters,$lat,$lon);
              relation["shop"="supermarket"](around:$radiusMeters,$lat,$lon);
            );
            out center;
        """.trimIndent()
    }

    private fun distanceMeters(
        startLatitude: Double,
        startLongitude: Double,
        endLatitude: Double,
        endLongitude: Double
    ): Float {
        val distanceMeters = FloatArray(1)
        Location.distanceBetween(
            startLatitude,
            startLongitude,
            endLatitude,
            endLongitude,
            distanceMeters
        )
        return distanceMeters.first()
    }

    private companion object {
        const val BASE_URL = "https://overpass-api.de/"
        const val DEFAULT_SUPERMARKET_NAME = "Supermercato vicino"
        const val DEFAULT_RADIUS_METERS = 300
    }
}
