package lab.sabily.pulseid.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import lab.sabily.pulseid.data.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import okhttp3.MultipartBody

@OptIn(ExperimentalCoroutinesApi::class)
class Esp32ManagementViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: Esp32ManagementViewModel
    private lateinit var fakeAuthApi: FakeAuthApi

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeAuthApi = FakeAuthApi()
        viewModel = Esp32ManagementViewModel(fakeAuthApi)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `ping success updates state correctly`() = runTest {
        fakeAuthApi.shouldSucceed = true
        viewModel.ping()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.isDeviceOnline)
        assertNotNull(state.pingResult)
        assertEquals(state.pingResult, state.lastSeenPingResult)
        assertFalse(state.isPingLoading)
    }

    @Test
    fun `ping failure updates state correctly`() = runTest {
        fakeAuthApi.shouldSucceed = false
        viewModel.ping()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isDeviceOnline)
        assertEquals(null, state.pingResult)
        assertFalse(state.isPingLoading)
    }

    private class FakeAuthApi : AuthApi {
        var shouldSucceed = true

        override suspend fun login(request: LoginRequest): LoginResponse = TODO()
        override suspend fun getProfile(): ProfileResponse = TODO()
        override suspend fun getEmployeeKey(): EmployeeKeyResponse = TODO()
        override suspend fun getPresentEmployees(): List<AttendanceRecord> = TODO()
        override suspend fun getLeftEmployees(): List<AttendanceRecord> = TODO()
        override suspend fun getAttendanceHistory(all: Boolean?): AttendanceHistoryResponse = TODO()
        override suspend fun getAttendanceLogs(): AttendanceLogResponse = TODO()
        override suspend fun getAllMembers(all: Boolean): AllMembersResponse = TODO()
        override suspend fun getMemberDetail(uuid: String): MemberDetailResponse = TODO()
        override suspend fun addEmployee(request: AddEmployeeRequest): AddEmployeeResponse = TODO()
        override suspend fun editEmployee(request: EditEmployeeRequest): GenericResponse = TODO()
        override suspend fun importEmployees(file: MultipartBody.Part): ImportEmployeeResponse = TODO()
        override suspend fun addHoliday(request: AddHolidayRequest): GenericResponse = TODO()
        override suspend fun getHolidays(): HolidaysResponse = TODO()
        override suspend fun getWorkPolicies(): WorkPolicyResponse = TODO()
        override suspend fun addWorkPolicy(request: AddWorkPolicyRequest): GenericResponse = TODO()
        override suspend fun editWorkPolicy(request: EditWorkPolicyRequest): GenericResponse = TODO()
        override suspend fun getLeaveRecords(): LeaveResponse = TODO()
        override suspend fun addLeave(request: AddLeaveRequest): GenericResponse = TODO()
        override suspend fun manageLeave(request: ManageLeaveRequest): GenericResponse = TODO()
        override suspend fun esp32Command(request: Esp32CommandRequest): Esp32CommandResult {
            return Esp32CommandResult(shouldSucceed)
        }
        override suspend fun esp32Ping(): Esp32PingResult {
            return if (shouldSucceed) {
                Esp32PingResult(success = true, ntp_time = "2026-05-12T09:20:00+06:00")
            } else {
                Esp32PingResult(success = false)
            }
        }
    }
}
