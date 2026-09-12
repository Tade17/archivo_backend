# archivo-backend

Backend para el sistema de digitalización y gestión de archivo físico de la **Municipalidad Distrital de San José**. API REST hecha con Spring Boot para llevar el control de expedientes, su ubicación física (archivo central → estante → nivel → caja), su digitalización, préstamos y auditoría de todo lo que pasa en el sistema.

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

Si preferís usar tu propio Postgres (nativo, sin Docker), creá una base llamada `archivo_sanjose` y saltá al paso 2 usando el profile por defecto (puerto 5432).

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
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | `localhost`, `5432`, `archivo_sanjose`, `postgres`, `1234` | Conexión a Postgres (no aplica si usás el profile `docker`, que fija el puerto a 5433) |
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

- **ADMIN**: acceso total. Es el único rol que puede crear/editar/borrar catálogos (roles, usuarios, áreas, tipos documentales, estados, estructura física del archivo) y ver la auditoría.
- **Cualquier usuario autenticado**: puede crear/editar Expedientes, Documentos Digitales y Préstamos (el trabajo del día a día de un archivista/técnico). Los `DELETE` de esas entidades siguen siendo solo-ADMIN.

Los nombres de rol son datos libres en la tabla `rol`; la convención de autoridad interna es `ROLE_<NOMBRE_EN_MAYUSCULAS>`, así que para que un usuario tenga privilegios de administrador su rol debe llamarse exactamente `ADMIN`.

## Módulos / endpoints

Todos bajo `/api`. Los de catálogo (`roles`, `areas-responsables`, `tipos-documentales`, `estados-expediente`, `archivos-centrales`, `estantes`, `niveles`, `cajas`, `tags`) siguen el mismo patrón CRUD: `POST` / `PUT /{id}` / `GET /{id}` / `GET` (paginado donde aplica) / `DELETE /{id}`.

| Recurso | Base path | Notas |
|---|---|---|
| Auth | `/api/auth/login` | Único endpoint público |
| Usuarios | `/api/usuarios` | Paginado. `DELETE` desactiva (no borra) |
| Roles | `/api/roles` | — |
| Expedientes | `/api/expedientes` | Paginado. Sin `DELETE`: un expediente nunca se borra, su ciclo de vida se maneja con `EstadoExpediente` |
| Documentos digitales | `/api/documentos-digitales` | Ver sección de subida de archivos abajo |
| Préstamos | `/api/prestamos` | `PATCH /{id}/devolver` cierra el préstamo. Filtros `?expedienteId=` / `?solicitanteId=` |
| Auditoría | `/api/auditoria` | Solo lectura, solo ADMIN. Filtros `?entidadAfectada=` + `?entidadId=` |
| Áreas, tipos documentales, estados, archivos centrales, estantes, niveles, cajas, tags | ver arriba | Catálogos, solo-ADMIN para escribir |

### Paginación

Los listados de Expediente, Usuario, Préstamo y Documento Digital devuelven:

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
