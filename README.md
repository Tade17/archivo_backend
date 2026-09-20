# archivo-backend

Backend para el sistema de archivo digital de la **Municipalidad Distrital de San José**. La API administra expedientes digitales, documentos, usuarios, permisos y auditoría. El sistema ya no registra ubicaciones físicas, cajas ni préstamos.

> Si ya tienes una base en Docker, sigue [ACTUALIZACION_BD_DOCKER.md](ACTUALIZACION_BD_DOCKER.md). No es necesario borrar el volumen: Flyway aplica las migraciones pendientes al iniciar el backend.

## Stack

- Java 17 + Spring Boot 4.1
- PostgreSQL (esquema versionado con Flyway)
- Spring Security + JWT (autenticación y autorización por rol)
- Spring Data JPA / Hibernate
- Maven

## Requisitos

- JDK 17
- Docker Desktop (para levantar Postgres — ver abajo) **o** una instancia de Postgres 17 propia

No hace falta instalar Maven: el repo trae el wrapper (`./mvnw` / `mvnw.cmd`).

## Levantar el proyecto

### 1. Base de datos

La forma más simple es con el Postgres que ya viene armado en `docker-compose.yml`:

```bash
docker compose up -d
```

Esto levanta Postgres 17 en el **puerto 5433** del host (no 5432, para no chocar con un Postgres nativo que ya tengas instalado para otros proyectos). Los datos quedan en un volumen con nombre — `docker compose down` no los borra; `docker compose down -v` sí, si en algún momento querés arrancar de cero.

Si prefieres usar tu propio PostgreSQL, crea una base llamada `archivo_sanjose` y configura `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` y `DB_PASSWORD`.

### 2. Correr la aplicación

Con el Postgres de Docker (puerto 5433), activá el profile `docker`:

```bash
# Windows (PowerShell)
$env:SPRING_PROFILES_ACTIVE="docker"; ./mvnw spring-boot:run

# Bash / Git Bash
SPRING_PROFILES_ACTIVE=docker ./mvnw spring-boot:run
```

Con un Postgres propio en el puerto 5432 (default), simplemente:

```bash
./mvnw spring-boot:run
```

Al arrancar, Flyway crea el esquema completo y siembra un rol `ADMIN` y un usuario administrador (ver credenciales abajo). La app queda escuchando en `http://localhost:8080`.

### Variables de entorno (todas opcionales, tienen default para desarrollo)

| Variable | Default | Para qué |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | `localhost`, `5433`, `archivo_sanjose`, `postgres`, `1234` | Conexión a PostgreSQL |
| `JWT_SECRET` | un valor de desarrollo, **cambiar en producción** | Clave para firmar los JWT (HMAC-SHA256, mínimo 32 bytes) |
| `JWT_EXPIRATION_MS` | `28800000` (8 horas) | Vigencia del token |
| `STORAGE_BASE_DIR` | `./storage/documentos` | Carpeta donde se guardan los documentos digitalizados subidos |
| `STORAGE_MAX_FILE_SIZE_BYTES` | `20971520` (20 MB) | Tamaño máximo por archivo |
| `STORAGE_TIPOS_MIME_PERMITIDOS` | `application/pdf,image/jpeg,image/png,image/tiff` | Lista blanca de tipos MIME aceptados al subir un documento |

## Usuario administrador semilla

Para poder usar la API por primera vez (todo excepto `/api/auth/login` requiere estar autenticado):

```
correo:    admin@sanjose.gob.pe
password:  CambiarInmediatamente123!
```

Cambiá esta contraseña (o desactivá este usuario y creá uno nuevo) antes de usar esto en un ambiente que no sea de desarrollo.

## Autenticación

```
POST /api/auth/login
{ "correo": "admin@sanjose.gob.pe", "password": "CambiarInmediatamente123!" }
```

Devuelve un JWT. Mandalo en cada request siguiente como:

```
Authorization: Bearer <token>
```

### Roles y permisos

- **ADMIN**: acceso total; administra usuarios y catálogos y consulta la actividad de seguridad.
- **GESTOR_DOCUMENTAL**: crea y edita expedientes y carga documentos. No accede a usuarios, catálogos ni auditoría.
- **LECTOR**: busca, visualiza y descarga documentos sin modificarlos.

Los nombres de rol son datos libres en la tabla `rol`; la convención de autoridad interna es `ROLE_<NOMBRE_EN_MAYUSCULAS>`, así que para que un usuario tenga privilegios de administrador su rol debe llamarse exactamente `ADMIN`.

## Módulos / endpoints

Todos los endpoints están bajo `/api`. Las áreas y los tipos documentales son los catálogos activos de este flujo digital.

| Recurso | Base path | Notas |
|---|---|---|
| Auth | `/api/auth/login` | Único endpoint público |
| Usuarios | `/api/usuarios` | Paginado. `DELETE` desactiva (no borra) |
| Roles | `/api/roles` | — |
| Expedientes | `/api/expedientes` | Paginado. Sin `DELETE`: un expediente nunca se borra, su ciclo de vida se maneja con `EstadoExpediente` |
| Documentos digitales | `/api/documentos-digitales` | Ver sección de subida de archivos abajo |
| Auditoría | `/api/auditoria` | Solo lectura, solo ADMIN. Filtros `?entidadAfectada=` + `?entidadId=` |
| Áreas y tipos documentales | `/api/areas-responsables`, `/api/tipos-documentales` | Catálogos, solo ADMIN para escribir |

### Paginación

Los listados de Expediente, Usuario y Documento Digital devuelven:

```json
{ "contenido": [...], "pagina": 0, "tamano": 20, "totalElementos": 42, "totalPaginas": 3 }
```

Se controla con query params estándar de Spring Data: `?page=0&size=20&sort=nombre,desc`.

### Subir documentos digitalizados

`POST /api/documentos-digitales` es `multipart/form-data` (no JSON) y acepta **uno o varios archivos en el mismo request** (pensado para digitalizar en lote):

```
POST /api/documentos-digitales
Content-Type: multipart/form-data

expedienteId=<uuid>
tecnicoResponsableId=<uuid>
escanerUtilizado=Epson V600      (opcional)
resolucionDpi=300                (opcional)
formatoSalida=PDF                (opcional)
archivos=<archivo1.pdf>
archivos=<archivo2.pdf>
```

El servidor calcula el hash SHA-256 y valida tipo MIME/tamaño antes de guardar — no se puede mandar la ruta o el hash a mano. `GET /api/documentos-digitales/{id}/archivo` descarga el binario real (y queda registrado en auditoría).

## Documentación interactiva (Swagger)

Con la app corriendo, abrí:

```
http://localhost:8080/swagger-ui/index.html
```

Ahí se ve la lista completa de endpoints (generada automáticamente desde el código, no a mano) y se pueden probar directo desde el navegador. Para probar un endpoint protegido: primero `POST /api/auth/login`, copiá el `token` de la respuesta, apretá el botón **Authorize** (arriba a la derecha) y pegalo ahí (sin el prefijo `Bearer`) — Swagger lo va a mandar solo en cada request siguiente.

## Tests

```bash
./mvnw test
```

Son tests de integración (contra una Postgres real, la misma que uses en desarrollo) envueltos en una transacción que se revierte al final de cada test — no ensucian la base.

## Estructura del proyecto

```
controller/    Endpoints REST
service/       Interfaces de negocio + impl/
repository/    Spring Data JPA
entity/        Entidades JPA
dto/           Request/Response DTOs (records)
mapper/        Entity <-> DTO
security/      JWT (filtro, servicio de tokens, UserDetailsService)
audit/         Aspecto AOP que graba la tabla auditoria
storage/       Abstracción de almacenamiento de archivos (filesystem local)
exception/     Excepciones de negocio + GlobalExceptionHandler
config/        Configuración de Spring Security
```
