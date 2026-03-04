package it.sapienza.smartpantry.service

import it.sapienza.smartpantry.model.*
import retrofit2.Call
import retrofit2.http.*

interface ApiService {
    @POST("user/login")
    fun getUserData(@Body request: UserRequest): Call<UserResponse>

    @POST("user/update")
    fun updateUser(@Body user: User): Call<UpdateUserResponse>

    @GET("pantry/search")
    fun searchProducts(
        @Query("q") query: String,
        @Query("similar") similar: Boolean,
        @Query("limit") limit: Int,
        @Query("lang") lang: String
    ): Call<OpenFoodFactsSearchResponse>

    @GET("pantry/barcode/{code}")
    fun getProductByBarcode(@Path("code") barcode: String): Call<OpenFoodFactsProductResponse>

    @POST("pantry/add")
    fun addToPantry(@Body request: PantryAddRequest): Call<Unit>

    @PATCH("pantry/quantity")
    fun updateItemQuantity(@Body request: PantryQuantityRequest): Call<Unit>

    @PATCH("pantry/item")
    fun updatePantryItem(@Body request: PantryItemUpdateRequest): Call<Unit>

    @GET("pantry/{uid}")
    fun getPantry(@Path("uid") uid: String): Call<PantryResponse>
}

data class PantryAddRequest(
    val uid: String,
    val openFoodFactsId: String? = null,
    val productName: String,
    val quantity: Int,
    val kcal: Double,
    val prot: Double,
    val fat: Double,
    val carbs: Double
)

data class PantryQuantityRequest(
    val uid: String,
    val openFoodFactsId: String,
    val quantity: Int
)

data class PantryItemUpdateRequest(
    val uid: String,
    val openFoodFactsId: String,
    val productName: String? = null,
    val quantity: Int? = null,
    val kcal: Double? = null,
    val prot: Double? = null,
    val fat: Double? = null,
    val carbs: Double? = null
)
