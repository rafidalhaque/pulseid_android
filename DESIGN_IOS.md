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
    *   `app_lock_enabled` / `app_lock_biometric_enabled` / `app_lock_interval` (Preferences)
    *   `ntfy_topic` / `ntfy_token` (Push notification credentials)
*   **App Lock**:
    *   Implement a custom PIN entry screen in SwiftUI.
    *   Use the **LocalAuthentication** framework (`LAContext`) to support FaceID/TouchID.
    *   Support a configurable idle timeout interval (e.g., Immediately, 15s, 30s, 1m, 5m) before the app automatically locks.

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
*   **Interceptors**: Auto-inject the `Authorization: Bearer <token>` header into all calls. Handle 401 Unauthorized by clearing the token and redirecting the user:
    *   If a **Server URL** is already configured, redirect to the **Login** screen.
    *   If no server is configured, redirect to the **Welcome** screen.
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

### 3.4. Push Notifications via ntfy (`NtfyService`)
iOS should implement both real-time notifications (SSE) while the app is active and periodic polling for background updates.
*   **ntfy Server**: `https://notify.teamsabily.qzz.io`
*   **Credentials**: Stored in Keychain (`ntfy_topic` and `ntfy_token`).
*   **Real-time (Active)**: Use **Server-Sent Events (SSE)** to receive instant notifications when the app is in the foreground.
*   **Background (Inactive)**: Use **Background Tasks** framework to poll ntfy periodically (every 15-30 mins).
*   **Polling Endpoint**: `GET /{ntfy_topic}/json?poll=1&since=15m` with `Authorization: Bearer {ntfy_token}`.
*   **Logic**:
    *   Parse NDJSON response.
    *   If `event == "message"`, show a local `UNUserNotificationCenter` notification.
    *   `title` = message title, `body` = message body.
    *   Set notification category/action to handle the `click` URL.
*   **Deep Linking**:
    *   Support `pulseid://` scheme.
    *   `pulseid://leaves/pending` -> Admin Panel -> Leave Management.
    *   `pulseid://leaves/my` -> Requests.
*   **Trigger**: Start SSE connection on login/app launch, schedule background task, cancel on logout.

### 3.5. App Update Checking (`AppUpdateService`)
*   **Endpoint**: `GET /api/app/version?platform=ios` (Change default `android` to `ios`).
*   **Flow**: On app launch or manual check in **Settings**, the app checks the latest available version.
*   **UI**: If a newer version is available, present a standard SwiftUI `Alert` prompting the user to update ("Update Available"), with actions to "Update Now" (redirects to App Store/Download URL) or "Later".

### 3.6. QR Utilities
*   **Scanning**: Use **AVFoundation** (`AVCaptureMetadataOutput`) or **Vision** framework to scan QR codes for Server URL and Employee ID.
*   **Generation**: Use **Core Image** (`CIFilter(name: "CIQRCodeGenerator")`) to generate QR code images from employee UUIDs (Member Details) and Server URL (Admin Settings).

## 4. Features & UI Screens
The UI should map 1-to-1 with the Android architecture, utilizing SwiftUI navigation (`NavigationStack`, `TabView`).

### 4.1. Common/Employee Features
*   **Onboarding**: 
    *   **Welcome**: Brief introduction and "Get Started" call to action.
    *   **Server Setup**: Manual entry of server URL or QR code scan.
    *   **Login (Initial & Re-auth)**: 
        *   Used for both first-time setup and subsequent logins.
        *   **Employee ID Scanning**: Supports QR code scan.
        *   **Smart Pre-fill**: Automatically pre-fills stored ID and sets to Read-Only during re-auth.
        *   **Clear Data**: Reset setup by wiping stored credentials.
*   **Main Dashboard (TabView)**: 4 fixed tabs. Active tab uses **filled** icon, inactive uses **outlined** variant.
    1.  **Home**: BLE status, today's attendance summary, and quick actions.
    2.  **Activity**: Full attendance history (entries/exits, late, early, overtime).
    3.  **Requests**: Submission and status of requests (Leave, etc.).
    4.  **Profile**: Hub for user info and navigation to settings/admin.
*   **Profile (Hub Layout)**: Uses a list/lazy column with:
    *   **Member Card**: Name, role, ID, and UUID.
    *   **Settings Row**: Navigates to **Settings Screen**.
    *   **Notifications Row**: Navigates to **Notifications Screen** (stub).
    *   **Admin Panel Row**: (Admin only) Navigates to **Admin Panel Menu**.
    *   **Logout Button**: (At bottom).
*   **Settings Screen**:
    *   **App Lock**: Enable/Disable, PIN setup, Biometric toggle, Auto-lock interval.
    *   **App Updates**: Current version info and "Check for Updates" button.
*   **App Lock**: Shown on app resume if enabled.

### 4.2. Admin Features (Nested under Admin Panel Menu)
Accessed via **Profile -> Admin Panel**.
*   **Members**: All employees list, Add/Edit employee, Member Details (with QR generator).
*   **Attendance Report**:
    *   Attendance Sheet (Present/Left list).
    *   At A Glance (Monthly summary).
    *   Attendance Logs (Raw BLE data).
*   **Holidays**: Manage office holidays.
*   **Work Policy**: Manage work hours and policies.
*   **Leave Management**: Approve/Reject leave requests.
*   **Import Employees**: Bulk import via CSV.
*   **Server Settings**: View current server URL, copy to clipboard, and view scannable QR.
*   **ESP32 Management**: Ping devices, send commands, generate NVS configs.

## 5. Design & Styling
*   **Theming**: Support system-wide Light and Dark mode using SwiftUI's environment.
*   **Typography & Layout**: Standard iOS typography (San Francisco) with Material-like card layouts for data presentation.
*   **Navigation Style**: Use `NavigationLink` with a trailing chevron (KeyboardArrowRight) for hub navigation rows.
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
