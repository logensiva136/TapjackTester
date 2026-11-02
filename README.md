# TapjackTester

TapjackTester is a small Android test application designed to determine if an Android application is vulnerable to tapjacking. The app allows you to toggle a **transparent fullscreen red overlay** while keeping normal app interaction intact. If your app accepts input while the overlay is on, you know it is vulnerable to tapjacking.

Specifically, this is made for the "full occlusion" scenario described by the Android docs [here](https://developer.android.com/privacy-and-security/risks/tapjacking#risk_full_occlusion). OWASP MASTG defines static and dynamic tests [here](https://mas.owasp.org/MASTG/tests/android/MASVS-PLATFORM/MASTG-TEST-0035/), but I have found that it is difficult to ensure static tests are comprehensive when considering frameworks like React Native that provide abstractions over the Android `View` object. I also found that the dynamic tests listed in MASTG and elsewhere on GitHub, etc. were outdated, or tested the wrong thing (attempting to collect user input, etc). So, I created this test app. 

[More infomation can be found on my blog.](https://voraci0.us/2025/12/21/android-tapjacking.html)

---

## What this app does

- Displays a **fullscreen transparent red overlay** using `TYPE_APPLICATION_OVERLAY`
- Overlay **persists across apps** (stays visible when switching to other apps)
- Overlay is **non-touchable** — all taps pass through to the underlying app
- Provides a **simple in-app toggle button**
- Toggle remains usable even while the overlay is active
- Runs the overlay inside a **foreground service** (required on modern Android)

---

## What this app does NOT do

- Does not capture, log, or block touch input
- Does not attempt to bypass Android overlay protections
- Does not hide its presence from the system or the user

---

## Requirements

- Android 8.0+ (overlay APIs)
- Android 14+ recommended (tested with `targetSdkVersion = 36`)
- User must manually grant **“Draw over other apps”** permission

---

## Permissions used

- `SYSTEM_ALERT_WINDOW`  
  Required to draw system overlays.

- `FOREGROUND_SERVICE`  
  Required to keep the overlay active across apps.

- `FOREGROUND_SERVICE_SPECIAL_USE`  
  Required when targeting SDK 36+.


## Disclaimer

This project is intended for **security testing, education, and research** only.

Do not deploy or distribute overlay-based applications without user consent or in violation of platform policies.

---

## License

This project is licensed under the MIT License.
See the LICENSE file for details.
