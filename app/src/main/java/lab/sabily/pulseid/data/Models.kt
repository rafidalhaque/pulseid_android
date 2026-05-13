package lab.sabily.pulseid.data

import kotlinx.serialization.SerialName
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
    val success: Boolean,
    val employee: Employee
)

@Serializable
data class ImportEmployeeResponse(
    val success: Boolean,
    val message: String? = null
)

@Serializable
data class AttendanceRecord(
    val id: String,
    @SerialName("employee_id") val employeeId: String,
    @SerialName("employee_name") val employeeName: String?,
    val date: String,
    @SerialName("entry_time") val entryTime: String?,
    @SerialName("exit_time") val exitTime: String?,
    @SerialName("entry_comment") val entryComment: String?,
    @SerialName("exit_comment") val exitComment: String?,
    @SerialName("is_present") val isPresent: Boolean = false
)

@Serializable
data class AttendanceHistoryResponse(
    val success: Boolean,
    val records: List<AttendanceRecord>
)

@Serializable
data class AttendanceLogRecord(
    val id: String,
    @SerialName("employee_id") val employeeId: String,
    @SerialName("employee_name") val employeeName: String?,
    @SerialName("ble_uuid") val bleUuid: String,
    @SerialName("recorded_rssi") val recordedRssi: Float,
    @SerialName("recorded_timestamp") val recordedTimestamp: String?,
    @SerialName("event_type") val eventType: String
)

@Serializable
data class AttendanceLogResponse(
    val success: Boolean,
    val log: List<AttendanceLogRecord>
)

@Serializable
data class Employee(
    val id: String,
    val name: String,
    val email: String,
    @SerialName("id_number") val idNumber: String,
    @SerialName("join_date") val joinDate: String?,
    val designation: String,
    val position: String,
    @SerialName("is_admin") val isAdmin: Boolean
)

@Serializable
data class AllMembersResponse(
    val success: Boolean,
    val employee: List<Employee>
)

@Serializable
data class MemberDetailResponse(
    val success: Boolean,
    val employee: Employee,
    val history: List<AttendanceRecord>
)

@Serializable
data class AddEmployeeRequest(
    val name: String,
    val email: String,
    @SerialName("id_number") val idNumber: String,
    @SerialName("join_date") val joinDate: String,
    val designation: String,
    val password: String,
    val position: Int,
    @SerialName("is_admin") val isAdmin: Boolean
)

@Serializable
data class EditEmployeeRequest(
    val uuid: String,
    val name: String? = null,
    val email: String? = null,
    @SerialName("id_number") val idNumber: String? = null,
    @SerialName("join_date") val joinDate: String? = null,
    val designation: String? = null,
    val password: String? = null,
    val position: Int? = null,
    @SerialName("is_admin") val isAdmin: Boolean? = null
)

@Serializable
data class GenericResponse(
    val success: Boolean,
    val message: String? = null
)

@Serializable
data class AddEmployeeResponse(
    val success: Boolean,
    val message: String? = null,
    val id: String? = null
)