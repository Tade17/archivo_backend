# Contexto verificado para el Informe Final de PPP

**Autor del informe:** Wilson Omar Tadeo Mendoza Gastulo (usuario git `Tade17`)
**Corte:** 2026-10-19 · **Generado por:** lectura de archivos y comandos de consulta; no se modificó código. Las únicas ejecuciones fueron las suites de pruebas (sección 2.4).

**Convención de fuentes:** `[archivo]` = leído del repositorio · `[cmd: …]` = salida de un comando ejecutado hoy · `[usuario]` = dato dicho por el usuario en la conversación, no comprobable en el repositorio · **NO VERIFICADO** = no se pudo comprobar.

---

## 0. Qué es cada repositorio (corte)

| Repositorio | URL | Rama | HEAD al corte | Commits |
|---|---|---|---|---|
| Backend (incluye `ocr_service/`, 18 archivos versionados dentro de este repo) | github.com/Tade17/archivo_backend | main | `793eebd` (2026-10-19) | 38 |
| Frontend | github.com/Tade17/archivo_frontend | main | `0362b7e` (2026-10-19) | 26 |

Fuentes: `[cmd: git remote get-url origin; git log -1; git rev-list --count HEAD; git ls-files ocr_service]`.
No existe un tercer repositorio para el OCR: el servicio vive en `archivo-backend/ocr_service/`. No hay CI (`.github/` no existe en ninguno) `[cmd: git ls-files | grep .github]`.

---

## 1. Stack y versiones reales

### 1.1 Backend (Java)

| Componente | Versión | Fuente |
|---|---|---|
| Java (objetivo de compilación y ejecución en Docker) | **17** | `[pom.xml: java.version]`, `[Dockerfile: eclipse-temurin:17-jre]` |
| JDK usado por Maven en esta laptop | 25 (Oracle); `java -version` del shell muestra 23.0.2 | `[cmd: ./mvnw -v; java -version]` |
| Maven Wrapper | Apache Maven 3.9.16 | `[cmd: ./mvnw -v]` |
| Spring Boot | **4.1.1** | `[pom.xml: parent]` |
| Spring Framework / Spring Security | 7.0.9 / 7.1.1 | `[BOM spring-boot-dependencies-4.1.1.pom en ~/.m2]` |
| Hibernate ORM | 7.4.5.Final | `[mismo BOM]` |
| Tomcat embebido | 11.0.24 | `[mismo BOM]` |
| Jackson | 3.1.5 (paquete `tools.jackson`) y 2.21.5 | `[mismo BOM]; [código: import tools.jackson.databind.ObjectMapper]` |
| **Flyway** | **12.4.0** (`spring-boot-starter-flyway` + `flyway-database-postgresql`) | `[BOM]; [pom.xml]` |
| Driver PostgreSQL (JDBC) | 42.7.13 | `[BOM]` |
| HikariCP / AspectJ / Lombok | 7.0.2 / 1.9.25.1 / 1.18.46 | `[BOM]` |
| **jjwt** | **0.12.6** (api, impl, jackson) | `[pom.xml]` |
| **Swagger / springdoc** | `springdoc-openapi-starter-webmvc-ui` **3.1.1**; UI en `/swagger-ui/index.html` | `[pom.xml]; [README.md]` |
| JUnit / Mockito | 6.0.3 / 5.23.0 | `[BOM]` |

Nota: las versiones marcadas `[BOM]` son las que gestiona Spring Boot 4.1.1; **no se verificó contra el classpath resuelto** (`mvn dependency:list` no devolvió salida en esta máquina).

### 1.2 Frontend (Angular)

| Componente | Declarado | Resuelto en `package-lock.json` | Fuente |
|---|---|---|---|
| **Angular** (core, common, router, forms) | ^22.1.0 | **22.1.6** | `[package.json; package-lock.json]` |
| Angular CLI / build | ^22.1.8 | 22.1.8 | `[package-lock.json]` |
| **Tailwind CSS** | ^3.4.17 | **3.4.17** | `[package-lock.json]` |
| TypeScript | ~6.0.2 | 6.0.3 | `[package-lock.json]` |
| RxJS | ~7.8.0 | 7.8.2 | `[package-lock.json]` |
| pdfjs-dist | ^6.3.289 | 6.3.289 | `[package-lock.json]` |
| Vitest (pruebas) / jsdom | ^4.0.8 / ^28.0.0 | 4.1.11 / 28.1.0 | `[package-lock.json]` |
| PostCSS / Autoprefixer | ^8.5.28 / ^10.6.0 | 8.5.28 / 10.6.0 | `[package-lock.json]` |
| Node.js / npm | imagen `node:24-alpine` en Docker; `packageManager: npm@11.16.0` | local: Node v24.19.0, npm 11.17.0 | `[Dockerfile; package.json; cmd: node -v; npm -v]` |

Observación: `qrcode ^1.5.4` figura en `package.json` pero no hay ningún uso en `src/` `[cmd: grep -ril qrcode src]` (dependencia declarada sin uso). Las plantillas de los componentes son **inline** dentro de los `.ts` (solo hay 1 `.html`: `index.html`).

### 1.3 Servicio OCR (Python)

| Componente | Versión | Fuente |
|---|---|---|
| Python | imagen `python:3.12-slim`; real en la imagen construida: **3.12.15** | `[ocr_service/Dockerfile]; [cmd: docker run … python --version]` |
| **FastAPI** / Uvicorn | **0.116.1** / 0.35.0 | `[requirements.txt]; [cmd: import dentro de la imagen]` |
| **RapidOCR** | **3.9.2**, modelos PP-OCRv5 (detección PaddleOCR, reconocimiento latino), ONNX Runtime 1.22.1 en CPU | `[requirements.txt; app.py:get_engine(); cmd: pip show rapidocr]` |
| NumPy / Pillow / ReportLab / pypdf / pypdfium2 | 2.2.6 / 11.3.0 / 4.4.4 / 6.20.1 / 4.30.0 | `[requirements.txt]` |

### 1.4 Infraestructura

| Componente | Versión | Fuente |
|---|---|---|
| PostgreSQL | imagen `postgres:17`; servidor observado al correr las pruebas: **17.11** | `[docker-compose.yml, docker-compose.prod.yml]; [cmd: log de pruebas "Database version: 17.11"]` |
| Build backend | `maven:3.9-eclipse-temurin-17` → `eclipse-temurin:17-jre` | `[Dockerfile]` |
| Servidor web del frontend | `nginx:1.27-alpine` (proxy de `/api` hacia el backend, `client_max_body_size 200m`) | `[archivo-frontend/Dockerfile, nginx.conf]` |
| Docker / Compose en esta laptop | Docker 28.5.1, Compose v2.40.0-desktop.1 | `[cmd: docker --version; docker compose version]` |
| Docker / Compose en la máquina de la municipalidad | Docker 29.8.2, Compose v5.6.0 | `[usuario: salida de docker info pegada en la conversación]`; no verificable en repo |
| Archivos Compose | `docker-compose.yml` (desarrollo: solo db + OCR) y `docker-compose.prod.yml` (db + OCR + backend + frontend) | `[archivos]` |

---

## 2. Métricas actuales

Criterio: solo archivos **versionados en git** (`git ls-files`; sin `node_modules`, `target`, `dist`). Las líneas se cuentan con `wc -l` equivalente; "no vacías" excluye líneas en blanco. `[cmd: script Python sobre git ls-files]`.

### 2.1 Backend (repo `archivo-backend`)

| Tipo | Archivos | Líneas | Líneas no vacías |
|---|---:|---:|---:|
| Java `src/main/java` | 158 | 6 977 | 5 943 |
| Java `src/test/java` | 15 | 2 192 | 1 939 |
| SQL (migraciones Flyway) | 14 | 485 | 417 |
| Python OCR (producción: `app.py`, `job_store.py`, `artifact_store.py`, `searchable_pdf.py`) | 4 | 1 521 | 1 379 |
| Python OCR (pruebas `test_*.py`) | 10 | 1 144 | 1 005 |
| Python `benchmark_ocr.py` | 2 | 305 | 266 |
| JavaScript seeder `scripts/seed-demo` (`.mjs`) | 4 | 1 109 | 1 067 |
| Markdown (documentación) | 9 | 1 220 | 901 |
| **Código propio (Java + SQL + Python + seeder), sin Markdown** | — | **13 733** | — |
| Total de archivos versionados (incluye `mvnw`, YAML, etc.) | 231 | 15 863 | 13 740 |

- **Controladores:** 16 `[src/main/java/**/controller]`
- **Endpoints REST:** **85** (anotaciones `@Get/Post/Put/Delete/PatchMapping` en los 16 controladores)
- **Entidades JPA (`@Entity`):** 15 — ArchivoCentral, AreaResponsable, Auditoria, Caja, Contenedor, DocumentoDigital, EstadoExpediente, Estante, Expediente, Nivel, Prestamo, Rol, Tag, TipoDocumental, Usuario
- Repositorios 15 · Interfaces de servicio 16 · Implementaciones 15 · DTO 34
- `@PreAuthorize` en controladores: 43 · `@Auditable` en servicios: 45
- **Servicio OCR (FastAPI): 12 rutas HTTP** `[cmd: introspección de app.routes dentro de la imagen]`: `POST /v2/ocr`, `GET /v2/artifacts/{id}`, `GET /health`, `POST /ocr`, `POST /pdf`, `POST /images/pdf`, `GET /v2/jobs/config`, `POST|GET|DELETE /v2/jobs/{id}`, `GET|DELETE /v2/jobs/{id}/result`.

Endpoints por controlador:

| Controlador | Endpoints | Ruta base |
|---|---:|---|
| ArchivoCentral | 5 | /api/archivos-centrales |
| AreaResponsable | 5 | /api/areas-responsables |
| Auditoria | 2 | /api/auditoria |
| Auth | 1 | /api/auth |
| Caja | 5 | /api/cajas |
| Contenedor | 8 | /api/workspace |
| DocumentoDigital | 10 | /api/documentos-digitales |
| EstadoExpediente | 5 | /api/estados-expediente |
| Estante | 5 | /api/estantes |
| Expediente | 6 | /api/expedientes |
| Nivel | 5 | /api/niveles |
| Prestamo | 5 | /api/workspace/prestamos |
| Rol | 5 | /api/roles |
| TipoDocumental | 5 | /api/tipos-documentales |
| Usuario | 5 | /api/usuarios |
| Workspace | 8 | /api/workspace |

### 2.2 Migraciones Flyway (14)

`[cmd: git ls-files src/main/resources/db/migration]`

| # | Archivo | # | Archivo |
|---|---|---|---|
| 1 | V1__init_schema.sql | 8 | V8__pdf_ocr_posicionado.sql |
| 2 | V2__seed_admin.sql | 9 | V9__contenedores_y_ubicacion.sql |
| 3 | V3__mvp_archivo.sql | 10 | V10__estado_unico_registrado.sql |
| 4 | V4__modo_solo_digital.sql | 11 | V11__prestamos_a_entidades_externas.sql |
| 5 | V5__roles_claros_y_correlativos.sql | 12 | V12__documento_originales.sql |
| 6 | V6__busqueda_sin_tildes.sql | 13 | V13__ocr_jobs_progress.sql |
| 7 | V7__ocr_automatico.sql | 14 | V14__archivo_central_inicial.sql |

### 2.3 Frontend (repo `archivo-frontend`)

| Tipo | Archivos | Líneas | Líneas no vacías |
|---|---:|---:|---:|
| TypeScript de la app (`src/app`, sin `.spec`) | 60 | 6 093 | 5 925 |
| TypeScript de pruebas (`*.spec.ts`) | 5 | 613 | 567 |
| CSS | 2 | 345 | 296 |
| HTML | 1 | 13 | 13 |
| Markdown | 4 | 241 | 189 |
| **Código propio (TS + specs + CSS + HTML)** | — | **7 064** | — |

**No usar el "total versionado" del frontend (60 013 líneas):** incluye `package-lock.json` (9 545 líneas) y dos PNG en `public/design/` que `wc -l` cuenta como texto (una sola imagen aporta 5 147 "líneas") `[cmd: ranking de archivos por líneas]`.

- **Componentes (`*.component.ts`):** 26 (16 de pantallas/features + 9 compartidos + 1 raíz `app.component`). Pantallas: login, buscador (Explorar), expediente (detalle, formulario, lista), documento digital (detalle, lista), carga de documentos, contenedores (lista, detalle), préstamos, ubicaciones, usuarios (lista, formulario), catálogos, auditoría.
- **Servicios:** 11 · **Guards:** 2 (`authGuard`, `adminGuard`) · **Interceptores:** 2 (hay un `roles(...)` definido en `app.routes.ts`).
- **Rutas declaradas** en `app.routes.ts` (`path:`): 20 entradas = 17 pantallas (`login`, `buscar`, `expedientes`, `expedientes/nuevo`, `expedientes/:id/editar`, `expedientes/:id`, `digitalizar`, `contenedores`, `contenedores/:id`, `prestamos`, `ubicaciones`, `documentos`, `documentos/:id`, `usuarios`, `usuarios/nuevo`, `catalogos`, `auditoria`) + redirección raíz + comodín `**`.

### 2.4 Pruebas EJECUTADAS (hoy, 2026-10-19, corriendo las suites)

| Suite | Cómo se ejecutó | Resultado real |
|---|---|---|
| Backend (JUnit/Spring) | `./mvnw test` contra un PostgreSQL 17 limpio (contenedor `postgres:17` en `localhost:5433`, Flyway aplicó V1–V14) | **130 reportadas por Surefire: 0 fallas, 0 errores, 6 omitidas → 124 ejecutadas.** `BUILD SUCCESS` |
| Frontend (Vitest) | `ng test --watch=false` | **48 pasaron de 48** (5 archivos) |
| OCR (Python `unittest`) | dentro de la imagen del servicio: `test_app_rendering test_artifact_store test_benchmark_ocr test_image_batch test_job_store test_ocr_contract test_ocr_jobs test_pdf_overlay test_searchable_pdf` | **79 ejecutadas, OK** |
| **Total ejecutado con éxito** | | **251** (124 + 48 + 79) |

Detalle del backend por clase `[cmd: log de surefire]`: ArchivoBackendApplicationTests 1 · DocumentoDigitalControllerTest 6 · WorkspaceControllerTest 3 · AuthIntegrationTest 8 · ContenedorIntegrationTest 6 · ExpedienteIntegrationTest 6 · PrestamoIntegrationTest 3 · RolIntegrationTest 6 · OcrClientTest 46 · OcrQueueIntegrationTest 6 (**omitidas**) · OcrQueueSchedulerTest 6 · OcrWorkerTest 12 · DocumentoImageBatchServiceTest 16 · FileSystemStorageServiceTest 5.

Limitaciones de la medición (importante para el informe):
- Las **6 pruebas omitidas** (`OcrQueueIntegrationTest`) solo se habilitan con `-Docr.test.integration=true` **y** una base aislada con URL exacta `jdbc:postgresql://127.0.0.1:55439/ocr_s4_isolated`; al forzarlas sin esa base fallan por esa protección (no por un defecto del código). **No se ejecutaron.**
- `test_live_pdf.py` (OCR) **no se ejecutó**: no estaba en el comando de la suite.
- Conteos estáticos (no son ejecuciones): 100 `@Test` en Java, 40 `it(` en specs y 79 `def test_` en Python; la diferencia con lo ejecutado la explican las pruebas parametrizadas.
- Estas pruebas **no cubren** la interfaz en navegador; no hay pruebas end-to-end automatizadas.

### 2.5 Commits totales

Backend 38 · Frontend 26 · **Total 64** `[cmd: git rev-list --count HEAD en cada repo]`.

---

## 3. Historial git por autor

Autores detectados `[cmd: git log --format=%an]`: `truki20`, `Tade17`, `LenninSc`. No hay variantes de nombre para la misma persona.

**Cobertura:** el historial git del backend empieza el **2026-09-12 (semana 6)** y el del frontend el **2026-09-16 (semana 7)**. De la semana 1 (03/08) a la 5 **no hay commits**: lo trabajado antes **NO VERIFICADO** con git.

**Identidades:** que `truki20` sea el compañero Jorge es dato `[usuario]`; no consta en git. De `LenninSc` (2 commits de limpieza de comentarios el 19/10 en cada repo) **no hay información de quién es**: NO VERIFICADO.

### 3.1 Primer y último commit, y cantidad por autor

| Repo | Autor | Commits | Primer commit | Último commit |
|---|---|---:|---|---|
| Backend | truki20 | 19 | 2026-09-20 (`9a8e0f9`) | 2026-10-14 (`91ab901`) |
| Backend | Tade17 | 17 | 2026-09-12 (`bfcbfc2`) | 2026-10-19 (`793eebd`) |
| Backend | LenninSc | 2 | 2026-10-19 (`bfd9ce3`) | 2026-10-19 (`5e9ea42`) |
| Frontend | Tade17 | 12 | 2026-09-16 (`d7d3313`) | 2026-10-15 (`d411643`) |
| Frontend | truki20 | 12 | 2026-09-20 (`ddce5b0`) | 2026-10-14 (`9167eb9`) |
| Frontend | LenninSc | 2 | 2026-10-19 (`0362b7e`) | 2026-10-19 (`f0a62b0`) |

Fechas = fecha de autor (`git log --date=short`); semana 1 = lunes 2026-08-03, semanas de lunes a domingo.

### 3.2 Commits por semana y autor

**Backend**

| Sem. | Rango | LenninSc | Tade17 | truki20 | Total |
|---:|---|---:|---:|---:|---:|
| 6 | 09-07 a 09-13 | 0 | 4 | 0 | 4 |
| 7 | 09-14 a 09-20 | 0 | 2 | 1 | 3 |
| 8 | 09-21 a 09-27 | 0 | 3 | 2 | 5 |
| 9 | 09-28 a 10-04 | 0 | 2 | 3 | 5 |
| 10 | 10-05 a 10-11 | 0 | 3 | 0 | 3 |
| 11 | 10-12 a 10-18 | 0 | 2 | 13 | 15 |
| 12 | 10-19 a 10-25 | 2 | 1 | 0 | 3 |

**Frontend**

| Sem. | Rango | LenninSc | Tade17 | truki20 | Total |
|---:|---|---:|---:|---:|---:|
| 7 | 09-14 a 09-20 | 0 | 1 | 1 | 2 |
| 8 | 09-21 a 09-27 | 0 | 3 | 2 | 5 |
| 9 | 09-28 a 10-04 | 0 | 2 | 2 | 4 |
| 10 | 10-05 a 10-11 | 0 | 4 | 0 | 4 |
| 11 | 10-12 a 10-18 | 0 | 2 | 7 | 9 |
| 12 | 10-19 a 10-25 | 2 | 0 | 0 | 2 |

### 3.3 Commits de Tade17 (fecha, semana, hash, asunto)

**Backend (17)**

| Fecha | Sem. | Hash | Asunto |
|---|---:|---|---|
| 2026-09-12 | 6 | bfcbfc2 | agregando readme y archivo dockerfile para que no tengas que levantar bd sino trabajas directo |
| 2026-09-12 | 6 | 6cd030b | version inicial:CRUD completo , jwt, RBAC,auditoria,paginacion,storage y tests |
| 2026-09-13 | 6 | 31de883 | Feat : Implementando swagger |
| 2026-09-13 | 6 | 0d5b62b | Documentacion agregada |
| 2026-09-14 | 7 | 04124b0 | feat:Arreglando la paginacion |
| 2026-09-16 | 7 | 66b9428 | feat:merge con el repositorio de fiestas |
| 2026-09-25 | 8 | c3f80f8 | feat: alinear Explorar, documento digital, auditoría y usuarios con el diseño |
| 2026-09-25 | 8 | 4bd632f | feat: búsqueda sin tildes en Explorar y datos de prueba municipales |
| 2026-09-26 | 8 | 7caa538 | cambios pequeños nomas pa |
| 2026-09-30 | 9 | e267986 | feat:Cambios |
| 2026-10-04 | 9 | 9fbb0cb | feat: eliminar edición del texto OCR y corregir generación de PDF |
| 2026-10-11 | 10 | aa6bece | Préstamos a entidades externas con semáforo de vencimiento (V11) |
| 2026-10-11 | 10 | 24b9281 | Estado único del documento y eliminación de expedientes por el administrador (V10) |
| 2026-10-11 | 10 | 5600a61 | feat: reincorporar ubicación física con archivadores y folders |
| 2026-10-14 | 11 | eaded9d | dsa |
| 2026-10-15 | 11 | ada4bea | V14: crea el Archivo Central inicial; Dockerfile con caché y reintentos |
| 2026-10-19 | 12 | 793eebd | Despliegue con un solo clon: el frontend se descarga al construir |

**Frontend (12)**

| Fecha | Sem. | Hash | Asunto |
|---|---:|---|---|
| 2026-09-16 | 7 | d7d3313 | Commit inicial del frontend separado |
| 2026-09-25 | 8 | c200c9e | feat(front): alinear pantallas al diseño y añadir búsqueda dentro del documento |
| 2026-09-25 | 8 | 11e887b | feat: alinear Explorar, documento digital, auditoría y usuarios con el diseño |
| 2026-09-25 | 8 | 0caff5c | feat: búsqueda dentro del documento sin tildes, con resaltado exacto y navegación |
| 2026-09-30 | 9 | 1816e79 | Feat :Cambios minimos en el diseño |
| 2026-10-04 | 9 | cdd2e00 | feat: quitar la corrección de texto OCR de la interfaz |
| 2026-10-11 | 10 | 825dc8e | feat :Un error en alerta |
| 2026-10-11 | 10 | e1faa3c | Sección de préstamos con semáforo y alerta de vencidos |
| 2026-10-11 | 10 | 115e349 | Catálogos administrables y eliminación de documentos para el administrador |
| 2026-10-11 | 10 | c071663 | feat: ubicación física, archivadores y folders |
| 2026-10-14 | 11 | 60ab093 | para levantar todo |
| 2026-10-15 | 11 | d411643 | feat : tolerancia para la instalacion de dependencias |

Los asuntos de `aa6bece`, `24b9281`, `e1faa3c` y `115e349` están resumidos en estas tablas (el mensaje real es más largo); el texto completo está en `git log`.

---

## 4. Atribución por funcionalidad (basada en `git log` y `git blame`)

**Método** `[cmd: script sobre git ls-files + git log -- <archivos> + git blame --line-porcelain -w]`:
1. Se agruparon los archivos de cada funcionalidad por nombre/ruta (patrones por funcionalidad y repo).
2. "Líneas vivas" = líneas del archivo actual atribuidas por `git blame -w` a cada autor.
3. **Se excluyeron** del blame y del conteo de commits los 4 commits de limpieza de comentarios de `LenninSc` del 19/10 (`5e9ea42`, `bfd9ce3`, `f0a62b0`, `0362b7e`): tocan 65 + 18 archivos y falsearían la atribución. Con ellos no excluidos, `LenninSc` aparecería en casi todo con líneas que son solo comentarios.
4. "Autor principal" = mayor número de líneas vivas. "Apoyo" = el siguiente autor con líneas vivas.
5. Las funcionalidades comparten archivos en algunos casos (p. ej. `V9__` solo cuenta en Contenedores, `WorkspaceController` no se asigna a ninguna por mezclar varias).

| Funcionalidad | Repo | Archivos | Autor principal | Líneas vivas por autor | Commits por autor (sin limpieza) | Commits de respaldo (hasta 5 por autor) |
|---|---|---:|---|---|---|---|
| Login / JWT | BE | 9 | Tade17 | Tade17 472 (94%), truki20 29 (5%) | Tade17 4, truki20 1 | Tade17: 66b9428 04124b0 31de883 6cd030b · truki20: 9a8e0f9 |
| Login / JWT | FE | 7 | Tade17 | Tade17 537 (100%) | Tade17 1 | d7d3313 |
| Roles y permisos (RBAC) | BE | 12 | Tade17 | Tade17 452 (95%), truki20 20 (4%) | truki20 1, Tade17 1 | Tade17: 6cd030b · truki20: 9a8e0f9 |
| Roles y permisos (RBAC) | FE | 4 | truki20 | truki20 325 (85%), Tade17 56 (14%) | Tade17 2, truki20 1 | Tade17: 11e887b d7d3313 · truki20: ddce5b0 |
| Auditoría / Actividad | BE | 10 | Tade17 | Tade17 310 (99%), truki20 1 | Tade17 2, truki20 1 | Tade17: 66b9428 6cd030b · truki20: 9a8e0f9 |
| Auditoría / Actividad | FE | 3 | truki20 | truki20 179 (70%), Tade17 76 (29%) | Tade17 2, truki20 1 | Tade17: 11e887b d7d3313 · truki20: ddce5b0 |
| Almacenamiento de archivos | BE | 15 | truki20 | truki20 692 (54%), Tade17 575 (45%) | truki20 6, Tade17 5 | truki20: f1c7261 fd5a862 5321588 e0225b9 b4ae7be · Tade17: 24b9281 9fbb0cb 66b9428 04124b0 6cd030b |
| Almacenamiento de archivos | FE | 7 | Tade17 | Tade17 568 (54%), truki20 479 (45%) | truki20 5, Tade17 5 | truki20: 2fb143d 6090909 397e5ad c4b7a26 ddce5b0 · Tade17: 115e349 cdd2e00 11e887b 0caff5c d7d3313 |
| Swagger / OpenAPI | BE | 1 | Tade17 | Tade17 31 (100%) | Tade17 1 | 31de883 |
| Docker / Compose / despliegue | BE | 8 | Tade17 | Tade17 178 (52%), truki20 160 (47%) | truki20 8, Tade17 5 | Tade17: 793eebd ada4bea eaded9d 9fbb0cb bfcbfc2 · truki20: bd025dc aec4ee9 b5e9b36 5321588 b4ae7be |
| Docker / Compose / despliegue | FE | 3 | Tade17 | Tade17 49 (100%) | Tade17 2 | d411643 60ab093 |
| Paginación | BE | 1 | Tade17 | Tade17 22 (100%) | Tade17 1 | 6cd030b |
| Paginación | FE | 1 | Tade17 | Tade17 4 (100%) | Tade17 1 | d7d3313 |
| Búsqueda y unaccent | BE | 2 | truki20 | truki20 129 (96%), Tade17 4 (3%) | truki20 1, Tade17 1 | truki20: a12afdb · Tade17: 4bd632f |
| Búsqueda y unaccent | FE | 3 | Tade17 | Tade17 340 (64%), truki20 189 (35%) | Tade17 5, truki20 1 | Tade17: 115e349 c071663 1816e79 11e887b d7d3313 · truki20: ddce5b0 |
| Servicio OCR (RapidOCR/FastAPI + cliente Java) | BE | 12 | truki20 | truki20 2 012 (99%), Tade17 4 | truki20 9, Tade17 1 | truki20: aec4ee9 fd5a862 b5e9b36 9d3969a 5321588 · Tade17: 9fbb0cb |
| PDF con texto buscable (capa invisible) | BE | 6 | truki20 | truki20 549 (99%), Tade17 4 | truki20 4, Tade17 1 | truki20: 9d3969a e0225b9 91ab901 b4ae7be · Tade17: 9fbb0cb |
| Reanudación del OCR / cola durable | BE | 10 | truki20 | truki20 1 366 (100%) | truki20 3 | fd5a862 b5e9b36 5321588 |
| Carga de imágenes en orden (PDF combinado + originales) | BE | 5 | truki20 | truki20 562 (100%) | truki20 2 | fd5a862 e0225b9 |
| Carga de imágenes en orden | FE | 4 | truki20 | truki20 1 247 (86%), Tade17 187 (13%) | truki20 5, Tade17 5 | truki20: f98f8a2 9167eb9 397e5ad c4b7a26 ddce5b0 · Tade17: 115e349 c071663 1816e79 c200c9e d7d3313 |
| Ubicaciones (sección/nivel/área) | BE | 33 | Tade17 | Tade17 1 092 (100%) | Tade17 3, truki20 1 | Tade17: ada4bea 5600a61 6cd030b · truki20: 9a8e0f9 |
| Ubicaciones (sección/nivel/área) | FE | 2 | Tade17 | Tade17 322 (100%) | Tade17 1 | c071663 |
| Contenedores (archivador/folder) | BE | 11 | Tade17 | Tade17 669 (100%) | Tade17 2 | aa6bece 5600a61 |
| Contenedores (archivador/folder) | FE | 2 | Tade17 | Tade17 486 (100%) | Tade17 2 | e1faa3c c071663 |
| Préstamos y semáforo | BE | 10 | Tade17 | Tade17 549 (99%), truki20 2 | Tade17 5, truki20 1 | Tade17: aa6bece 24b9281 66b9428 04124b0 6cd030b · truki20: 9a8e0f9 |
| Préstamos y semáforo | FE | 2 | Tade17 | Tade17 425 (100%) | Tade17 3, truki20 1 | Tade17: 825dc8e e1faa3c d7d3313 · truki20: ddce5b0 |
| Catálogos (áreas de destino y tipos) | BE | 25 | Tade17 | Tade17 726 (98%), truki20 10 (1%) | Tade17 2, truki20 1 | Tade17: 24b9281 6cd030b · truki20: 9a8e0f9 |
| Catálogos (áreas de destino y tipos) | FE | 3 | Tade17 | Tade17 123 (53%), truki20 108 (46%) | Tade17 2, truki20 1 | Tade17: 115e349 d7d3313 · truki20: ddce5b0 |
| Seeder de demostración | BE | 5 | Tade17 | Tade17 1 155 (100%) | Tade17 4 | 24b9281 5600a61 7caa538 4bd632f |
| Guías (DESPLIEGUE, OCR, README, DOCUMENTACION, ACTUALIZACION) | BE | 6 | Tade17 | Tade17 551 (62%), truki20 335 (37%) | truki20 17, Tade17 9 | Tade17: 793eebd eaded9d 5600a61 e267986 7caa538 · truki20: bd025dc 79b88a1 aec4ee9 f1c7261 fd5a862 |
| Guías (README del frontend) | FE | 1 | truki20 | truki20 88 (80%), Tade17 21 (19%) | truki20 1, Tade17 1 | truki20: 8d453ae · Tade17: d7d3313 |
| **Manual de usuario** | — | — | **NO VERIFICADO** | — | — | No está versionado en ningún repo `[cmd: git ls-files | grep -i manual\|guia]`. Existen fuera de git, en la carpeta raíz del proyecto (que no es un repositorio): `guia-sistema-archivo.html` (modif. 2026-09-20), `guia_flujo_sistema_archivo_municipal.md` (2026-09-15), `resumen-sistema.html` (2026-09-17). No se puede atribuir autoría con git. |

**Total de líneas vivas** (solo los archivos de las funcionalidades listadas, sin los 4 commits de limpieza): Backend → Tade17 6 794 (53 %), truki20 5 867 (46 %). Frontend → Tade17 3 194 (54 %), truki20 2 615 (45 %).

### Advertencias de atribución (leer antes de redactar)
- **Git muestra quién hizo el commit y de quién es cada línea tras el último cambio; no prueba quién la redactó ni cómo.** Un `blame` sobre código movido, formateado o reescrito atribuye al último que lo tocó.
- **No hay ningún trailer `Co-Authored-By` (de ningún tipo) en los 64 commits** `[cmd: git log --format=%B | grep -ci co-authored-by → 0 en ambos repos]`. El historial **no distingue** qué parte se desarrolló con asistencia de herramientas de IA: si la normativa de la PPP exige declarar ese uso, esa información **no sale de git** y hay que declararla aparte.
- **Origen previo NO VERIFICADO** de parte del código del 16/09: el commit `66b9428` de Tade17 se llama "merge con el repositorio de fiestas" (313 líneas insertadas, 11 archivos, un solo padre, no es un merge real de git). La procedencia de ese contenido **no consta en este historial**.
- Las filas de "Ubicaciones" y "Préstamos" en backend aparecen 99–100 % de Tade17 porque los archivos se (re)crearon en `5600a61` / `aa6bece`; los controladores `Estante/Nivel/Caja/ArchivoCentral` fueron restaurados desde el historial anterior (commit `66b9428`).
- Cuenta de commits ≠ esfuerzo: en la semana 11 el backend tiene 13 commits de `truki20` y varios son documentación de seguimiento.

---

## 5. Qué hace el sistema HOY

### 5.1 Roles y permisos reales (efectivos = `SecurityConfig` + `@PreAuthorize` + guards del frontend)

Tres roles `[BD: tabla rol; V2/V3/V5]`: `ADMIN`, `GESTOR_DOCUMENTAL`, `LECTOR`. Autenticación JWT sin estado, sin refresh token `[SecurityConfig.java; AuthController: 1 endpoint]`. Reglas globales `[SecurityConfig.java líneas 106–118]`: `/api/auth/**` y Swagger públicos; `/api/auditoria/**` y `/api/usuarios/**` solo ADMIN; `GET /api/**` cualquier autenticado; `POST|PUT /api/expedientes/**` y `/api/documentos-digitales/**` ADMIN o GESTOR; el resto de `/api/**` solo ADMIN; `/api/workspace/**` exige autenticación y cada método fija su rol con `@PreAuthorize`.

| Capacidad | ADMIN | GESTOR | LECTOR |
|---|:---:|:---:|:---:|
| Explorar/buscar, ver ficha, vista previa y descarga de archivos y originales | ✔ | ✔ | ✔ |
| Registrar y editar documentos (`/workspace/recepcion`, `PUT /workspace/expedientes/{id}`) | ✔ | ✔ | ✖ |
| Subir archivos y reintentar OCR (`POST /documentos-digitales/**`) | ✔ | ✔ | ✖ |
| Crear/editar/completar/reabrir/eliminar archivadores y folders | ✔ | ✔ | ✖ (API de lectura sí; la pantalla no) |
| Préstamos: registrar, devolver, listar, resumen | ✔ | ✔ | ✖ (solo ve el indicador `prestado`) |
| Eliminar archivo digital (`DELETE /documentos-digitales/{id}`) | ✔ | ✖ | ✖ |
| Eliminar expediente (`DELETE /expedientes/{id}`) | ✔ | ✖ | ✖ |
| Crear/editar/quitar Secciones, Niveles, Áreas, Archivo Central | ✔ | ✖ | ✖ (leer: todos) |
| Escribir catálogos (áreas de destino, tipos, estados) | ✔ | ✖ | ✖ (leer: todos) |
| Usuarios (listar, crear, editar, desactivar) | ✔ | ✖ | ✖ |
| Roles (API crear/editar/eliminar) | ✔ (**sin pantalla**) | ✖ | ✖ |
| Actividad / auditoría | ✔ | ✖ | ✖ |

Fuentes: tabla generada leyendo `@PreAuthorize` de los 16 controladores `[cmd: script sobre controller/*.java]` y `[SecurityConfig.java]`; probado en vivo con los tres roles (86 comprobaciones, 85 correctas; la restante fue un error de mi script).

### 5.2 Opciones del menú por rol `[app-shell.component.ts; app.routes.ts; auth.service.ts: can()]`

`can(...roles)` deja pasar siempre a ADMIN; un guard `roles()` sin argumentos significa "solo ADMIN" y redirige a `/buscar`.

| Opción del menú | Ruta | Quién la ve |
|---|---|---|
| Explorar | `/buscar` | Todos |
| Archivadores y folders | `/contenedores` | GESTOR y ADMIN |
| Préstamos | `/prestamos` (con contador rojo de vencidos) | GESTOR y ADMIN |
| Ubicaciones | `/ubicaciones` | Solo ADMIN |
| Actividad | `/auditoria` | Solo ADMIN |
| Usuarios | `/usuarios` | Solo ADMIN; desde ahí se llega a **Catálogos** (`/catalogos`, solo ADMIN) |

- **"Cargar varios documentos" (`/digitalizar`) ya no está en el menú** (lo retiró el compañero el 14/10); la ruta sigue existiendo, solo GESTOR/ADMIN. La carga se hace desde la ficha del expediente ("Añadir documentos") o al crearlo.
- Botones por rol en fichas: Editar ficha, Añadir documentos, Prestar documento físico → GESTOR/ADMIN; Eliminar expediente y Eliminar archivo → solo ADMIN; Reintentar OCR → GESTOR/ADMIN `[expediente-detail.component.ts, documento-detalle.component.ts]`.
- Al iniciar sesión los usuarios sin rol suficiente que abren una ruta restringida son redirigidos a Explorar.

### 5.3 Flujo de contenedores `[ContenedorServiceImpl.java; ExpedienteServiceImpl.java; V9]`

Jerarquía física: Archivo Central → Sección (tabla `estante`) → Nivel → Área (tabla `caja`) → **Contenedor** (ARCHIVADOR o FOLDER) → Documento (tabla `expediente`) → Archivo(s) digitales.

- Un contenedor **nace PENDIENTE** (`EstadoDigitalizacion`: PENDIENTE | COMPLETADO).
- **Completar:** exige al menos un documento ("Registra al menos un documento antes de marcar el contenedor como completado").
- **Contenedor COMPLETADO:** rechaza nuevos documentos ("El contenedor está marcado como completado. Reábrelo para agregar documentos"); tampoco se puede mover un documento a uno completado.
- **Reabrir:** vuelve a PENDIENTE.
- **Eliminar:** solo si está vacío y sin préstamos registrados.
- Listado: los PENDIENTES primero y luego por último cambio; filtros por estado y texto; fecha y autor de creación guardados.
- Cualquier gestor puede retomar un contenedor que dejó otro.

### 5.4 Reglas de préstamos `[PrestamoServiceImpl.java; PrestamoController.java; V11; PrestamoIntegrationTest]`

- Préstamo **físico** a una **entidad externa** (texto libre de hasta 200 caracteres, p. ej. "Contraloría - Sr. Pérez"), no a un usuario del sistema.
- Se presta **un documento O un archivador/folder completo** (exactamente uno; lo exige un CHECK en la BD). Si se presta un contenedor, **sus documentos cuentan como prestados**.
- **Plazo:** días elegidos en cada préstamo; **7 por defecto**, mínimo 1, máximo 365; la fecha prevista es hoy + días. Observaciones opcionales (hasta 500 caracteres).
- **Doble préstamo:** no se presta algo ya prestado, ni un documento cuyo archivador está prestado, ni un archivador que tiene un documento prestado; un contenedor sin documentos no se presta; además hay índices únicos parciales en la BD.
- **Semáforo** (calculado al consultar, `DIAS_AVISO = 2`): GRIS = devuelto · **ROJO** = fecha prevista anterior a hoy · **AMARILLO** = vence en 2 días o menos · VERDE = en plazo.
- **Alerta:** contador (sin devolver / por vencer / vencidos) y, si hay vencidos, franja roja global y número en el menú, solo para ADMIN/GESTOR.
- Devolver: una sola vez; un documento devuelto puede volver a prestarse. No se eliminan expedientes ni contenedores con préstamos; los préstamos no se borran.
- Solo ADMIN y GESTOR acceden; el LECTOR ve únicamente que el documento está prestado, sin datos del solicitante.

### 5.5 Estados del OCR `[EstadoOcr.java; OcrWorker.java; job_store.py; OCR.md]`

- Estados del documento: `PENDIENTE`, `PROCESANDO`, `COMPLETADO`, `REQUIERE_REVISION`, `ERROR`.
- **REQUIERE_REVISION** si no hay texto o la confianza es menor a **0.75** (`app.ocr.review-threshold`); el PDF resultante se puede consultar igual.
- Cola persistente en PostgreSQL con lease y heartbeat (V13); **hasta 5 reintentos automáticos** con espera creciente (`MAX_RETRIES = 5`); **reanudación por página** con puntos de control en el servicio OCR.
- Estados del trabajo en el servicio OCR: QUEUED, RUNNING, COMPLETED, ERROR, CANCELLED, ACKNOWLEDGED; fases visibles en la interfaz: en cola, reconociendo, exportando, completado `[job_store.py; OCR.md]`.
- El resultado es un PDF con la imagen original y capa de texto invisible, seleccionable y buscable. **El texto OCR no se edita** (eliminado el 04/10: commits `9fbb0cb`, `cdd2e00`).
- Las imágenes de una carga se unen en un solo PDF (`imagenes.pdf`) en el orden elegido y **se conservan los originales** (V12 `originales_manifest`); los PDF se guardan por separado.
- Límites `[app.py; frontend; OCR.md]`: 100 archivos por carga, 20 MB por archivo, 150 MB por lote, 20 MB para el grupo de imágenes, 100 páginas, 250 millones de píxeles acumulados, PDF OCR derivado hasta 128 MiB.
- Medido hoy en la instalación Docker: un lote de 1 PDF y 2 imágenes se procesó en 52–62 s con confianza 98 % (el PDF); un PDF de prueba de una página tardó 12 s con 99 %. Son mediciones puntuales, no un benchmark.

### 5.6 Auditoría `[AccionAuditoria.java; AuditoriaAspect; @Auditable; WorkspaceController]`

- Acciones: `CONSULTAR`, `CREAR`, `MODIFICAR`, `DESCARGAR`, `ELIMINAR`. Tabla `auditoria` con usuario, entidad, id de entidad, acción, fecha y `detalle` (jsonb, en la práctica vacío).
- **Entidades auditadas** con `@Auditable` (14): ArchivoCentral, AreaResponsable, Caja, Contenedor, DocumentoDigital, EstadoExpediente, Estante, Expediente, Nivel, Prestamo, Rol, Tag, TipoDocumental, Usuario. Además, `WorkspaceController` inserta a mano `MODIFICAR` de expediente y `CONSULTAR` al abrir la vista previa de un documento.
- **No se audita** el inicio de sesión (no hay `@Auditable` en `AuthController`).
- Pantalla "Actividad" (solo ADMIN) con filtros por acción, módulo, usuario y fechas, y contadores del día.
- Dato de uso, **no reproducible hoy**: el 11/10 se midió sobre la base de desarrollo que la tabla `auditoria` tenía 3 561 filas, de las cuales 3 239 eran "CONSULTAR" y 3 032 correspondían a un único documento (ráfagas del 17, 20 y 24 de septiembre, por un error del visor que ya se corrigió). Esa base fue eliminada después y respaldada fuera del repositorio, así que la cifra no se puede volver a consultar.

### 5.7 Datos iniciales de una instalación nueva `[V2, V3, V10, V14; cmd: consulta a BD limpia]`

1 usuario administrador semilla, 3 roles, 1 estado de expediente ("Registrado"), 4 tipos documentales (Informe, Carta, Memorando, Solicitud) y 1 Archivo Central ("Archivo Central Municipal"). Todo lo demás nace vacío. Los usuarios y documentos de demostración **no** se cargan (existen solo en el seeder `scripts/seed-demo`).

### 5.8 Lo que NO está implementado (con evidencia)

| Elemento | Estado | Evidencia |
|---|---|---|
| **Borrado de expedientes** | **SÍ implementado**, solo ADMIN, registrado en auditoría; se rechaza si tiene préstamos | `DELETE /api/expedientes/{id}` `[ExpedienteController]`, prueba `ExpedienteIntegrationTest`. Corrige la suposición de que no existía: `DOCUMENTACION.md` aún dice lo contrario |
| Borrado de archivos digitales | Implementado, solo ADMIN, con botón en la interfaz | `DELETE /documentos-digitales/{id}`; `documento-detalle` y `expediente-detail` |
| PDF/A validado o certificado | **No** | solo existe una columna informativa `formato_salida` ("PDF/A, TIFF") en `V1__init_schema.sql`; ningún código valida PDF/A |
| Firma digital / sellado | **No** | `grep` de "firma digital/electrónica": solo aparece "signature" en el sentido de bytes mágicos de archivo |
| Notificaciones (correo, push, SMS) | **No** | sin `spring-boot-starter-mail` ni código de envío; la alerta de préstamos es solo visual dentro del sistema |
| Cambio de contraseña por el propio usuario | **No** | no hay endpoint; la fija el ADMIN al crear/editar usuarios |
| Refresh token / cierre de sesión con revocación | **No** | `AuthController` solo tiene `login`; JWT de 8 h |
| Pantalla para administrar roles | **No** | `RolController` existe (API), sin pantalla; los 3 roles están fijados en el código |
| Ampliar el plazo de un préstamo / editarlo | **No** | `PrestamoService` solo tiene `crear` y `devolver` |
| Reportes y exportación (Excel/PDF de listados) | **No** | sin código relacionado |
| Edición del texto OCR | **Eliminada a propósito** | commits `9fbb0cb`, `cdd2e00` (04/10) |
| Filtros por ubicación/autor en archivadores, ilustración del archivo ("andamio") | **No** (pendientes acordados) | no existen en el código |
| CRUD de etiquetas (`Tag`) | **No** | hay servicio y auditoría, sin controlador |
| CI en GitHub Actions | **No** | no hay `.github/` |
| Pruebas end-to-end en navegador | **No** | solo pruebas unitarias e integración de API |
| Despliegue **probado en la máquina real de la municipalidad** | **NO VERIFICADO** | se probó con instalación limpia en esta laptop (clon desde GitHub); el resultado en el equipo de la municipalidad no consta en el repositorio |

---

## 6. Discrepancias entre documentación y código real

`DOCUMENTACION.md` es el más desfasado: su último commit es del 30/09 `[cmd: git log -- DOCUMENTACION.md → e267986]`, anterior a ubicaciones, contenedores, préstamos y a todo el rediseño del OCR.

### 6.1 `DOCUMENTACION.md` (backend)

| Afirma | Realidad en el código |
|---|---|
| "El sistema ya no maneja ubicación física ni préstamos… no hay Archivo Central/Estante/Nivel/Caja ni módulo de Préstamos expuestos" | Existen y están expuestos: `ArchivoCentral/Estante/Nivel/CajaController` y `PrestamoController`; contenedores, V9 y V11 |
| Sección 9.9: "ArchivoCentral, Caja, Estante, Nivel y PrestamoController fueron eliminados el 20/09" | Fueron **reincorporados** (commit `5600a61`, V9 y V11). Solo `TagController` sigue sin existir |
| Sección 11: "módulo de Préstamo… código muerto" | Ya no es código muerto: 5 endpoints, entidad rehecha y 3 pruebas de integración |
| "Expediente nunca se borra (sin `DELETE`)" | Existe `DELETE /api/expedientes/{id}` solo ADMIN (commit `24b9281`) |
| Modelo: `cajaId` opcional en `ExpedienteRequestDTO`; Response con `cajaId/cajaCodigo` | El DTO tiene **`contenedorId` obligatorio** y `destinatario`; no hay `cajaId` (V9 la eliminó de `expediente`) |
| Sección 8 "Corrección manual": `PUT /documentos-digitales/{id}` acepta `ocrTexto`; campo `ocrRevisado` | `DocumentoDigitalActualizarDTO` ya **no tiene `ocrTexto`** (edición eliminada); texto OCR de solo lectura |
| Sección 8: "al reiniciar, los documentos en PROCESANDO vuelven a PENDIENTE"; `REQUIERE_REVISION` = "alguien debe corregir a mano" | Ahora hay cola durable con lease y reanudación por página (V13); no se corrige a mano |
| 9.5 Documentos digitales: no lista `/originales`, `/originales/{orden}` ni `/pdf`; marca reintentar como "🔐" cualquier usuario | Existen `/originales` y `/originales/{orden}`; el reintento OCR exige ADMIN o GESTOR (regla en `SecurityConfig`) |
| 9.8 Workspace: lista 8 endpoints; filtro `cajaId`; body de recepción sin destinatario/folios/contenedor | Hay 8 endpoints en `WorkspaceController` más los de `ContenedorController` y `PrestamoController`; el filtro físico es `areaId` y existe `contenedorId`; la recepción pide `destinatario`, `numeroFolios`, `contenedorId` |
| Sección 3: token "HMAC-SHA384" | `Keys.hmacShaKeyFor(...)` elige el algoritmo según la longitud del secreto: el secreto por defecto (59 bytes) da HS384, pero el generado para Docker (64 bytes hex) da **HS512**. La afirmación solo vale para ciertos tamaños |
| Sección 10: "20 tests de integración" | Hay **130** reportadas (124 ejecutadas) en 14 clases |
| Sección 11 (estado al 29/09) e ítem "validación de MIME por contenido real pendiente" | Desactualizada; hoy se valida firma del archivo (`%PDF-` en `FileSystemStorageService`; `detectType` en `DocumentoImageBatchService`) |
| Nota del typo "V6__ocr_automatico.sql" en ACTUALIZACION | Ya no existe ese texto en `ACTUALIZACION_BD_DOCKER.md`; la nota quedó obsoleta |
| Rol de datos: "`Usuario` no se borra, se desactiva" | **Correcto** (`UsuarioServiceImpl.desactivar` pone `activo=false`) |

### 6.2 `README.md` (backend)

| Afirma | Realidad |
|---|---|
| "actualmente existen **V1 a V13**" | Hay **V1 a V14** (`V14__archivo_central_inicial.sql`) |
| Guía solo de desarrollo local; no menciona `DESPLIEGUE.md`, `docker-compose.prod.yml` ni `.env.example` | Existe un despliegue completo con un comando. Además, si el `.env` contiene `COMPOSE_FILE=docker-compose.prod.yml` (como indica `.env.example`), el `docker compose up -d --build` del paso 2 del README levantaría **la pila de producción** y no solo db + OCR |
| "Compose no inicia Java ni Angular" | Cierto solo para `docker-compose.yml`; `docker-compose.prod.yml` sí inicia backend y frontend |
| "Para comprobar una instalación nueva, valida V1–V13" | V1–V14 |
| Primer acceso: crear admin propio y "desactivar la cuenta semilla" | `DESPLIEGUE.md` indica cambiar la contraseña del admin semilla; las dos guías dan consejos distintos |
| Resto (JDK 17, puertos 5433/8091/8080/4200, variables `DB_*`, límites 100/20/150 MB) | **Coincide** con `docker-compose.yml`, `application.properties`, `app.py` y el frontend |

### 6.3 `DESPLIEGUE.md` (propio, del 15–19/10)

| Afirma | Realidad / inconsistencia |
|---|---|
| `archivo_ocr_artifacts`: "caché temporal… (no necesita respaldo)" | `OCR.md` y `ACTUALIZACION_BD_DOCKER.md` dicen que guarda trabajos y puntos de control y que para recuperar toda la instalación se necesita un respaldo coherente de ese volumen. Las guías se contradicen |
| "Actualizar: `git pull` y `docker compose up -d --build`" | `ACTUALIZACION_BD_DOCKER.md` exige antes detener, respaldar base y documentos y verificar la cola; `DESPLIEGUE.md` no lo menciona |
| Instalación con un solo clon (`FRONTEND_CONTEXT` por defecto a GitHub) | **Coincide** con `docker-compose.prod.yml` (probado desde un clon limpio) |
| "Los usuarios de demostración no existen en una instalación nueva" | **Comprobado** (1 usuario en BD limpia) |

### 6.4 `ACTUALIZACION_BD_DOCKER.md`

| Afirma | Realidad |
|---|---|
| "actualmente V1 a V13"; "la última es 13" | La última es **14** |
| "Qué cambió en el esquema" termina en V13 | Falta V14 (Archivo Central inicial) |
| Todos los comandos usan el contenedor `archivo-postgres`, detienen el backend con `Ctrl+C` y ejecutan `.\mvnw.cmd` | Eso vale solo para el flujo de **desarrollo**. En la pila de producción los contenedores se llaman `archivo-municipal-<servicio>-1`, se accede con `docker compose exec postgres …` y el backend es un contenedor; la guía no lo cubre |
| Descripción de V9–V11 "contenedores, estado registrado y préstamos externos" | **Correcto** |

### 6.5 `OCR.md`

Verificado contra `app.py`, `job_store.py` y `docker-compose.yml`: **sin discrepancias materiales**. Coinciden: 100 páginas, 250 millones de píxeles, 128 MiB por artefacto, 512 MiB de caché, hasta 8 trabajos (`max_count=8`), 32 artefactos, 220 DPI, hilos 2/1, estados del documento, rutas `/v2/jobs/*`, `/v2/artifacts/*` y rutas antiguas `/ocr`, `/pdf`, `/images/pdf`. Reservas:
- Dice instalar con `README.md` (guía de desarrollo) y no menciona `DESPLIEGUE.md` ni los volúmenes con prefijo de proyecto (`archivo-municipal_…`).
- La cifra de "16 MiB de metadatos/puntos de control por trabajo" **NO VERIFICADO** en el código.
- Menciona el diagnóstico de Windows con "Java 21/24"; en esta laptop Maven corre con JDK 25 y funcionó, pero no se probó el síntoma descrito.

### 6.6 `README.md` del frontend

| Afirma | Realidad |
|---|---|
| Producción necesita un servidor web que publique archivos, envíe `/api` al backend y devuelva la app en rutas del cliente; "`npm run build` no lo configura" | Ahora existen `Dockerfile` y `nginx.conf` que lo hacen (y `docker-compose.prod.yml`); el README no los menciona |
| "Las rutas actuales incluyen consulta, expedientes, carga y visor de documentos, además de funciones administrativas" | Omite contenedores, préstamos, ubicaciones y catálogos |
| Pasos: `npm ci`, `npm start`, `npm test -- --watch=false`, `npm run build` | **Coinciden**: 48 pruebas pasan y el build compila |
| Requiere `npm 11.16.0` | `packageManager` declara 11.16.0; en esta laptop está 11.17.0 (el build funcionó) |

### 6.7 Archivos fuera de git (no auditados)

`README.md`, `guia-sistema-archivo.html`, `guia_flujo_sistema_archivo_municipal.md` y `resumen-sistema.html` en la carpeta raíz del proyecto (fechas de modificación 15–20/09) **no están versionados** y son anteriores a V9–V14; su exactitud actual **NO VERIFICADO**.

### 6.8 Hallazgos de código sueltos

- `auth.interceptor.ts` termina con un comentario que afirma que "el interceptor no es necesario"; es **falso**: es el que añade `Authorization: Bearer`.
- `qrcode` declarado en `package.json` y sin uso en `src/`.
- Commit de limpieza de comentarios del 19/10 (`LenninSc`): solo comentarios en 65 archivos de backend y 18 de frontend; no cambió comportamiento (comprobado ignorando comentarios y espacios: en backend solo cambia `Auditable.java` y en frontend `Dockerfile`/`nginx.conf`).

---

## 7. Lo que no se pudo verificar (resumen)

- Historial de las semanas 1–5 (03/08–06/09): sin commits.
- Identidad real de `truki20` y de `LenninSc` (solo `[usuario]` para truki20).
- Origen del contenido del commit `66b9428` ("merge con el repositorio de fiestas").
- Qué parte del código se escribió con ayuda de IA (git no lo registra: 0 trailers).
- Autoría del manual de usuario y guías HTML fuera de git.
- Ejecución de las 6 pruebas `OcrQueueIntegrationTest` y de `test_live_pdf.py`.
- Versiones del classpath resuelto de Maven (se citan las del BOM).
- Despliegue en la máquina real de la municipalidad.
- Límite "16 MiB de metadatos" del OCR.
