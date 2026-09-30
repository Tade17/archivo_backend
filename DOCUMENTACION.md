# Documentación técnica — archivo-backend

Referencia completa del backend, pensada para quien va a **consumir la API desde el frontend**. Para instrucciones de instalación/arranque, ver [`README.md`](./README.md) — acá se asume que ya tenés la app (y, si vas a probar OCR, el servicio `ocr` de Docker) corriendo.

**Convenciones de acceso usadas en toda esta doc:**
- 🔓 público, no requiere token
- 🔐 requiere JWT válido (cualquier usuario autenticado, cualquier rol)
- 🔐👑 requiere JWT válido **y** rol `ADMIN`

> **El sistema ya no maneja ubicación física del archivo ni préstamos.** Desde el 20 de septiembre de 2026 el modelo pasó a ser "solo digital": no hay Archivo Central / Estante / Nivel / Caja / Tag ni módulo de Préstamos expuestos por la API (el código de Préstamo sigue en el repo pero no está conectado a ningún endpoint — es código muerto, ver sección 10).

---

## 1. Arquitectura general

```
Controller  →  Service (interfaz)  →  ServiceImpl  →  Repository (Spring Data JPA)
                    ↑
                 Mapper (Entity <-> DTO)
```

- Cada módulo "clásico" (Usuario, Expediente, Rol, etc.) sigue el patrón de arriba: Controller delgado, lógica en el Service, DTOs `record` inmutables, nunca se expone la entidad JPA directamente.
- **Excepción a esta regla**: `WorkspaceController` (`/api/workspace/**`) es un módulo aparte, pensado para las pantallas de "mesa de trabajo" del frontend (búsqueda avanzada, recepción con código automático, vista de auditoría). Usa `JdbcTemplate` con SQL directo en vez de pasar por Service/Repository — es intencional (consultas de lectura compuestas, más simples de armar así), pero por eso mismo sus reglas de validación pueden no coincidir 100% con las de los endpoints "clásicos" del mismo recurso. Ver sección 9.
- Las excepciones de negocio (`RecursoNoEncontradoException`, `RecursoDuplicadoException`, etc.) las traduce un `GlobalExceptionHandler` centralizado a códigos HTTP — ver sección 5.

### Stack
Java 17 · Spring Boot 4.1 · Spring Security + JWT · Spring Data JPA / Hibernate · PostgreSQL · Flyway · Maven · RapidOCR (PP-OCRv5 sobre ONNX Runtime) en un microservicio Python separado.

---

## 2. Modelo de datos (entidades y relaciones)

```
Rol (1) ──< (N) Usuario

AreaResponsable, TipoDocumental, EstadoExpediente, Usuario ──< Expediente
Expediente (N) >──< (N) Tag        (ExpedienteRequestDTO todavía acepta "tagIds", pero ya
                                     no hay /api/tags para crear un Tag nuevo por API —
                                     solo se pueden referenciar tags que ya existan en la BD)

Expediente (1) ──< (N) DocumentoDigital  >── Usuario (técnico responsable)

Usuario (1) ──< (N) Auditoria   (quién hizo cada acción)

correlativo_expediente(anio, ultimo_numero)   -- contador atómico para numerar expedientes
```

**Expediente nunca se borra** (no tiene `DELETE`): su ciclo de vida se maneja reasignándole un `EstadoExpediente` distinto, nunca eliminando la fila.

**Usuario no se borra, se desactiva**: el `DELETE /api/usuarios/{id}` pone `activo=false`, no borra la fila.

**La caja/ubicación física es opcional** (`cajaId` en `ExpedienteRequestDTO` ya no es obligatorio): el sistema quedó pensado para operar sin contraparte física, pero el campo se conserva para expedientes históricos que sí la tenían.

**Numeración de expedientes**: `numeroTramite` y `codigoUnico` se generan del lado del servidor con un correlativo por año (tabla `correlativo_expediente`, con bloqueo de fila para que dos registros simultáneos no choquen), no con UUIDs aleatorios ni a mano por el cliente. Ver `POST /api/workspace/recepcion` en la sección 9.

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

- El token es JWT (HMAC-SHA384), vigente **8 horas** por defecto (`JWT_EXPIRATION_MS`).
- No hay refresh token ni logout server-side todavía: pasadas las 8 horas hay que loguearse de nuevo; "cerrar sesión" en el frontend es simplemente borrar el token guardado localmente.

### Credenciales de arranque (semilla)

```
correo:    admin@sanjose.gob.pe
password:  CambiarInmediatamente123!
rol:       ADMIN
```

### Roles (3 desde el 20 de septiembre de 2026)

El nombre del rol en la tabla `rol` es texto libre, pero la convención de autoridad interna es `ROLE_<NOMBRE_EN_MAYUSCULAS>`. Hoy el código reconoce específicamente estos tres:

| Rol | Puede |
|---|---|
| **ADMIN** | Todo: gestiona usuarios, roles y catálogos (áreas, tipos documentales, estados), y es el único que ve la auditoría (`/api/auditoria` y `/api/workspace/auditoria*`) |
| **GESTOR_DOCUMENTAL** | Crea y edita Expedientes, sube/edita Documentos Digitales. No accede a usuarios, roles, catálogos ni auditoría |
| **LECTOR** | Solo lectura: busca, visualiza y descarga documentos y expedientes. No puede crear/editar nada |

> Si un usuario tiene un rol con otro nombre (por ejemplo, quedó de una migración de datos vieja), no cae en ninguna de estas categorías especiales: puede hacer `GET` en casi todo (la regla por defecto es "cualquier autenticado puede leer"), pero cualquier escritura le va a dar 403.

### Respuestas de error de autenticación/autorización

| Situación | HTTP |
|---|---|
| Sin header `Authorization`, o token ausente/inválido/expirado | 401 |
| Correo/password incorrectos o usuario inactivo en login | 401 |
| Token válido pero sin el rol requerido | 403 (mensaje: *"Tu perfil no tiene acceso a esta función"*) |

---

## 4. Formato de errores

Toda respuesta de error (4xx) tiene esta forma:

```json
{ "status": 404, "mensaje": "Expediente no encontrado con id: ...", "timestamp": "2026-09-29T10:00:00" }
```

| Código | Cuándo pasa |
|---|---|
| 400 | Validación de campos, JSON malformado o con un enum inválido, regla de negocio inválida, archivo con MIME no permitido o que supera el tamaño máximo |
| 401 | Sin autenticar / credenciales inválidas / token inválido o vencido |
| 403 | Autenticado pero sin el rol requerido |
| 404 | El recurso (o alguna de sus dependencias referenciadas por id) no existe |
| 409 | Duplicado (ej. código único de expediente repetido) o "recurso en uso" |

---

## 5. Paginación

Los listados de **Usuario, Expediente y Documento Digital** están paginados. Los catálogos chicos (roles, áreas, tipos documentales) devuelven la lista completa sin paginar.

**Query params** (estándar Spring Data): `?page=0&size=20&sort=nombre,desc`

**Forma de la respuesta:**
```json
{ "contenido": [ /* array de items */ ], "pagina": 0, "tamano": 20, "totalElementos": 42, "totalPaginas": 3 }
```

Los endpoints de `/api/workspace/*` que también paginan (`/buscar`, `/auditoria`) usan la misma forma de respuesta pero se controlan con `?page=` y `?size=` simples (no aceptan `sort`, el orden viene fijo en cada endpoint).

---

## 6. Auditoría

Cada `crear`/`actualizar`/`eliminar`/`descargar` queda registrado automáticamente por un aspecto AOP — no es algo que el frontend dispare. **Solo lectura, solo rol ADMIN**, por dos caminos:

- `GET /api/auditoria` (clásico, filtros `?entidadAfectada=&entidadId=`)
- `GET /api/workspace/auditoria` y `/api/workspace/auditoria/resumen` (filtros más ricos, ver sección 9)

```json
{
  "id": "uuid",
  "usuarioId": "uuid", "usuarioNombre": "Administrador",
  "entidadAfectada": "Expediente", "entidadId": "uuid",
  "accion": "CREAR",   // CREAR | MODIFICAR | ELIMINAR | DESCARGAR | CONSULTAR
  "fecha": "2026-09-29T10:00:00",
  "detalle": null
}
```

---

## 7. Almacenamiento de archivos

- Guardado en filesystem local del servidor, organizado por expediente.
- Tamaño máximo por archivo: 20 MB (configurable). Tipos permitidos: `application/pdf`, `image/jpeg`, `image/png`, `image/tiff`.
- El hash SHA-256 y el tipo MIME los calcula/valida el servidor — **nunca** se mandan a mano desde el cliente.
- El técnico responsable de la subida también lo fija el servidor (el usuario autenticado), no lo que mande el cliente en el formulario.
- `POST /api/documentos-digitales` acepta **múltiples archivos en un solo request** (lote de digitalización).

---

## 8. OCR automático

Desde el 26 de septiembre de 2026, cada documento subido se procesa automáticamente con **RapidOCR (PP-OCRv5, motor ONNX)**, corriendo 100% local en un microservicio Python (`ocr_service/`, contenedor `ocr` en `docker-compose.yml`, puerto `127.0.0.1:8091`) — no depende de ningún servicio externo ni API key.

### Flujo
1. Subís el documento → la respuesta HTTP **no espera** al OCR (es asíncrono).
2. Un worker en background le manda el archivo al servicio OCR.
3. El resultado se guarda en `ocrTexto` con un estado:

| Estado | Significado |
|---|---|
| `PENDIENTE` | esperando que un worker lo tome |
| `PROCESANDO` | el OCR está trabajando en este documento ahora mismo |
| `COMPLETADO` | texto reconocido con confianza suficiente (≥ umbral, default 0.75) |
| `REQUIERE_REVISION` | no se encontró texto o la confianza quedó debajo del umbral — alguien debe revisar/corregir a mano |
| `ERROR` | el servicio OCR no pudo procesar el archivo (se puede reintentar) |

Si el backend se reinicia con documentos en `PROCESANDO`, esos vuelven solos a la cola (`PENDIENTE`) al arrancar — no quedan colgados.

### Campos relevantes en `DocumentoDigitalResponseDTO`
`ocrTexto`, `ocrEstado`, `ocrConfianza` (0 a 1), `ocrPaginas`, `ocrError`, `ocrIntentos`, `ocrRevisado` (true si un humano corrigió el texto a mano vía `PUT`), `ocrActualizadoEn`.

### Reintentar manualmente
```
POST /api/documentos-digitales/{id}/ocr/reintentar
```
Responde `202 Accepted` (no espera el resultado). Falla con 400 si el documento ya está `PROCESANDO`.

### Corrección manual
`PUT /api/documentos-digitales/{id}` permite mandar `ocrTexto` a mano (por ejemplo, para corregir un resultado `REQUIERE_REVISION`); al hacerlo, el documento queda marcado `ocrRevisado=true` y su estado pasa a `COMPLETADO` (si el texto no quedó vacío) o se mantiene en revisión.

Detalle completo de configuración y errores frecuentes: [`OCR.md`](./OCR.md).

---

## 9. Referencia de la API por módulo

Base URL: `http://localhost:8080`. Todos los paths llevan el prefijo `/api`.

> Para explorar/probar interactivamente: `http://localhost:8080/swagger-ui/index.html` (se genera sola desde el código, siempre al día).

### 9.1 Auth — `/api/auth`

| Método | Path | Acceso | Body | Respuesta |
|---|---|---|---|---|
| POST | `/login` | 🔓 | `{ "correo": string, "password": string }` | `{ token, tipo, usuarioId, nombre, correo, rol }` |

---

### 9.2 Usuarios — `/api/usuarios`

| Método | Path | Acceso | Notas |
|---|---|---|---|
| POST / PUT `/{id}` | | 🔐👑 | `password` obligatorio al crear, opcional al editar |
| GET / GET `/{id}` | | 🔐👑 | Lista paginada. **Todo el módulo es solo-ADMIN, incluida la lectura** — ni GESTOR_DOCUMENTAL ni LECTOR pueden ver la lista de usuarios |
| DELETE `/{id}` | | 🔐👑 | **Desactiva** (`activo=false`), no borra |

**Request:** `{ "nombre", "correo", "password", "rolId", "activo" }`
**Response:** `{ "id", "nombre", "correo", "rolNombre", "activo", "fechaCreacion", "fechaActualizacion" }`

---

### 9.3 Roles — `/api/roles`

| Método | Acceso |
|---|---|
| POST / PUT `/{id}` / DELETE `/{id}` | 🔐👑 |
| GET / GET `/{id}` | 🔐 |

**Request:** `{ "nombre", "descripcion" }` · **Response:** `{ "id", "nombre", "descripcion" }`
409 si el nombre ya existe o (al borrar) hay usuarios con ese rol.

---

### 9.4 Expedientes — `/api/expedientes`

| Método | Path | Acceso |
|---|---|---|
| POST / PUT `/{id}` | | 🔐 (GESTOR_DOCUMENTAL o ADMIN) |
| GET `/{id}`, GET `/codigo/{codigoUnico}`, GET (lista paginada) | | 🔐 |
| — | — | **Sin `DELETE`**, a propósito |

**Request:**
```json
{
  "codigoUnico": "string", "numeroDocumento": "string", "remitente": "string",
  "areaDestinoId": "uuid", "tipoId": "uuid", "estadoId": "uuid",
  "fechaDocumento": "2026-01-15", "asunto": "string", "glosa": "string|null",
  "cajaId": "uuid|null", "numeroFolios": 5, "creadoPorId": "uuid",
  "tagIds": []
}
```
`cajaId` ya es **opcional**. Nota: si vas a crear un expediente con numeración automática (correlativo por año), usá `POST /api/workspace/recepcion` (sección 9.7) en vez de este endpoint directo — este endpoint clásico exige que **vos** mandes `codigoUnico` a mano y no aplica el correlativo.

**Response:**
```json
{
  "id", "codigoUnico", "numeroDocumento", "remitente",
  "areaDestinoNombre", "tipoNombre", "estadoNombre",
  "fechaDocumento", "asunto", "glosa",
  "cajaId", "cajaCodigo", "numeroFolios", "creadoPorNombre",
  "fechaRegistro", "fechaActualizacion", "tags": []
}
```
409 si `codigoUnico` ya existe. 404 si algún id referenciado no existe.

---

### 9.5 Documentos digitales — `/api/documentos-digitales`

| Método | Path | Acceso | Notas |
|---|---|---|---|
| POST | `/` | 🔐 (GESTOR_DOCUMENTAL o ADMIN) | `multipart/form-data`, ver abajo. Dispara OCR automático |
| PUT | `/{id}` | 🔐 (GESTOR_DOCUMENTAL o ADMIN) | Solo metadata (JSON) |
| GET | `/{id}`, `/` (paginado, filtro `?expedienteId=`) | 🔐 | — |
| GET | `/{id}/archivo` | 🔐 | Descarga el binario; audita `DESCARGAR` |
| POST | `/{id}/ocr/reintentar` | 🔐 | Reprocesa el OCR, `202 Accepted` |
| DELETE | `/{id}` | 🔐👑 | Borra fila **y** archivo físico |

**Crear (multipart, uno o varios archivos):**
```
expedienteId=<uuid>              (obligatorio)
tecnicoResponsableId=<uuid>      (se ignora: siempre se usa el usuario autenticado)
escanerUtilizado, resolucionDpi, formatoSalida   (opcionales)
archivos=<file>                  (uno o más, mismo campo repetido)
```
Devuelve un **array** de `DocumentoDigitalResponseDTO`.

**Actualizar (JSON, solo metadata):**
```json
{ "nombreArchivo", "tecnicoResponsableId", "escanerUtilizado", "resolucionDpi", "formatoSalida", "ocrTexto" }
```
(`tecnicoResponsableId` también se ignora acá y se fuerza al usuario autenticado.)

**Response:** ver campos completos (incluidos los de OCR) en la sección 8.

---

### 9.6 Auditoría — `/api/auditoria` (solo lectura, solo ADMIN)

`GET /{id}` · `GET /?entidadAfectada=&entidadId=` (ambos opcionales, van juntos).

---

### 9.7 Catálogos — solo ADMIN puede escribir, cualquier autenticado lee

| Recurso | Base path | Campos |
|---|---|---|
| Área responsable | `/api/areas-responsables` | `nombre` (obligatorio), `codigo` (**ahora opcional**) |
| Tipo documental | `/api/tipos-documentales` | `nombre` |
| Estado de expediente | `/api/estados-expediente` | `nombre` |

Todos: `POST`/`PUT /{id}`/`DELETE /{id}` (🔐👑), `GET`/`GET /{id}` (🔐, sin paginar). 409 si hay duplicado o si al borrar hay algo que depende del registro.

---

### 9.8 Workspace — `/api/workspace/*` (endpoints de "mesa de trabajo", con SQL directo)

Pensado para las pantallas de búsqueda/recepción del frontend. Todo 🔐 salvo donde se indica.

| Método | Path | Acceso | Qué hace |
|---|---|---|---|
| GET | `/buscar` | 🔐 | Búsqueda avanzada combinable (ver query params abajo). Paginado, `size` default 4 |
| GET | `/expedientes/{id}` | 🔐 | Detalle "enriquecido" de un expediente (incluye `documentoId`, `documentoNombre`, `totalDocumentos`) |
| GET | `/catalogos` | 🔐 | `{ areas: [...], tipos: [...], roles: [...] }` — para poblar selects del frontend |
| POST | `/recepcion` | 🔐 (GESTOR_DOCUMENTAL/ADMIN) | Crea un expediente **con numeroTramite y código autogenerados** (correlativo por año) |
| PUT | `/expedientes/{id}` | 🔐 (GESTOR_DOCUMENTAL/ADMIN) | Edita expediente vía SQL directo (no pasa por `ExpedienteService`) |
| GET | `/documentos/{id}/vista` | 🔐 | Descarga el documento con `Content-Disposition: inline` (para visor embebido, no fuerza descarga). Audita `CONSULTAR` |
| GET | `/auditoria` | 🔐👑 | Filtros: `?accion=&modulo=&usuario=&desde=&hasta=` |
| GET | `/auditoria/resumen` | 🔐👑 | Conteos del día: eventos, descargas, eliminaciones, consultas |

**Query params de `/buscar`:** `texto` (busca en código/documento/remitente/asunto/glosa, sin distinguir tildes, y también dentro del texto OCR), `codigo`, `areaDestinoId`, `tipoId`, `cajaId`, `anio`, `desde`, `hasta`.

**Body de `/recepcion` y `PUT /expedientes/{id}` (record `Reception`):**
```json
{ "numeroDocumento", "remitente", "areaDestinoId", "tipoId", "fechaDocumento", "asunto", "glosa" }
```
`/recepcion` no pide `codigoUnico` ni `numeroTramite`: los genera el servidor (`TRM-<año>-<correlativo>` / `EXP-<año>-<correlativo>`).

---

### 9.9 Módulos que existían y ya no están (para no buscarlos)

`ArchivoCentralController`, `CajaController`, `EstanteController`, `NivelController`, `TagController` y `PrestamoController` **fueron eliminados** el 20/09/2026 al pasar el sistema a "modo solo digital". Sus DTOs/entidades/repos en algunos casos siguen en el código (Prestamo, por ejemplo) pero **no hay ningún endpoint que los use** — no son parte de la API actual.

---

## 10. Tests automatizados

```bash
./mvnw test
```
**20 tests de integración** (login/JWT, RBAC por los 3 roles actuales, reglas de negocio, auditoría, timestamps, paginación), corridos contra Postgres real dentro de una transacción que se revierte — no ensucian la base. Ver `src/test/java/.../integration/`.

---

## 11. Estado del proyecto / lo que falta

**Funcional y probado (a la fecha, 29/09/2026):** CRUD de Usuario/Rol/Expediente/DocumentoDigital + catálogos, JWT + RBAC de 3 roles, auditoría real, paginación, subida/descarga de archivos, **OCR automático con recuperación de fallos**, búsqueda avanzada sin tildes, numeración automática de expedientes por correlativo, Postgres + servicio OCR en Docker, 20 tests automatizados, datos de demo cargables por script.

**Pendiente / deuda conocida:**
- CI en GitHub Actions.
- Refresh tokens / logout con revocación server-side.
- Validación de MIME por contenido real del archivo (hoy se confía en el `Content-Type` declarado).
- `WorkspaceController` usa SQL directo en vez del patrón Service/Repository del resto del proyecto — funciona y está probado, pero es un estilo distinto sin cobertura de tests propia.
- Código muerto a limpiar en algún momento: todo el módulo de Préstamo (entidad/DTO/mapper/repo/service) y el archivo de perfil `application-docker.properties` (ya redundante, el default de conexión ya apunta a Docker).
- `ACTUALIZACION_BD_DOCKER.md` tiene un typo: menciona "V6\_\_ocr_automatico.sql" pero el archivo real es `V7__ocr_automatico.sql`.
