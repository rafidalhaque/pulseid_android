package lab.sabily.pulseid.data

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val uuid: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val success: Boolean,
    val token: String
)

@Serializable
data class EmployeeKeyResponse(
    val master_secret: String
)

@Serializable
data class ProfileResponse(
    val id: String,
    val name: String,
    val email: String,
    val id_no: String,
    val join_date: String?,
    val designation: String,
    val position: String
)