package it.sapienza.smartpantry.service
import it.sapienza.smartpantry.model.*
import retrofit2.Call
import retrofit2.http.*

interface SmartPantryApi {
    @POST("get-user-data")
    fun getUserData(@Body request: UserRequest): Call<UserResponse>

    @POST("update-user")
    fun updateUser(@Body user: User): Call<UpdateUserResponse>

    @POST("get-diet")
    fun getDiet(@Body request: DietRequest): Call<DietResponse>

    @POST("save-diet")
    fun saveDiet(@Body request: SaveDietRequest): Call<SaveDietResponse>

    @POST("register-user")
    fun registerUser(@Body request: RegisterRequest): Call<UserResponse>

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

    @PATCH("pantry/grams")
    fun updateItemGrams(@Body request: PantryGramsRequest): Call<Unit>

    @GET("pantry/{uid}")
    fun getPantry(@Path("uid") uid: String): Call<PantryResponse>
}

data class PantryAddRequest(
    val uid: String,
    val openFoodFactsId: String? = null,
    val productName: String? = null,
    val quantity: Int,
    val nutrients: PantryAddNutrients? = null,
    val packageWeightGrams: Double? = null
) {
    init {
        require(quantity >= 1) { "quantity must be >= 1." }
    }
}

data class PantryAddNutrients(
    val kcal: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val protein: Double = 0.0
)

data class PantryGramsRequest(
    val uid: String,
    val openFoodFactsId: String? = null,
    val productName: String? = null,
    val grams: Double
) {
    init {
        require(grams.isFinite() && grams >= 0.0) { "grams must be >= 0." }
        require(
            !openFoodFactsId.isNullOrBlank() || !productName.isNullOrBlank()
        ) { "Either openFoodFactsId or productName is required." }
    }
}
