package it.sapienza.smartpantry.service
import it.sapienza.smartpantry.model.User
import it.sapienza.smartpantry.model.UserResponse
import it.sapienza.smartpantry.model.UserRequest
import it.sapienza.smartpantry.model.UpdateUserResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface SmartPantryApi {
    @POST("get-user-data")
    fun getUserData(@Body request: UserRequest): Call<UserResponse>

    @POST("update-user")
    fun updateUser(@Body user: User): Call<UpdateUserResponse>
}