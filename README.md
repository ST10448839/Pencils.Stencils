🎨 Pencils & Stencils

## 📌 Overview

A Kotlin-based Android shopping app for art and stationery supplies. Users can browse products, view product details, manage a shopping bag, and complete a demonstration checkout. Firebase Authentication supports account registration and sign-in. Product data can be loaded from the FastAPI backend, with sample products available locally.

---

## 👤 Group Members

- Keenan Neil Scully – ST10438180
- Raihaan Rajah – ST10448839
- Muhammad Aashiq Petersen – ST10435507
- Teagon Patrick Vermeulen – ST10437198

---

## 🚀 Features

- 🔐 Register and log in with email and password using Firebase Authentication
- 🔑 Sign in with Google
- 🛍️ Browse products and filter by category
- 🖼️ View product photos, descriptions, prices, and suggested products
- 🛒 Add products to the shopping bag, change quantities, and remove items
- 📦 Enter delivery details in the prototype checkout
- ⚙️ Change account and shopping preferences in Settings
- ❓ Read the in-app help and user guide
- 🗃️ Load products from the FastAPI backend or use the local sample catalogue
- 💳 Demo checkout only: no payment is processed and card details are not saved or sent

---

## 🛠️ Technologies Used

- Kotlin
- Android Studio and Android SDK
- Firebase Authentication
- Android Credential Manager and Google sign-in
- FastAPI
- SQLAlchemy
- SQLite
- PostgreSQL support through the backend connection setting
- Gradle

---

## ▶️ How to Use

1. Open the project in Android Studio and run the `app` configuration or install the APK file and open the app.
2. Register with an email and password, or sign in with Google.
3. Browse the collection and select a category or product.
4. Add products to the shopping bag and adjust quantities as needed.
5. Open the bag and continue to the prototype checkout.
6. Use Settings to manage account and shopping preferences; open Help for app instructions.

### Run the optional product API

1. Install Python 3.11 or later.
2. Run `backend/Start API.bat` on Windows and keep its terminal open.
3. Check `http://localhost:8000/health` to confirm that the API is running.
4. The Android emulator connects to `http://10.0.2.2:8000/`. The API seeds ten sample products and ten synthetic API users in its local SQLite database.

---

## 🎥 Link to Demonstration Video: (YouTube)

https://youtu.be/D-9ucvWjdZ4?si=Hj_z8up7dFnRue6P 

---

## 📦 APK Download

The APK file for the app has been added to the GitHub Repository.
