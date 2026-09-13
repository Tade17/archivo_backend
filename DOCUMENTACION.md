# Documentación técnica — archivo-backend

Referencia completa del backend, pensada para quien va a **consumir la API desde el frontend**. Para instrucciones de instalación/arranque, ver [`README.md`](./README.md) — acá se asume que ya tenés la app corriendo en `http://localhost:8080`.

**Convenciones de acceso usadas en toda esta doc:**
- 🔓 público, no requiere token
- 🔐 requiere JWT válido (cualquier usuario autenticado, cualquier rol)
- 🔐👑 requiere JWT válido **y** rol `ADMIN`

---

## 1. Arquitectura general

```
Controller  →  Service (interfaz)  →  ServiceImpl  →  Repository (Spring Data JPA)
                    ↑
                 Mapper (Entity <-> DTO)
```

- Cada módulo (Usuario, Expediente, etc.) tiene su propio Controller, Service/ServiceImpl, Repository, Entity, Mapper y DTOs de request/response. Nada de lógica de negocio en los Controllers.
- Los DTOs son `record` de Java (inmutables). Nunca se expone la entidad JPA directamente en una respuesta HTTP.
- Las excepciones de negocio (`RecursoNoEncontradoException`, `RecursoDuplicadoException`, etc.) las traduce un `GlobalExceptionHandler` centralizado a códigos HTTP — ver sección 5.

### Stack
Java 17 · Spring Boot 4.1 · Spring Security + JWT · Spring Data JPA / Hibernate · PostgreSQL · Flyway · Maven.

---

## 2. Modelo de datos (entidades y relaciones)

```
Rol (1) ──< (N) Usuario

ArchivoCentral (1) ──< (N) Estante (1) ──< (N) Nivel (1) ──< (N) Caja

AreaResponsable, TipoDocumental, EstadoExpediente, Caja, Usuario  ──< Expediente
Expediente (N) >──< (N) Tag   (muchos a muchos)

Expediente (1) ──< (N) DocumentoDigital  >── Usuario (técnico responsable)
Expediente (1) ──< (N) Prestamo          >── Usuario (solicitante)

Usuario (1) ──< (N) Auditoria   (quién hizo cada acción)
```

**Expediente nunca se borra** (no tiene `DELETE`): su ciclo de vida se maneja reasignándole un `EstadoExpediente` distinto (ej. "Archivado", "Dado de baja"), nunca eliminando la fila.

**Usuario no se borra, se desactiva**: el `DELETE /api/usuarios/{id}` pone `activo=false`, no borra la fila (por integridad histórica: expedientes/préstamos/documentos siguen referenciando a ese usuario).

---

## 3. Autenticación y autorización

### Flujo

```
POST /api/auth/login  { correo, password }
        │
        ▼
   200 OK + { token, tipo: "Bearer", usuarioId, nombre, correo, rol }
        │
        ▼
Guardar el token. En cada request siguiente:
   Header: Authorization: Bearer <token>
```

- El token es JWT (HMAC-SHA256), vigente **8 horas** por defecto (`JWT_EXPIRATION_MS`).
- No hay refresh token todavía: pasadas las 8 horas, el usuario tiene que loguearse de nuevo (el frontend debería manejar un 401 volviendo a la pantalla de login).
- No hay logout del lado del servidor (no hay revocación de tokens): "cerrar sesión" en el frontend es simplemente borrar el token guardado localmente.

### Credenciales de arranque (semilla)

```
correo:    admin@sanjose.gob.pe
password:  CambiarInmediatamente123!
rol:       ADMIN
```

### Roles

El nombre del rol en la tabla `rol` es texto libre (no un enum fijo), pero la convención de autoridad interna es `ROLE_<NOMBRE_EN_MAYUSCULAS>`. Hoy el único nombre con privilegios especiales reconocido por el código es exactamente `ADMIN`.

| Puede hacer | Cualquier autenticado | Solo ADMIN |
|---|---|---|
| Ver (`GET`) casi todo | ✅ | ✅ |
| Crear/editar Expediente, Documento Digital, Préstamo | ✅ | ✅ |
| Borrar Documento Digital o Préstamo | ❌ | ✅ |
| Crear/editar/borrar Usuario, Rol y todos los catálogos (áreas, tipos documentales, estados, estructura física del archivo) | ❌ | ✅ |
| Ver auditoría | ❌ | ✅ |

### Respuestas de error de autenticación/autorización

| Situación | HTTP |
|---|---|
| Sin header `Authorization`, o token ausente | 401 |
| Token inválido, mal formado o expirado | 401 |
| Correo/password incorrectos en login | 401 |
| Usuario inactivo intentando loguearse | 401 |
| Token válido pero sin el rol requerido (ej. no-ADMIN intentando borrar un Usuario) | 403 |

---

## 4. Formato de errores

Toda respuesta de error (4xx) tiene esta forma:

```json
{ "status": 404, "mensaje": "Expediente no encontrado con id: ...", "timestamp": "2026-09-13T10:00:00" }
```

| Código | Cuándo pasa |
|---|---|
| 400 | Validación de campos (`@NotBlank`/`@NotNull`/`@Email`), JSON malformado o con un enum inválido, regla de negocio inválida (ej. devolver un préstamo ya devuelto), archivo con MIME no permitido o que supera el tamaño máximo |
| 401 | Sin autenticar / credenciales inválidas / token inválido o vencido |
| 403 | Autenticado pero sin el rol requerido |
| 404 | El recurso (o alguna de sus dependencias referenciadas por id) no existe |
| 409 | Duplicado (ej. código único de expediente repetido) o "recurso en uso" (ej. borrar un Rol que todavía tiene usuarios asignados) |

---

## 5. Paginación

Los listados de **Usuario, Expediente, Préstamo y Documento Digital** (las entidades que crecen sin límite) están paginados. El resto (catálogos chicos: roles, áreas, tipos, estados, estructura física, tags, auditoría) devuelve la lista completa sin paginar.

**Query params** (estándar Spring Data): `?page=0&size=20&sort=nombre,desc`

**Forma de la respuesta:**
```json
{
  "contenido": [ /* array de items */ ],
  "pagina": 0,
  "tamano": 20,
  "totalElementos": 42,
  "totalPaginas": 3
}
```

---

## 6. Auditoría

Cada `crear`/`actualizar`/`eliminar` (y la descarga de un documento digital) queda registrado automáticamente — no es algo que el frontend tenga que disparar, pasa solo en el backend. Solo lectura, solo ADMIN, vía `GET /api/auditoria`.

```json
{
  "id": "uuid",
  "usuarioId": "uuid", "usuarioNombre": "Administrador",
  "entidadAfectada": "Expediente", "entidadId": "uuid",
  "accion": "CREAR",   // CREAR | MODIFICAR | ELIMINAR | DESCARGAR
  "fecha": "2026-09-13T10:00:00",
  "detalle": null
}
```
Filtros: `GET /api/auditoria?entidadAfectada=Expediente&entidadId=<uuid>` para ver el historial de un recurso puntual.

---

## 7. Almacenamiento de archivos (documentos digitalizados)

- Guardado en filesystem local del servidor, organizado por expediente.
- Tamaño máximo por archivo: 20 MB (configurable).
- Tipos permitidos: `application/pdf`, `image/jpeg`, `image/png`, `image/tiff`.
- El hash SHA-256 y el tipo MIME los calcula/valida el servidor — **nunca** se mandan a mano desde el cliente.
- `POST /api/documentos-digitales` acepta **múltiples archivos en un solo request** (pensado para digitalizar en lote). Ver detalle de request en la sección 8.9.

---

## 8. Referencia de la API por módulo

Base URL: `http://localhost:8080` (o el host que corresponda). Todos los paths llevan el prefijo `/api`.

> Esta sección explica el *contrato* de cada endpoint (para entenderlo). Para *probarlo* interactivamente sin escribir curl/Postman, con la app corriendo abrí `http://localhost:8080/swagger-ui/index.html` — la lista de endpoints ahí se genera sola desde el código, así que siempre está al día.

### 8.1 Auth — `/api/auth`

| Método | Path | Acceso | Body | Respuesta |
|---|---|---|---|---|
| POST | `/login` | 🔓 | `{ "correo": string, "password": string }` | `{ token, tipo, usuarioId, nombre, correo, rol }` |

---

### 8.2 Usuarios — `/api/usuarios`

| Método | Path | Acceso | Notas |
|---|---|---|---|
| POST | `/` | 🔐👑 | Crea usuario. `password` obligatorio al crear |
| PUT | `/{id}` | 🔐👑 | Edita. `password` opcional (si viene vacío, no se cambia) |
| GET | `/{id}` | 🔐 | — |
| GET | `/` | 🔐 | Paginado |
| DELETE | `/{id}` | 🔐👑 | **Desactiva** (`activo=false`), no borra la fila |

**Request (`crear`/`actualizar`):**
```json
{ "nombre": "string", "correo": "email", "password": "string|null", "rolId": "uuid", "activo": true }
```
**Response:**
```json
{ "id", "nombre", "correo", "rolNombre", "activo", "fechaCreacion", "fechaActualizacion" }
```

---

### 8.3 Roles — `/api/roles`

| Método | Path | Acceso |
|---|---|---|
| POST / PUT `/{id}` / DELETE `/{id}` | | 🔐👑 |
| GET / GET `/{id}` | | 🔐 |

**Request:** `{ "nombre": "string", "descripcion": "string|null" }`
**Response:** `{ "id", "nombre", "descripcion" }`
**409** si el nombre ya existe, o si al borrar hay usuarios con ese rol asignado.

---

### 8.4 Expedientes — `/api/expedientes`

| Método | Path | Acceso | Notas |
|---|---|---|---|
| POST | `/` | 🔐 | — |
| PUT | `/{id}` | 🔐 | — |
| GET | `/{id}` | 🔐 | — |
| GET | `/codigo/{codigoUnico}` | 🔐 | Búsqueda por código único |
| GET | `/` | 🔐 | Paginado |
| — | — | — | **Sin `DELETE`**: a propósito, ver sección 2 |

**Request:**
```json
{
  "codigoUnico": "string", "numeroDocumento": "string", "remitente": "string",
  "areaDestinoId": "uuid", "tipoId": "uuid", "estadoId": "uuid",
  "fechaDocumento": "2026-01-15", "asunto": "string", "glosa": "string|null",
  "cajaId": "uuid", "numeroFolios": 5, "creadoPorId": "uuid",
  "tagIds": ["uuid", "..."]
}
```
**Response:**
```json
{
  "id", "codigoUnico", "numeroDocumento", "remitente",
  "areaDestinoNombre", "tipoNombre", "estadoNombre",
  "fechaDocumento", "asunto", "glosa",
  "cajaId", "cajaCodigo", "numeroFolios", "creadoPorNombre",
  "fechaRegistro", "fechaActualizacion", "tags": ["string", "..."]
}
```
**409** si `codigoUnico` ya existe. **404** si `areaDestinoId`/`tipoId`/`estadoId`/`cajaId`/`creadoPorId`/algún `tagId` no existen.

---

### 8.5 Documentos digitales — `/api/documentos-digitales`

| Método | Path | Acceso | Notas |
|---|---|---|---|
| POST | `/` | 🔐 | **`multipart/form-data`**, no JSON — ver abajo |
| PUT | `/{id}` | 🔐 | Solo metadata (JSON) |
| GET | `/{id}` | 🔐 | Metadata |
| GET | `/{id}/archivo` | 🔐 | Descarga el binario real; queda auditado como `DESCARGAR` |
| GET | `/` | 🔐 | Paginado. Filtro opcional `?expedienteId=` |
| DELETE | `/{id}` | 🔐👑 | Borra la fila **y** el archivo físico |

**Crear (multipart, uno o varios archivos por request):**
```
Content-Type: multipart/form-data

expedienteId=<uuid>              (obligatorio)
tecnicoResponsableId=<uuid>      (obligatorio)
escanerUtilizado=<string>        (opcional)
resolucionDpi=<int>              (opcional)
formatoSalida=<string>           (opcional)
archivos=<file>                  (uno o más, mismo nombre de campo repetido)
```
Devuelve un **array** de `DocumentoDigitalResponseDTO` (uno por archivo subido), no un solo objeto.

**Actualizar (JSON, solo metadata — no reemplaza el archivo):**
```json
{ "nombreArchivo": "string", "tecnicoResponsableId": "uuid", "escanerUtilizado": "string|null", "resolucionDpi": 300, "formatoSalida": "string|null", "ocrTexto": "string|null" }
```
**Response:**
```json
{
  "id", "expedienteId", "expedienteCodigoUnico",
  "nombreArchivo", "rutaAlmacenamiento", "tipoMime", "hashSha256",
  "fechaDigitalizacion", "tecnicoResponsableNombre",
  "escanerUtilizado", "resolucionDpi", "formatoSalida", "ocrTexto",
  "fechaActualizacion"
}
```
`rutaAlmacenamiento` es informativa (ruta relativa en el servidor); para bajar el archivo siempre usar `GET /{id}/archivo`, no construir la ruta a mano.

---

### 8.6 Préstamos — `/api/prestamos`

| Método | Path | Acceso | Notas |
|---|---|---|---|
| POST | `/` | 🔐 | Crea con `estado: PRESTADO` |
| PUT | `/{id}` | 🔐 | Solo `tipoSolicitud`/`fechaDevolucionPrevista` (no reasigna expediente/solicitante) |
| PATCH | `/{id}/devolver` | 🔐 | Cierra el préstamo (`estado: DEVUELTO`, fija `fechaDevolucionReal`) |
| GET | `/{id}` | 🔐 | — |
| GET | `/` | 🔐 | Paginado. Filtros (mutuamente excluyentes) `?expedienteId=` / `?solicitanteId=` |
| DELETE | `/{id}` | 🔐👑 | — |

**Request (crear/actualizar):**
```json
{ "expedienteId": "uuid", "solicitanteId": "uuid", "tipoSolicitud": "FISICO|DIGITAL", "fechaDevolucionPrevista": "2026-02-01" }
```
**Response:**
```json
{
  "id", "expedienteId", "expedienteCodigoUnico", "solicitanteNombre",
  "tipoSolicitud", "fechaSolicitud", "fechaDevolucionPrevista", "fechaDevolucionReal",
  "estado": "PRESTADO|DEVUELTO|VENCIDO", "fechaActualizacion"
}
```
**400** si se intenta `devolver` un préstamo que ya está en `DEVUELTO` (guard de idempotencia).

---

### 8.7 Auditoría — `/api/auditoria` (solo lectura, solo ADMIN)

| Método | Path |
|---|---|
| GET | `/{id}` |
| GET | `/?entidadAfectada=&entidadId=` (ambos opcionales, van juntos) |

Ver forma de la respuesta en sección 6.

---

### 8.8 Catálogos (mismo patrón CRUD en los 9)

Todos 🔐 para leer, 🔐👑 para crear/editar/borrar. Sin paginar (son listas chicas).

| Recurso | Base path | Campos del request | Depende de |
|---|---|---|---|
| Área responsable | `/api/areas-responsables` | `nombre`, `codigo` | — |
| Tipo documental | `/api/tipos-documentales` | `nombre` | — |
| Estado de expediente | `/api/estados-expediente` | `nombre` | — |
| Tag | `/api/tags` | `nombre` | — |
| Archivo central | `/api/archivos-centrales` | `nombre` | — |
| Estante | `/api/estantes` | `codigo`, `archivoCentralId` | ArchivoCentral. Filtro `GET ?archivoCentralId=` |
| Nivel | `/api/niveles` | `codigo`, `estanteId` | Estante. Filtro `GET ?estanteId=` |
| Caja | `/api/cajas` | `codigo`, `nivelId` | Nivel. Filtro `GET ?nivelId=` |

Todos devuelven `409` si el nombre/código ya existe (duplicado) o si al borrar hay algo que depende de ese registro (ej. borrar un Estante con Niveles adentro).

---

## 9. Tests automatizados

```bash
./mvnw test
```
17 tests de integración (login, RBAC, reglas de negocio, auditoría, timestamps, paginación, devolución de préstamos), corridos contra Postgres real dentro de una transacción que se revierte — no ensucian la base. Ver `src/test/java/.../integration/`.

---

## 10. Estado del proyecto / lo que falta

**Funcional y probado:** CRUD de los 14 módulos, JWT + RBAC, semilla de datos, auditoría real, paginación, subida/descarga de archivos, tests automatizados, Postgres en Docker para desarrollo.

**Pendiente (no bloqueante para integrar el frontend):**
- CI en GitHub Actions (correr los tests automáticamente en cada push).
- Refresh tokens / logout con revocación server-side (hoy el token simplemente expira a las 8 horas).
- Validación de MIME por contenido real del archivo (hoy se confía en el `Content-Type` que declara el cliente al subir, no se inspecciona el binario).
