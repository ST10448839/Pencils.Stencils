# Pencils & Stencils Android prototype

This native Android prototype follows the mobile wireframes in Task 1: login, registration, home, product collection, shopping bag, settings, and help. It is a local demonstration build; no online API, hosted database, Azure DevOps project, or OAuth credentials were included with the supplied starter project or Task 1 document.

## Run in Android Studio

1. Open this folder in Android Studio and let Gradle sync.
2. Select an Android emulator or device running Android 7.0 (API 24) or later.
3. Run the `app` configuration.
4. To run the automated UI flow, use **Run > Run 'MainFlowTest'** or run `gradlew.bat :app:connectedDebugAndroidTest` with an emulator attached.

This folder uses the Android Studio build versions supplied with the original project (AGP 9.0.1, Gradle 9.2.1, SDK 36.1). No JDK path is pinned in the project; open it in Android Studio and use its normal Gradle JDK selection. The app itself is Kotlin.





