# Archivo municipal: backend y OCR

API del archivo digital de la Municipalidad Distrital de San José. Gestiona expedientes, archivos, usuarios y búsqueda por contenido. Los PDF adjuntos permanecen separados; las imágenes se unen en un PDF en el orden elegido y el OCR se ejecuta automáticamente después de guardar.

Esta guía es para **desarrollo local**. Necesitas también [archivo_frontend](https://github.com/Tade17/archivo_frontend).

## Qué debes iniciar

| Proceso | Inicio | Dirección local |
|---|---|---|
| PostgreSQL 17 | Docker Compose | `localhost:5433` |
| OCR local | Docker Compose | `http://localhost:8091` |
| API Java | Maven Wrapper | `http://localhost:8080` |
| Interfaz Angular | npm, en otra terminal | `http://localhost:4200` |

**Compose no inicia Java ni Angular.** Mantén abiertas las terminales de ambos mientras trabajas.

## 1. Preparar el equipo

- Git.
- **JDK 17**, con `JAVA_HOME` y `PATH` apuntando al JDK.
- Docker Desktop iniciado, con contenedores Linux y Docker Compose v2.
- Para la interfaz: Node.js **24.15 o superior dentro de 24.x**, y npm **11.16.0**.

Comprueba en PowerShell:

```powershell
git --version
java -version
docker version
docker compose version
```

No necesitas Maven global, Python local, GPU ni claves de proveedores OCR. La primera construcción necesita Internet para descargar dependencias y modelos y puede tardar varios minutos. Los modelos quedan en la imagen; el reconocimiento posterior es local.

## 2. Descargar e iniciar PostgreSQL y OCR

Desde la carpeta donde guardarás ambos repositorios:

```powershell
git clone https://github.com/Tade17/archivo_backend.git
Set-Location archivo_backend
docker compose up -d --build
docker compose ps
```

Espera a que ambos servicios estén saludables y verifica:

```powershell
Invoke-RestMethod http://localhost:8091/health
Invoke-RestMethod http://localhost:8091/v2/jobs/config
```

La primera respuesta debe incluir `status: ok` y **`jobsReady: true`**; la segunda debe devolver configuración, no 404. Si sigue iniciando, consulta `docker compose logs --tail 100 ocr`.

## 3. Iniciar la API

En una terminal ubicada en `archivo_backend`:

```powershell
.\mvnw.cmd spring-boot:run
```

En Linux/macOS usa `./mvnw spring-boot:run`, o `sh mvnw spring-boot:run` si el wrapper no tiene permiso de ejecución.

Espera el inicio correcto de Spring Boot y Tomcat en **8080**. Flyway crea el esquema en una base vacía o aplica las migraciones pendientes; actualmente existen **V1 a V13**. Hibernate valida el esquema, no lo crea.

Abre [Swagger](http://localhost:8080/swagger-ui/index.html). No cierres la terminal. Un endpoint protegido devuelve 401 sin token; eso no significa que la API esté caída.

### Primer acceso

La migración [V2__seed_admin.sql](src/main/resources/db/migration/V2__seed_admin.sql) crea el administrador inicial. Consulta sus comentarios **localmente** para los datos de desarrollo y coordina con el responsable si la base ya existía. No publiques esos datos en incidencias o capturas.

Antes de compartir la instalación, crea una cuenta administradora propia y desactiva la cuenta semilla según el procedimiento del equipo. Reiniciar o actualizar no restablece credenciales de una base ya modificada.

### Datos de prueba (solo desarrollo)

Con el backend en marcha, `node seed-demo.mjs` (en `scripts/seed-demo`) carga 30 expedientes, usuarios, préstamos y actividad de ejemplo; se puede repetir sin duplicar. Los pasos completos para un equipo nuevo, después de un `git pull` y para empezar de cero están en [scripts/seed-demo/README.md](scripts/seed-demo/README.md).

Continúa con el [README del frontend](https://github.com/Tade17/archivo_frontend#readme). La API autentica mediante `POST /api/auth/login`; las solicitudes siguientes requieren `Authorization: Bearer <token>`.

## Configuración opcional

Los valores de desarrollo coinciden entre Compose y `application.properties`. **No son una configuración de producción.** PostgreSQL publica el puerto del host en todas las interfaces; OCR está restringido a loopback. No expongas esta configuración directamente a Internet.

| Variables Java | Uso |
|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | PostgreSQL: inicialmente `localhost:5433`, base `archivo_sanjose` |
| `JWT_SECRET` | Firma JWT: secreto propio de al menos 32 bytes antes de compartir |
| `JWT_EXPIRATION_MS` | Vigencia del token; inicialmente 8 horas |
| `STORAGE_BASE_DIR` | Documentos; inicialmente `./storage/documentos`, relativo al directorio de ejecución |
| `STORAGE_MAX_FILE_SIZE_BYTES`, `STORAGE_TIPOS_MIME_PERMITIDOS` | Límite por archivo y formatos admitidos |
| `OCR_ENABLED`, `OCR_URL` | Inicialmente `true` y `http://localhost:8091` |
| `OCR_TIMEOUT_SECONDS`, `OCR_REVIEW_THRESHOLD` | Tiempo de comunicación y confianza mínima |
| `OCR_ARTIFACT_MAX_BYTES` | PDF OCR derivado; inicialmente 128 MiB |
| `OCR_POLL_MILLIS`, `OCR_LEASE_SECONDS` | Consulta de cola y vigencia del trabajo reclamado |

Define las variables en **la misma terminal que inicia Java**; por ejemplo, para otro PostgreSQL:

```powershell
$env:DB_PORT = '5432'
.\mvnw.cmd spring-boot:run
```

En Bash: `DB_PORT=5432 ./mvnw spring-boot:run`. Gestiona los secretos con el mecanismo seguro del equipo, no en archivos versionados ni comandos que vayas a compartir.

Un `.env` junto a Compose configura **Compose**, por ejemplo `OCR_HOST_PORT`; **Spring Boot no lo carga automáticamente**. Cambiar ese puerto requiere también configurar `OCR_URL` en la terminal Java. Cambiar `DB_PASSWORD` solo en Java no cambia la contraseña del servidor ni una base ya existente.

## Actualizar sin perder información

Sigue la [guía de actualización](ACTUALIZACION_BD_DOCKER.md): detener Java, respaldar base y documentos, conservar volúmenes, actualizar, reconstruir OCR, comprobar la cola e iniciar Java. Actualiza después la interfaz.

**Nunca uses `docker compose down -v` para actualizar.** Los datos están repartidos entre:

- Base: volumen `archivo_postgres_data`.
- Originales y PDF derivados: carpeta `STORAGE_BASE_DIR`.
- Trabajos OCR y puntos de control: volumen `archivo_ocr_artifacts`.

Un respaldo PostgreSQL **no incluye documentos ni avances OCR**.

## Carga y OCR automático

1. Selecciona archivos y ordena las imágenes antes de guardar.
2. Se guarda cada PDF por separado y un único PDF para el grupo de imágenes, conservando sus originales.
3. La cola persistente procesa en segundo plano; la interfaz muestra estado, fase y páginas procesadas.
4. El PDF resultante conserva el escaneo y añade texto seleccionable y buscable, no editable.
5. Reintenta los errores desde el documento existente, sin volver a cargarlo.

La interfaz admite hasta 100 archivos, 20 MB por archivo y 150 MB por lote. El grupo de imágenes y su PDF combinado tienen un límite adicional de 20 MB. El OCR admite hasta 100 páginas y 250 millones de píxeles acumulados: el límite de píxeles puede alcanzarse antes de 100 páginas. Se respetan los límites configurados más bajos. Consulta [OCR.md](OCR.md) para recuperación, capacidad y rendimiento.

## Verificar cambios

Pruebas focalizadas sin base de datos:

```powershell
.\mvnw.cmd "-Dtest=OcrClientTest,OcrWorkerTest,OcrQueueSchedulerTest,DocumentoImageBatchServiceTest,FileSystemStorageServiceTest" test
```

**No ejecutes la suite Maven completa contra una base compartida o de producción.** Las pruebas de integración usan la conexión PostgreSQL configurada y un contexto con Flyway. Configura primero una base aislada; el rollback no sustituye ese aislamiento.

Para comprobar una instalación nueva, valida V1–V13 en una base vacía aislada y realiza una carga sintética completa. Esta guía no certifica que esa prueba ya se haya realizado en tu equipo.

## Problemas frecuentes

| Síntoma | Revisión |
|---|---|
| Docker no responde | Inicia Docker Desktop y ejecuta `docker version` |
| Puerto ocupado | Revisa qué proceso lo usa; no inicies una segunda API en el mismo puerto |
| PostgreSQL no conecta | `docker compose ps`, puerto 5433 y variables `DB_*` de la terminal Java |
| `/v2/jobs/config` devuelve 404 | Imagen antigua: `docker compose up -d --build ocr`, espera salud y reinicia Java actualizado |
| Error al iniciar sesión | Revisa qué solicitud falla en Red del navegador y los registros Java |
| HTTP 401 en login | Credenciales vigentes de esa base; reiniciar no restaura la cuenta semilla |
| HTTP 500 después del login | Puede fallar una consulta o vista previa posterior, no la autenticación |
| Flyway falla | Conserva datos y migraciones; no borres volúmenes ni ejecutes `repair` automáticamente |
| Error de loopback en Windows | Usa primero JDK17; consulta [el diagnóstico específico](OCR.md#problema-de-selector-java-en-windows) |

Antes de compartir registros elimina tokens, credenciales y datos de documentos. Un entorno compartido requiere revisar usuarios, secretos, CORS, redes, HTTPS y respaldos; este inicio local no sustituye ese endurecimiento.
