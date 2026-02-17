package it.sapienza.smartpantry.data.supermarket

import com.google.gson.annotations.SerializedName

data class OverpassResponse(
    @SerializedName("elements")
    val elements: List<OverpassElementDto> = emptyList()
)

data class OverpassElementDto(
    @SerializedName("id")
    val id: Long?,
    @SerializedName("type")
    val type: String?,
    @SerializedName("lat")
    val lat: Double?,
    @SerializedName("lon")
    val lon: Double?,
    @SerializedName("center")
    val center: OverpassCenterDto?,
    @SerializedName("tags")
    val tags: Map<String, String>?
)

data class OverpassCenterDto(
    @SerializedName("lat")
    val lat: Double?,
    @SerializedName("lon")
    val lon: Double?
)

data class NearbySupermarket(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)
