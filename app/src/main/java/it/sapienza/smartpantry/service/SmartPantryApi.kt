package it.sapienza.smartpantry.service
import it.sapienza.smartpantry.model.User
import it.sapienza.smartpantry.model.UserResponse
import it.sapienza.smartpantry.model.UserRequest
import it.sapienza.smartpantry.model.UpdateUserResponse
import it.sapienza.smartpantry.model.RegisterRequest
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface SmartPantryApi {
    @POST("get-user-data")
    fun getUserData(@Body request: UserRequest): Call<UserResponse>

    @POST("update-user")
    fun updateUser(@Body user: User): Call<UpdateUserResponse>

    @POST("register-user")
    fun registerUser(@Body request: RegisterRequest): Call<UserResponse>
}