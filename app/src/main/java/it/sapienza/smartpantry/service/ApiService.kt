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
    fun addToPantry(@Body request: PantryRequest): Call<Unit>

    @PATCH("pantry/decrement")
    fun decrementItem(@Body request: PantryRequest): Call<Unit>

    @HTTP(method = "DELETE", path = "pantry/item", hasBody = true)
    fun deleteItem(@Body request: PantryRequest): Call<Unit>

    @GET("pantry/{uid}")
    fun getPantry(@Path("uid") uid: String): Call<PantryResponse>
}

data class PantryRequest(
    val uid: String,
    val openFoodFactsId: String,
    val productName: String? = null,
    val quantityDelta: Int? = null
)
