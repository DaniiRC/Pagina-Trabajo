# 🚀 TechJob Aggregator (Personal Job Board)

Agregador personal de ofertas de empleo para perfiles técnicos (**Desarrollo Multiplataforma & Sistemas/Redes/DevOps**). Construido con **Java 21**, **Spring Boot 3.4.3**, **Spring Data JPA**, **H2 / PostgreSQL**, tareas programadas mediante **`@Scheduled`** y una interfaz web reactiva en un único archivo (**Tailwind CSS + Vanilla JS**).

Diseñado específicamente para desplegarse de forma 100% gratuita y continua en **[Render](https://dashboard.render.com)** y mantenerse activo 24/7 mediante **UptimeRobot**.

---

## 🌟 Características Principales

- **Consumo 100% Legal de APIs y Feeds Públicos:**
  - 🟣 **Remotive API:** Búsqueda en categorías `software-dev` y `devops-sysadmin`.
  - 🟡 **Arbeitnow API:** Ofertas remotas y europeas con extracción de tags y requisitos.
  - 🟢 **WeWorkRemotely (WWR) RSS:** Lectura de feeds XML en tiempo real para puestos de desarrollo y sistemas.
- **Normalización Inteligente de Tecnologías (`TechnologyParserService`):**
  - Detecta y clasifica automáticamente tecnologías clave: *Java, Spring Boot, Kotlin, Swift, Flutter, Linux, Docker, Kubernetes, AWS, Azure, Redes/Networking, SQL, PostgreSQL, Python, etc.*
- **Deduplicación y Persistencia de Estados:**
  - Evita ofertas repetidas mediante URLs e identificadores externos únicos.
  - Preserva el estado marcado por el usuario: `NUEVA`, `VISTA`, `APLICADA` o `DESCARTADA`.
- **Frontend Moderno en un Único Archivo (`src/main/resources/static/index.html`):**
  - Servido directamente en la raíz `http://localhost:8080/`.
  - Diseño Dark Mode con Tailwind CSS, tarjetas interactivas de KPIs, nube de filtros por tecnologías, buscador en vivo con *debounce*, apertura de oferta con auto-marcado a "Vista" y disparo de sincronización manual con feedback visual.
- **Preparado para Despliegue en Render:**
  - `Dockerfile` multi-stage optimizado para el plan gratuito de Render (512MB RAM).
  - Configuración automática de PostgreSQL a partir de `DATABASE_URL`.
  - Endpoints `/api/ping` y `/api/health` para monitorización keep-alive.

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
4. Variables de Entorno (**Environment Variables**):
   - `PORT` = `8080`
   - `SPRING_PROFILES_ACTIVE` = `prod`
   - `DATABASE_URL` = *(La URL de conexión interna de tu base de datos PostgreSQL en Render)*
5. Pulsa **"Create Web Service"**.

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
