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

    @POST("delete-diet")
    fun deleteDiet(@Body request: DeleteDietRequest): Call<DeleteDietResponse>

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

    fun addPantryItem(@Body request: PantryAddRequest): Call<Unit> = addToPantry(request)

    @PATCH("pantry/grams")
    fun updateItemGrams(@Body request: PantryGramsRequest): Call<Unit>

    @POST("pantry/delete")
    fun deletePantryItem(@Body request: PantryDeleteRequest): Call<Unit>

    @GET("pantry/{uid}")
    fun getPantry(@Path("uid") uid: String): Call<PantryResponse>

    @POST("generate_shopping_list")
    fun generateShoppingList(@Body request: GenerateShoppingListRequest): Call<List<ShoppingListItem>>

    @POST("get_shopping_list")
    fun getShoppingList(@Body request: GetShoppingListRequest): Call<GetShoppingListResponse>

    @POST("update_shopping_list")
    fun updateShoppingList(@Body request: UpdateShoppingListRequest): Call<UpdateShoppingListResponse>

    @POST("home/add")
    fun addHomeEntry(@Body request: HomeAddRequest): Call<HomeMutationResponse>

    @PATCH("home/update")
    fun updateHomeEntry(@Body request: HomeUpdateRequest): Call<HomeMutationResponse>

    @HTTP(method = "DELETE", path = "home/delete", hasBody = true)
    fun deleteHomeEntry(@Body request: HomeDeleteRequest): Call<HomeMutationResponse>

    @GET("home/{uid}/{dateKey}")
    fun getHomeDay(
        @Path("uid") uid: String,
        @Path("dateKey") dateKey: String
    ): Call<HomeDayResponse>

    @POST("get-stats")
    fun getStats(@Body request: StatsRequest): Call<StatsResponse>

    @POST("nearby-supermarkets")
    fun getNearbySupermarkets(@Body request: NearbySupermarketsRequest): Call<NearbySupermarketsResponse>
}

data class GetShoppingListRequest(val uid: String)
data class GetShoppingListResponse(val status: String, val shoppingList: List<ShoppingListItem>)

data class UpdateShoppingListRequest(
    val uid: String,
    val shoppingList: List<ShoppingListItem>? = null,
    val item: ShoppingListItem? = null,
    val replace: Boolean = false
)
data class UpdateShoppingListResponse(val status: String, val updatedCount: Int, val replace: Boolean)

data class GenerateShoppingListRequest(
    val uid: String,
    val selectedDietId: String
)

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

data class PantryDeleteRequest(
    val uid: String,
    val openFoodFactsId: String? = null,
    val productName: String? = null
)

data class HomeAddRequest(
    val uid: String,
    val dateKey: String,
    val openFoodFactsId: String? = null,
    val mealType: String,
    val source: String,
    val productName: String,
    val grams: Double,
    val nutrients: HomeAddNutrients
) {
    init {
        require(dateKey.isNotBlank()) { "dateKey is required." }
        require(mealType in setOf("breakfast", "lunch", "dinner", "snacks")) {
            "mealType must be breakfast, lunch, dinner or snacks."
        }
        require(source in setOf("openfoodfacts", "manual")) {
            "source must be openfoodfacts or manual."
        }
        require(grams.isFinite() && grams > 0.0) { "grams must be > 0." }
        require(productName.isNotBlank()) { "productName is required." }
    }
}

data class HomeAddNutrients(
    val kcal: Double = 0.0,
    val carbs: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0
)

data class HomeUpdateRequest(
    val uid: String,
    val dateKey: String,
    val openFoodFactsId: String,
    val mealType: String,
    val source: String,
    val productName: String,
    val grams: Double,
    val nutrients: HomeAddNutrients
) {
    init {
        require(uid.isNotBlank()) { "uid is required." }
        require(dateKey.isNotBlank()) { "dateKey is required." }
        require(openFoodFactsId.isNotBlank()) { "openFoodFactsId is required." }
        require(mealType in setOf("breakfast", "lunch", "dinner", "snacks")) {
            "mealType must be breakfast, lunch, dinner or snacks."
        }
        require(source in setOf("openfoodfacts", "manual")) {
            "source must be openfoodfacts or manual."
        }
        require(productName.isNotBlank()) { "productName is required." }
        require(grams.isFinite() && grams > 0.0) { "grams must be > 0." }
    }
}

data class HomeDeleteRequest(
    val uid: String,
    val dateKey: String,
    val openFoodFactsId: String
) {
    init {
        require(uid.isNotBlank()) { "uid is required." }
        require(dateKey.isNotBlank()) { "dateKey is required." }
        require(openFoodFactsId.isNotBlank()) { "openFoodFactsId is required." }
    }
}
