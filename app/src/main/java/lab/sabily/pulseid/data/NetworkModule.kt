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
    @POST("/api/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("/api/profile")
    suspend fun getProfile(): ProfileResponse

    @GET("/api/employee_key")
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

    @Multipart
    @POST("/api/import-employee")
    suspend fun importEmployees(@Part file: MultipartBody.Part): ImportEmployeeResponse
}

class NetworkModule(private val securityManager: SecurityManager) {
    private val json = Json { ignoreUnknownKeys = true }

    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val token = securityManager.getToken()
        
        val requestBuilder = originalRequest.newBuilder()
        requestBuilder.header("Cache-Control", "no-cache")
        if (token != null) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        
        val response = chain.proceed(requestBuilder.build())
        
        if (response.code == 401) {
            securityManager.clearToken()
            // In a real app, we might trigger a re-login event/callback here
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

    val api: AuthApi = Retrofit.Builder()
        .baseUrl("https://pulseid.teamsabily.online")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(AuthApi::class.java)
}