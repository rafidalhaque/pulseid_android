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
    val position: String,
    val is_admin: String
)

@Serializable
data class AttendanceRecord(
    val id: String,
    val employee_id: String,
    val employee_name: String,
    val date: String,
    val entry_time: String,
    val exit_time: String?,
    val entry_comment: String?,
    val exit_comment: String?
)

@Serializable
data class AttendanceHistoryResponse(
    val success: Boolean,
    val records: List<AttendanceRecord>
)