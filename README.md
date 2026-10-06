# Radiografía Crediticia

Aplicación web en **Java 17 + Spring Boot 3.5** para analizar el riesgo y el historial crediticio a partir de
una o varias capturas de pantalla de **MiDataCrédito (Colombia)** y la descripción de la actividad económica del usuario,
usando la **API de Claude (Anthropic)**.

## Identidad visual

- Colores: naranja `#f6664c`, crema `#f1e8e1`, negro `#000000` (y un durazno `#f6e2d1` para campos y tarjetas).
- Logo: `src/main/resources/static/img/` (`logo-icon.png`, `favicon.png`, `logo-wordmark.png` para el PDF).
- Tipografías en `src/main/resources/static/fonts/`:
  - **Chopin** (Medium y ExtraBold) para textos, también incrustada en el PDF. Es una versión *Trial*:
    revisa su licencia antes de usarla comercialmente.
  - **Morphling** para títulos: aún no está en el proyecto. Copia `Morphling-Regular.otf` en esa carpeta y
    quita el comentario del bloque `@font-face` de Morphling en `static/css/styles.css`. Mientras tanto se usa
    **DM Serif Display** (licencia OFL, incluida).

## Reglas de negocio

- **Una radiografía por mes calendario** por usuario. Para pruebas se desactiva con `LIMITE_MENSUAL=false`.
- La **identificación** queda ligada a la cuenta con la primera radiografía exitosa y no se puede cambiar; una
  misma identificación no puede estar en dos cuentas.
- Se puede generar con **capturas**, con el **cuestionario** completo (si no hay capturas) o con ambos.

## Flujo

1. El usuario se registra (`/register`) e inicia sesión (`/login`).
2. En **Inicio** (`/inicio`) entra a *Radiografía de perfil* (`/radiografia/nueva`): sube de 0 a 10 capturas PNG/JPEG,
   responde el cuestionario si no tiene capturas, y cuenta qué ha pasado con su vida crediticia.
3. Las imágenes se codifican en **Base64** y se envían a `https://api.anthropic.com/v1/messages` en un único
   mensaje multimodal (un bloque `image` numerado por captura + un bloque `text` con el prompt).
4. Claude responde con un JSON estructurado: `estimated_score` (150–950), `summary`, `problems` y `recommendations`.
5. El análisis se guarda en la base de datos y se puede descargar como **informe PDF**.

## Estructura

```text
src/main/java/com/radiografiacrediticia/app/
├── config/
│   ├── SecurityConfig.java          # Spring Security: login por formulario, BCrypt, rutas públicas/protegidas
│   └── AnthropicClientConfig.java   # RestClient para la API de Anthropic (URL base, versión, timeouts)
├── controller/
│   ├── AuthController.java          # Login y registro
│   ├── DashboardController.java     # Inicio, formulario de radiografía, resultado, historial y PDF
│   └── GlobalExceptionHandler.java  # Errores de carga de archivos → mensajes amigables
├── dto/
│   ├── AnalysisResult.java          # Resultado estructurado devuelto por Claude
│   ├── Questionnaire.java           # Preguntas alternativas a las capturas
│   └── RegistrationForm.java        # Formulario de registro con validaciones
├── model/
│   ├── User.java                    # Entidad usuario (contraseña con hash BCrypt)
│   └── CreditAnalysis.java          # Entidad del historial de análisis
├── repository/
│   ├── UserRepository.java
│   └── CreditAnalysisRepository.java
├── service/
│   ├── UserDetailsServiceImpl.java  # Autenticación de Spring Security por correo
│   ├── ClaudeAiService.java         # Integración multimodal con la API de Anthropic
│   ├── ClaudeAnalysisException.java # Errores controlados del análisis
│   ├── CreditAnalysisService.java   # Orquestación: análisis + persistencia + historial
│   └── PdfReportService.java        # Generación del PDF en memoria (OpenPDF)
└── RadiografiaCrediticiaApplication.java

src/main/resources/
├── templates/  (login, register, inicio, radiografia-nueva, radiografia, fragments)
├── static/css/styles.css
├── application.properties           # H2, puerto, multipart y configuración de Anthropic
└── application-postgres.properties  # Perfil opcional para PostgreSQL
```

## Requisitos

- JDK 17 o superior
- Maven 3.9+
- Una API key de Anthropic (https://console.anthropic.com/)

## Configurar `ANTHROPIC_API_KEY`

La clave **nunca** se escribe en el código ni en `application.properties`; se lee de la variable de entorno
`ANTHROPIC_API_KEY`.

**Linux / macOS (bash/zsh):**

```bash
export ANTHROPIC_API_KEY="sk-ant-..."
```

Para que sea permanente, agrega esa línea a `~/.bashrc` o `~/.zshrc`.

**Windows (PowerShell):**

```powershell
$env:ANTHROPIC_API_KEY = "sk-ant-..."                              # sesión actual
[Environment]::SetEnvironmentVariable("ANTHROPIC_API_KEY", "sk-ant-...", "User")  # permanente
```

**Windows (CMD):**

```cmd
set ANTHROPIC_API_KEY=sk-ant-...
setx ANTHROPIC_API_KEY "sk-ant-..."
```

**IntelliJ IDEA / Eclipse:** en la configuración de ejecución (*Run Configuration*) agrega la variable de entorno
`ANTHROPIC_API_KEY`.

Si la variable no está definida, la aplicación arranca igual y muestra un mensaje claro al intentar un análisis.

### Modelo

El modelo `claude-3-5-sonnet-20240620` fue **retirado** por Anthropic el 28/10/2025 y ya no responde, por lo que la
aplicación usa su sucesor vigente de la familia Sonnet, `claude-sonnet-5-5`. Se puede cambiar sin recompilar:

```bash
export ANTHROPIC_MODEL=claude-opus-5-5
```

La solicitud usa *structured outputs* (`output_config.format` con un JSON Schema) para garantizar que la respuesta
siempre tenga las llaves `estimated_score`, `summary`, `problems` y `recommendations`, y habilita `fallbacks: "default"`
para que la API reintente con un modelo alterno si el principal declina la solicitud.

## Compilar y ejecutar

```bash
# Compilar y ejecutar las pruebas
mvn clean verify

# Ejecutar en modo desarrollo (H2 en ./data)
mvn spring-boot:run
```

Abre http://localhost:8080, crea una cuenta y entra al panel.

Para generar un JAR ejecutable:

```bash
mvn clean package
java -jar target/radiografia-crediticia-1.0.0.jar
```

### Consola H2 (desarrollo)

http://localhost:8080/h2-console — JDBC URL: `jdbc:h2:file:./data/radiografiacrediticia`, usuario `sa`, sin contraseña.

### PostgreSQL

```bash
export SPRING_PROFILES_ACTIVE=postgres
export DB_URL=jdbc:postgresql://localhost:5432/radiografiacrediticia
export DB_USERNAME=postgres
export DB_PASSWORD=tu_clave
mvn spring-boot:run
```

## Seguridad

- Contraseñas con `BCryptPasswordEncoder`.
- Protección CSRF activa en todos los formularios (Thymeleaf inserta el token automáticamente).
- `/login`, `/register` y los recursos estáticos son públicos; `/inicio`, `/radiografia/**` y el resto requieren sesión.
- Cada usuario solo puede ver y descargar sus propios análisis.
- Cada imagen se valida por su firma binaria (PNG/JPEG), no solo por la extensión.
- Límites: hasta 10 capturas por análisis, 5 MB cada una y 20 MB en total (así la solicitud queda bajo el
  límite de 32 MB de la API de Anthropic). Se ajustan en `ClaudeAiService` (`MAX_IMAGES`, `MAX_TOTAL_IMAGE_BYTES`).
- La consola H2 debe deshabilitarse en producción (`spring.h2.console.enabled=false`, ya desactivada en el perfil `postgres`).

## Aviso

Los resultados son una estimación generada por IA; no constituyen un reporte oficial de DataCrédito Experian ni
asesoría financiera.
