package it.sapienza.smartpantry.data.repository

import it.sapienza.smartpantry.model.UpdateUserResponse
import it.sapienza.smartpantry.model.User
import it.sapienza.smartpantry.model.UserRequest
import it.sapienza.smartpantry.model.UserResponse
import it.sapienza.smartpantry.service.RetrofitClient
import retrofit2.Callback

class UserRepository {
    private val api = RetrofitClient.instance

    fun getUserData(uid: String, email: String, callback: Callback<UserResponse>) {
        api.getUserData(UserRequest(uid, email)).enqueue(callback)
    }

    fun updateUser(user: User, callback: Callback<UpdateUserResponse>) {
        api.updateUser(user).enqueue(callback)
    }
}
