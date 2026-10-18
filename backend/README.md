# Pencils & Stencils API

This REST API is connected to the Android app for registration, login, logout, and product listing. It uses SQLite for local development and can use Azure Database for PostgreSQL when deployed. API documentation is available at `/docs` while the server is running.

## Endpoints

- `GET /health`
- `GET /api/products`
- `POST /api/auth/register` with `{ "name", "email", "password" }`
- `POST /api/auth/login` with `{ "email", "password" }`
- `GET /api/me` with `Authorization: Bearer <token>`
- `POST /api/auth/logout` with the bearer header

Passwords are stored as salted PBKDF2-HMAC-SHA256 hashes. The API returns a random bearer token after registration or login. Token hashes are stored in the database and sessions expire after 30 days. Ten synthetic demo accounts and ten products are seeded when the API starts.

## Run locally on Windows

1. Install Python 3.11 or newer.
2. Double-click `Start API.bat` in this folder and leave its window open while using the emulator. It installs the packages from `requirements.txt` and starts the API.
3. Open `http://localhost:8000/health` on your computer. For API docs, open `http://localhost:8000/docs`.
4. The Android emulator uses `http://10.0.2.2:8000/`; this is set in `ApiClient.BASE_URL`. If using a physical Android phone, use your computer's local network IP in `BASE_URL` and connect both devices to the same Wi-Fi.

Demo login: `demo01@example.test` / `DemoPass123!` (demo02 through demo10 also work).

The local HTTP endpoint is for development only. The Android debug build allows it; release builds should use HTTPS.

## Deploy to Azure

This source is ready to deploy, but it is not deployed into your Azure subscription yet. You need an Azure App Service and an Azure Database for PostgreSQL Flexible Server.

1. Create a PostgreSQL database and allow the App Service to reach it through its networking/firewall configuration.
2. Deploy the contents of this `backend` folder to a Linux App Service using Python 3.11 or newer.
3. In the App Service's **Configuration > Application settings**, set `DATABASE_URL` to a connection string in this format: `postgresql+psycopg://USER:PASSWORD@SERVER:5432/DATABASE?sslmode=require`. Keep the password in the Azure setting; do not put it in this repository.
4. Set the App Service startup command to `gunicorn --worker-class uvicorn.workers.UvicornWorker --bind=0.0.0.0:8000 api:app`.
5. After Azure gives the App Service its HTTPS hostname, change `ApiClient.BASE_URL` in the Android app to `https://YOUR-APP.azurewebsites.net/`, then rebuild the app.
6. Verify `https://YOUR-APP.azurewebsites.net/health` and `/docs`.

Azure App Service's Python startup configuration is described in [Microsoft's Python App Service guide](https://learn.microsoft.com/en-us/azure/app-service/configure-language-python). The Android app's demo Google button is not live SSO yet; Google OAuth/Firebase project settings and server-side ID-token verification still need to be configured.

## Add products

Add a tuple to `PRODUCTS` in `api.py`: `(11, "Watercolour Brush Set", "Six detail brushes.", 19900, "Tools")`. The ID must be unique; prices are cents, so `19900` is R199.00. Restart the API. It inserts products it hasn't seen before and preserves existing products and users. To add a product image, include it under `app/src/main/res/drawable-nodpi` and map its ID in `MainActivity.kt`'s `collectionScreen()` image selection.
