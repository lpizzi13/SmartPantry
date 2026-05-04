package it.sapienza.smartpantry.model

data class NearbySupermarketsRequest(
    val latitude: Double,
    val longitude: Double,
    val radius: Int = 5000 // default 5km
)

data class NearbySupermarket(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distance: Double, // in meters
    val rating: Double? = null,
    val userRatingsTotal: Int? = null,
    val openNow: Boolean? = null
)

data class NearbySupermarketsResponse(
    val status: String,
    val results: List<NearbySupermarket>
)
