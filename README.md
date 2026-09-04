# 🚀 TechJob Aggregator (Personal Job Board)

Agregador personal de ofertas de empleo para perfiles técnicos (**Desarrollo Multiplataforma & Sistemas/Redes/DevOps**). Construido con **Java 21**, **Spring Boot 3.4.3**, **Spring Data JPA**, **H2 / PostgreSQL**, tareas programadas mediante **`@Scheduled`** y una interfaz web reactiva en un único archivo (**Tailwind CSS + Vanilla JS**).

Diseñado específicamente para desplegarse de forma 100% gratuita y continua en **[Render](https://dashboard.render.com)** y mantenerse activo 24/7 mediante **UptimeRobot**.

---

## 🌟 Características Principales

## 🌟 Fuentes de Empleo Integradas

La aplicación agrega ofertas mediante clientes modulares en `src/main/java/.../client/`:

| Fuente | Tipo de Integración | Requiere Credenciales | Relevancia para España / DAM Junior |
|---|---|---|---|
| 🇪🇸 **Tecnoempleo** | RSS XML público oficial | ❌ Ninguna (100% libre) | **Máxima**: Portal técnico líder en España con ofertas directas en Madrid, Barcelona, remoto y provincias. |
| 🇪🇸 **InfoJobs** | API REST oficial (`/api/1/offer`) | 🔑 `INFOJOBS_CLIENT_ID` y `INFOJOBS_CLIENT_SECRET` | **Máxima**: La mayor bolsa de empleo en España para perfiles junior, graduados de DAM/DAW/FP y prácticas. |
| 🇪🇸 **Adzuna España** | API REST oficial (`/api/jobs/es/...`) | 🔑 `ADZUNA_APP_ID` y `ADZUNA_APP_KEY` (Free tier 250 req/día) | **Alta**: Agregador masivo con filtros por salario y ubicación española. |
| 🇪🇸 **LinkedIn Jobs** | Google Custom Search JSON API (`site:es.linkedin.com/jobs`) | 🔑 `GOOGLE_SEARCH_API_KEY` y `GOOGLE_SEARCH_CX` (100 req/día) | **Media/Alta**: Búsqueda legal sin scraping de puestos técnicos en LinkedIn España. |
| 🌍 **Jobicy** | API REST pública oficial | ❌ Ninguna | **Media**: Ofertas técnicas con filtro geográfico para España y EMEA. |
| 🇪🇸 **GetManfred** | API REST | ❌ Ninguna (Desactivado por defecto) | **Baja para Junior**: Principalmente roles senior/lead (>40k€). |
| 🌍 **Remotive** | API REST pública oficial | ❌ Ninguna | **Media**: Empleo remoto internacional (`software-dev`, `devops`). |
| 🇪🇺 **Arbeitnow** | API REST pública oficial | ❌ Ninguna | **Media**: Empleo técnico en la UE y remoto europeo. |
| 🌍 **WeWorkRemotely** | Feeds RSS públicos | ❌ Ninguna | **Media**: Empleo remoto técnico internacional. |

---

## 📁 Estructura del Proyecto

```text
pagPersonal/
├── Dockerfile                         # Multi-stage build para despliegue en Render
├── render.yaml                        # Blueprint para creación automática en Render
├── build.gradle                       # Configuración de dependencias (Spring Boot 3.4.3, ROME, JPA, PostgreSQL, H2)
├── settings.gradle                    # Nombre del proyecto
├── src/
│   ├── main/
│   │   ├── java/com/jobaggregator/personal/
│   │   │   ├── config/                # CORS, RestClient timeouts, DataSource de Producción
│   │   │   ├── controller/            # JobOfferController, SyncController, HealthController
│   │   │   ├── dto/                   # DTOs para APIs REST, estadísticas y sincronización
│   │   │   ├── model/                 # Entidad JobOffer, Enums JobStatus y JobSource
│   │   │   ├── repository/            # JobOfferRepository con Specs de filtrado y conteos
│   │   │   ├── service/               # JobOfferService, JobSyncService, TechnologyParserService
│   │   │   └── client/                # RemotiveClient, ArbeitnowClient, WwrRssClient
│   │   └── resources/
│   │       ├── application.yml        # Configuración común y cron de sincronización
│   │       ├── application-dev.yml    # Perfil local con H2 Database y consola web
│   │       ├── application-prod.yml   # Perfil de producción para Render con PostgreSQL
│   │       └── static/
│   │           └── index.html         # Dashboard Web SPA (Tailwind CSS + JS)
│   └── test/                          # Tests unitarios e integración (JUnit 5 + MockMvc)
```

---

## 💻 Ejecución en Local (Desarrollo)

### 1. Requisitos
- **Java 21** instalado en tu sistema.
- No requieres tener Maven o Gradle instalados, el proyecto incluye el wrapper `./gradlew` y `./gradlew.bat`.

### 2. Iniciar el Servidor
En la raíz del proyecto, ejecuta en tu terminal:

```bash
# En Windows (PowerShell / CMD)
.\gradlew.bat bootRun

# En Linux / macOS
./gradlew bootRun
```

### 3. Abrir en el Navegador
- **Dashboard Web:** [http://localhost:8080/](http://localhost:8080/)
- **Consola H2 Database:** [http://localhost:8080/h2-console](http://localhost:8080/h2-console)  
  *(JDBC URL: `jdbc:h2:file:./data/jobdb` | User: `sa` | Password: en blanco)*
- **Health Check:** [http://localhost:8080/api/health](http://localhost:8080/api/health)
- **Ping Keep-Alive:** [http://localhost:8080/api/ping](http://localhost:8080/api/ping)

---

## ☁️ Despliegue en Render (https://dashboard.render.com)

El proyecto está 100% configurado para desplegarse en Render de forma gratuita:

### Opción A: Despliegue con Blueprint (`render.yaml`) - *Recomendada*
1. Sube tu código a un repositorio de **GitHub** o **GitLab**.
2. Ve a [dashboard.render.com](https://dashboard.render.com) y pulsa en **"New +" -> "Blueprint"**.
3. Conecta tu repositorio. Render detectará automáticamente el archivo `render.yaml` y creará:
   - El **Web Service** (usando el `Dockerfile`).
   - La base de datos gestionada **PostgreSQL Free Tier**.
4. Pulsa **"Apply"** y Render compilará y desplegará la aplicación automáticamente.

### Opción B: Creación Manual del Web Service
1. En [dashboard.render.com](https://dashboard.render.com), pulsa **"New +" -> "Web Service"**.
2. Selecciona tu repositorio de GitHub.
3. Configuración:
   - **Language / Runtime:** `Docker`
   - **Branch:** `main` (o tu rama activa)
   - **Plan:** `Free`
   - **Health Check Path:** `/api/ping`
4. Variables de Entorno (**Environment Variables**) en Render:

### ⚙️ Guía de Credenciales y Variables de Entorno en Render

Para activar fuentes específicas en Render, ve a tu servicio en Render -> **Environment** y añade las claves deseadas:

#### 1. 🇪🇸 InfoJobs API (Recomendada para perfiles DAM/Junior en España)
- **Registro:** Regístrate en [developer.infojobs.net](https://developer.infojobs.net).
- Crea una nueva aplicación (tipo cliente API).
- Copia tus credenciales e indícalas en Render:
  - `INFOJOBS_CLIENT_ID`: Tu Client ID asignado.
  - `INFOJOBS_CLIENT_SECRET`: Tu Client Secret asignado.

#### 2. 🇪🇸 Adzuna España API (250 consultas/día gratuitas)
- **Registro:** Crea una cuenta en [developer.adzuna.com](https://developer.adzuna.com).
- Genera tus credenciales de desarrollador para la API de España:
  - `ADZUNA_APP_ID`: Tu identificador de aplicación.
  - `ADZUNA_APP_KEY`: Tu clave de aplicación.

#### 3. 🔍 Google Custom Search API para LinkedIn España
- **Google Cloud:** En [console.cloud.google.com](https://console.cloud.google.com), habilita **Custom Search API** y genera una API Key -> Variable `GOOGLE_SEARCH_API_KEY`.
- **Buscador Personalizado:** En [programmablesearchengine.google.com](https://programmablesearchengine.google.com), crea un buscador con el sitio `site:es.linkedin.com/jobs` -> Variable `GOOGLE_SEARCH_CX`.

#### 4. 🎛️ Configuración de Perfiles y Seguridad
- `JOBS_ACTIVE_STUDIES`: Perfiles activos separados por coma. Por defecto: `DAM,DAM_JAVA,DAM_MOBILE,PRACTICAS_BECA`. Permite personalizar qué buscas sin redesplegar.
- `SYNC_SECRET_TOKEN`: Token opcional para proteger `POST /api/sync`. Si se define, requiere la cabecera `X-Sync-Token`.
- `CORS_ALLOWED_ORIGINS`: Dominios permitidos separados por coma (por defecto `http://localhost:*,https://*.onrender.com`).

---

## ⏰ Evitar que Render se Suspenda (UptimeRobot)

El plan gratuito de Render pone en suspensión (*spin down*) las aplicaciones tras 15 minutos de inactividad. Para mantener tu agregador activo 24/7 y que el cron siga ejecutando la sincronización de ofertas periódicamente:

1. Crea una cuenta gratuita en **[UptimeRobot](https://uptimerobot.com/)**.
2. Pulsa en **"Add New Monitor"**.
3. Configura:
   - **Monitor Type:** `HTTP(s)`
   - **Friendly Name:** `TechJob Aggregator Ping`
   - **URL (or IP):** `https://<tu-app-en-render>.onrender.com/api/ping`
   - **Monitoring Interval:** `Every 5 minutes` (o cada 10 minutos)
4. Guarda el monitor. ¡Listo! UptimeRobot enviará un ping cada 5 minutos manteniendo tu servidor de Render despierto de forma ininterrumpida.

---

## 📡 Referencia de la API REST

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/api/jobs` | Lista ofertas con paginación (`?page=0&size=20`), filtros por estado (`?status=NUEVA`), palabras clave (`?keyword=Java`) y tecnología (`?tech=Docker`). |
| `PATCH` | `/api/jobs/{id}/status` | Actualiza el estado de una oferta (`{"status": "APLICADA"}`). |
| `POST` | `/api/jobs/{id}/view` | Marca la oferta como `VISTA` automáticamente si está en estado `NUEVA`. |
| `GET` | `/api/jobs/stats` | Devuelve el desglose de contadores por estado (KPIs) y lista de tecnologías detectadas. |
| `POST` | `/api/sync` | Dispara una sincronización manual inmediata con todas las APIs y feeds RSS. |
| `GET` | `/api/health` | Estado de salud, perfil activo y uso de memoria RAM. |
| `GET` | `/api/ping` | Endpoint ultraligero para monitorización keep-alive. |
