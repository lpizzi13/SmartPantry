package it.sapienza.smartpantry.model

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class UserResponse(
    @SerializedName("status") val status: String = "",
    @SerializedName("userData") val userData: User = User()
) : Parcelable

@Parcelize
data class User(
    @SerializedName("uid") val uid: String = "",
    @SerializedName("email") val email: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("biometrics") val biometrics: Biometrics = Biometrics(),
    @SerializedName("goals") val goals: Goals = Goals(),
    @SerializedName("firstLogin") val firstLogin: Boolean = false
) : Parcelable

@Parcelize
data class Biometrics(
    @SerializedName("age") var age: Int = 0,
    @SerializedName("gender") var gender: String = "",
    @SerializedName("height") var height: Double = 0.0,
    @SerializedName("weight") var weight: Double = 0.0,
    @SerializedName("activityLevel") var activityLevel: String = ""
) : Parcelable

@Parcelize
data class Goals(
    @SerializedName("dailyKcal") var dailyKcal: Int = 0,
    @SerializedName("macrosTarget") var macrosTarget: Map<String, Int> = emptyMap()
) : Parcelable

data class UserRequest(
    @SerializedName("uid") val uid: String,
    @SerializedName("email") val email: String
)

data class UpdateUserResponse(
    @SerializedName("status") val status: String = "",
    @SerializedName("dailyKcal") val dailyKcal: Int = 0,
    @SerializedName("macros") val macros: Map<String, Int> = emptyMap()
)
