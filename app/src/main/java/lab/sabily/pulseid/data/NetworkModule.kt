package lab.sabily.pulseid.data

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import okhttp3.MultipartBody

interface AuthApi {
    @GET("/api/health")
    suspend fun healthCheck(): GenericResponse

    @POST("/api/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("/api/login")
    suspend fun onboardingLogin(@Body request: OnboardingLoginRequest): LoginResponse

    @GET("/api/profile")
    suspend fun getProfile(): ProfileResponse

    @GET("/api/employee/key")
    suspend fun getEmployeeKey(): EmployeeKeyResponse

    @GET("/api/attendance/present")
    suspend fun getPresentEmployees(): List<AttendanceRecord>

    @GET("/api/attendance/left")
    suspend fun getLeftEmployees(): List<AttendanceRecord>

    @GET("/api/attendance/history")
    suspend fun getAttendanceHistory(@Query("all") all: Boolean? = null): AttendanceHistoryResponse

    @GET("/api/attendance/log")
    suspend fun getAttendanceLogs(): AttendanceLogResponse

    @GET("/api/profile")
    suspend fun getAllMembers(@Query("all") all: Boolean = true): AllMembersResponse

    @GET("/api/profile")
    suspend fun getMemberDetail(@Query("uuid") uuid: String): MemberDetailResponse

    @POST("/api/employee/add")
    suspend fun addEmployee(@Body request: AddEmployeeRequest): AddEmployeeResponse

    @retrofit2.http.PATCH("/api/employee/edit")
    suspend fun editEmployee(@Body request: EditEmployeeRequest): GenericResponse

    @Multipart
    @POST("/api/employee/import")
    suspend fun importEmployees(@Part file: MultipartBody.Part): ImportEmployeeResponse

    @POST("/api/settings/holidays/add")
    suspend fun addHoliday(@Body request: AddHolidayRequest): GenericResponse

    @GET("/api/settings/holidays")
    suspend fun getHolidays(): HolidaysResponse

    @GET("/api/settings/work_policy")
    suspend fun getWorkPolicies(): WorkPolicyResponse

    @POST("/api/settings/work_policy/add")
    suspend fun addWorkPolicy(@Body request: AddWorkPolicyRequest): GenericResponse

    @retrofit2.http.PATCH("/api/settings/work_policy/edit")
    suspend fun editWorkPolicy(@Body request: EditWorkPolicyRequest): GenericResponse

    @GET("/api/attendance/leave")
    suspend fun getLeaveRecords(): LeaveResponse

    @POST("/api/attendance/leave/add")
    suspend fun addLeave(@Body request: AddLeaveRequest): GenericResponse

    @retrofit2.http.PATCH("/api/attendance/leave/manage")
    suspend fun manageLeave(@Body request: ManageLeaveRequest): GenericResponse

    @POST("/api/admin/esp32/command")
    suspend fun esp32Command(@Body request: Esp32CommandRequest): Esp32CommandResult

    @GET("/api/admin/esp32/ping")
    suspend fun esp32Ping(): Esp32PingResult

    @POST("/api/iot/esp32/nvs")
    suspend fun generateNvs(@Body request: NvsGeneratorRequest): okhttp3.ResponseBody

    @GET("/api/settings/tz")
    suspend fun getTz(): TimezoneResponse
}

interface AppUpdateService {
    @GET("/api/app/version")
    suspend fun getLatestVersion(@Query("platform") platform: String = "android"): VersionInfo
}

class NetworkModule(private val securityManager: SecurityManager) {
    private val json = Json { ignoreUnknownKeys = true }

    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val token = securityManager.getToken()
        
        val requestBuilder = originalRequest.newBuilder()
        requestBuilder.header("Cache-Control", "no-cache")
        
        // Don't add token to login requests to avoid sending old/expired tokens
        val isLoginRequest = originalRequest.url.encodedPath.contains("/api/login")
        if (token != null && !isLoginRequest) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        
        val response = chain.proceed(requestBuilder.build())
        
        // Only clear token if we actually sent one and got a 401
        if (response.code == 401 && token != null && !isLoginRequest) {
            securityManager.clearToken()
        }
        
        response
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()

    val api: AuthApi get() = buildRetrofit(securityManager.getBaseUrl() ?: BASE_URL).create(AuthApi::class.java)

    val updateApi: AppUpdateService get() = buildRetrofit(securityManager.getBaseUrl() ?: BASE_URL).create(AppUpdateService::class.java)

    private fun buildRetrofit(baseUrl: String): Retrofit {
        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(url)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    companion object {
        const val BASE_URL = "https://pulseid.teamsabily.online"
    }
}
