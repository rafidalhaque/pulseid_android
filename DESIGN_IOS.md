# PulseID iOS App Design Document

This document outlines the architecture, features, and technical specifications required to build the PulseID iOS application. The goal is to achieve feature parity and structural similarity with the existing Android application, ensuring consistent behavior across both platforms.

## 1. Overview
PulseID is an employee attendance and HR management application. Its core functionality revolves around using Bluetooth Low Energy (BLE) to broadcast a daily-rotating, cryptographically secure UUID to physical receivers (ESP32 IoT devices) for attendance tracking. It also provides comprehensive HR management features for both standard employees and administrators.

## 2. Core Architecture
The iOS app should follow a modern, declarative architecture mirroring the Android implementation:
*   **UI Framework**: **SwiftUI** (equivalent to Android's Jetpack Compose).
*   **Architecture Pattern**: **MVVM (Model-View-ViewModel)**. ViewModels will manage state and encapsulate business logic using `@Observable` or `ObservableObject`.
*   **Concurrency**: **Swift Concurrency (`async/await`)** (equivalent to Kotlin Coroutines).
*   **Networking**: **URLSession** or **Alamofire** with **Codable** for JSON parsing (equivalent to Retrofit + Kotlinx Serialization).
*   **Dependency Injection**: Can use a lightweight approach like Factory or simple environment injection, mirroring the manual DI used in Android.

## 3. Key Modules & Technical Specifications

### 3.1. Authentication & Security (`SecurityManager`)
*   **Secure Storage**: Use **Keychain Services** (via a wrapper) to securely store sensitive data. This replaces Android's `EncryptedSharedPreferences`.
*   **Stored Items**:
    *   `auth_token` (Bearer token for API)
    *   `employee_uuid` (User's unique identifier)
    *   `app_lock_pin` (Hashed user PIN)
    *   `app_lock_enabled` / `app_lock_biometric_enabled` (Preferences)
*   **App Lock**:
    *   Implement a custom PIN entry screen in SwiftUI.
    *   Use the **LocalAuthentication** framework (`LAContext`) to support FaceID/TouchID.

### 3.2. BLE Advertising (`BleAdvertiser` & `BleKeyManager`)
This is the most critical feature. The iOS app must generate the exact same daily UUID to be recognized by the ESP32 receivers.
*   **Framework**: **CoreBluetooth** (`CBPeripheralManager`).
*   **UUID Generation Algorithm**:
    1.  Fetch the `master_secret` from the API (`/api/employee/key`).
    2.  Construct the input string: `employeeUuid + "yyyy-MM-dd"` (Date formatted in `Asia/Dhaka` timezone).
    3.  Generate an HMAC-SHA256 hash using the `master_secret` as the key and the input string as the data.
    4.  Extract the first 16 bytes of the resulting hash.
    5.  Construct a 128-bit `UUID` using these 16 bytes.
*   **Advertising**: Broadcast this UUID using `CBPeripheralManager.startAdvertising`. **Note**: iOS restricts background BLE advertising. The app requires location permissions and specific `Info.plist` keys (`NSBluetoothPeripheralUsageDescription`, `UIBackgroundModes` with `bluetooth-peripheral`).

### 3.3. Networking & API Paths (`NetworkModule`)
*   **Base URL**: `https://pulseid.teamsabily.online`
*   **Interceptors**: Auto-inject the `Authorization: Bearer <token>` header into all calls. Handle 401 Unauthorized by logging the user out.
*   **Endpoints**:
    *   **Auth**:
        *   `POST /api/login` - Authenticate user.
        *   `GET /api/employee/key` - Fetch master secret for BLE.
    *   **Profile / Members**:
        *   `GET /api/profile` - Fetch current user profile.
        *   `GET /api/profile?all=true` - Fetch all members (Admin).
        *   `GET /api/profile?uuid={uuid}` - Fetch specific member details (Admin).
    *   **Attendance**:
        *   `GET /api/attendance/present` - List currently present employees.
        *   `GET /api/attendance/left` - List employees who have left.
        *   `GET /api/attendance/history` - User's attendance history (Admin can append `?all=true`).
        *   `GET /api/attendance/log` - Raw BLE scan logs.
    *   **Leaves**:
        *   `GET /api/attendance/leave` - Fetch leave records.
        *   `POST /api/attendance/leave/add` - Request a leave.
        *   `PATCH /api/attendance/leave/manage` - Approve/Reject leave (Admin).
    *   **Employee Management (Admin)**:
        *   `POST /api/employee/add` - Add new employee.
        *   `PATCH /api/employee/edit` - Edit employee details.
        *   `POST /api/employee/import` (Multipart) - Import employees via CSV.
    *   **Settings (Admin)**:
        *   `GET /api/settings/holidays` - Fetch holidays.
        *   `POST /api/settings/holidays/add` - Add new holiday.
        *   `GET /api/settings/work_policy` - Fetch work policies.
        *   `POST /api/settings/work_policy/add` - Add new work policy.
        *   `PATCH /api/settings/work_policy/edit` - Edit work policy.
    *   **IoT ESP32 Management (Admin)**:
        *   `GET /api/admin/esp32/ping` - Ping devices.
        *   `POST /api/admin/esp32/command` - Send device command.
        *   `POST /api/iot/esp32/nvs` - Generate NVS configuration.

### 3.4. App Update Checking (`AppUpdateService`)
*   **Endpoint**: `GET /api/app/version?platform=ios` (Change default `android` to `ios`).
*   **Flow**: On app launch (in the main view's `onAppear` or via a dedicated view model), the app checks the latest available version against the current installed version.
*   **UI**: If a newer version is available, present a standard SwiftUI `Alert` prompting the user to update ("Update Available"), with actions to "Update Now" (redirects to App Store/Download URL) or "Later".

## 4. Features & UI Screens
The UI should map 1-to-1 with the Android Compose screens, utilizing SwiftUI navigation (`NavigationStack`, `TabView`).

### 4.1. Common/Employee Features
*   **Login**: UUID and Password input.
*   **Main Dashboard (Tab View)**:
    *   **At A Glance**: Today's attendance status.
    *   **History**: Attendance logs (entries/exits, late, early, overtime).
    *   **Leave**: Leave requests and history.
    *   **Profile**: User details and logout.
*   **App Lock**: Shown on app resume if enabled.

### 4.2. Admin Features (Shown conditionally based on `isAdmin`)
*   **Employee Management**: Add, Edit, Import (CSV/Multipart), View all members.
*   **Attendance Management**: View present/left employees, raw attendance logs.
*   **Leave Management**: Approve/Reject leave requests.
*   **Settings**: Manage Holidays and Work Policies.
*   **IoT (ESP32) Management**: Ping devices, send commands, generate NVS configs.

## 5. Design & Styling
*   **Theming**: Support system-wide Light and Dark mode using SwiftUI's environment.
*   **Typography & Layout**: Standard iOS typography (San Francisco) with Material-like card layouts for data presentation.
*   **Color Palette (Hex Codes)**:
    *   **Light Theme**:
        *   Primary: `#6650A4` (Purple40)
        *   Secondary: `#625B71` (PurpleGrey40)
        *   Tertiary: `#7D5260` (Pink40)
        *   Background & Surface: `#FDFDFD` (AppBackground)
        *   On-Background/On-Surface (Text): `#1C1B1F`
    *   **Dark Theme**:
        *   Primary: `#D0BCFF` (Purple80)
        *   Secondary: `#CCC2DC` (PurpleGrey80)
        *   Tertiary: `#EFB8C8` (Pink80)

## 6. Permissions & Plist Configurations
Required entries in `Info.plist`:
*   `NSBluetoothAlwaysUsageDescription`
*   `NSBluetoothPeripheralUsageDescription`
*   `NSFaceIDUsageDescription` (for Biometric App Lock)
*   `UIBackgroundModes`: `bluetooth-peripheral` (to allow BLE broadcasting when app is in the background, subject to iOS limitations).

## 7. CI/CD & Automation (GitHub Actions)

To maintain parity with the Android release process, the iOS app should utilize a GitHub Actions workflow triggered on pushes to the `master` branch.

### 7.1. Workflow Responsibilities
*   **Environment**: `runs-on: macos-latest`.
*   **Build & Sign**:
    1.  Install Apple certificates and provisioning profiles (stored as Base64 encoded secrets).
    2.  Use `xcodebuild` to archive and export the app as a signed `.ipa` file.
*   **Version Extraction**: Extract `CFBundleShortVersionString` (Version Name) and `CFBundleVersion` (Version Code) from the project settings or `Info.plist`.
*   **GitHub Release**: Automatically create a new release tag (e.g., `v1.x.x`) and upload the `.ipa` as a release asset.
*   **Telegram Notification**: Upload the `.ipa` to the Telegram release channel using the Telegram Bot API (`/sendDocument`).
    *   **Endpoint**: `POST https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendDocument`
*   **Backend Upload**: Upload the `.ipa` to the PulseID backend to support in-app update checks.
    *   **Endpoint**: `POST https://pulseid.teamsabily.online/api/app/release`
    *   **Headers**: `X-RELEASE-KEY: ${{ secrets.RELEASE_KEY }}`
    *   **Form Data**:
        *   `platform`: `ios`
        *   `released_file`: `@path/to/pulseid.ipa`
        *   `version_code`: extracted build number
        *   `version_name`: extracted version string

### 7.2. Required Secrets
*   `APPLE_CERTIFICATE_BASE64`: P12 certificate for signing.
*   `APPLE_CERTIFICATE_PASSWORD`: Password for the P12 file.
*   `APPLE_PROVISIONING_PROFILE_BASE64`: Provisioning profile for the app.
*   `RELEASE_KEY`: Secret key for backend API authentication.
*   `TELEGRAM_BOT_TOKEN`: Token for the notification bot.
*   `TELEGRAM_CHAT_ID`: ID of the Telegram channel/chat.
*   `GITHUB_TOKEN`: Provided by GitHub for creating releases.