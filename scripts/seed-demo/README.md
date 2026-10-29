# Datos de prueba (seed-demo)

Carga una municipalidad de ejemplo para probar el sistema completo y ver todos los reportes con datos. **Solo para desarrollo: no ejecutar en un entorno con información real.**

Todo entra por la API real del sistema (login, recepción, carga de PDF, préstamos…), así que también genera auditoría y correlativos como en uso normal.

## Qué crea

| Qué | Detalle |
|---|---|
| Catálogos | 4 áreas de destino y 4 tipos documentales |
| Ubicación física | 2 secciones × 3 niveles × 2 áreas y un archivador por oficina de origen (20); todos completados salvo dos, que se dejan pendientes |
| Expedientes | **30 expedientes con 59 documentos PDF** (informes, resoluciones, contratos, actas, licencias…), incluidos 2 escaneos simulados sin texto para probar el OCR |
| Usuarios | 4 activos (2 gestores y 2 lectores) y 1 cuenta **suspendida** |
| Préstamos | 6: uno vencido hace varios días, uno vencido hace 1 día, uno por vencer, uno en plazo, uno devuelto con atraso y un archivador completo |
| Actividad | Consultas y descargas de los lectores, y 2 eliminaciones de ejemplo (para los reportes de seguridad) |

Todos los nombres, DNI, RUC, placas y montos son ficticios.

## Usuarios creados

Contraseña de todos: `Demo2026!`

| Correo | Rol |
|---|---|
| rosa.chero@sanjose.gob.pe | GESTOR_DOCUMENTAL |
| jorge.panta@sanjose.gob.pe | GESTOR_DOCUMENTAL |
| miriam.torres@sanjose.gob.pe | LECTOR |
| segundo.damian@sanjose.gob.pe | LECTOR |
| carlos.vega@sanjose.gob.pe | GESTOR_DOCUMENTAL (suspendida: no puede ingresar) |

El administrador (`admin@sanjose.gob.pe`) ya existe por la migración V2; su contraseña inicial está en [DOCUMENTACION.md](../../DOCUMENTACION.md).

## Primera vez en un equipo (clon nuevo)

Requisitos: Git, Docker Desktop, JDK 17 o superior, Node.js 18 o superior (consulta el README del frontend para la versión recomendada).

Abre **cuatro terminales**. Los comandos son de PowerShell; en Linux/macOS usa `./mvnw` en lugar de `.\mvnw.cmd`.

**1. Base de datos** (carpeta del backend). Usa `-f docker-compose.yml` para no tomar un `.env` de producción por error:

```powershell
git clone https://github.com/Tade17/archivo_backend.git archivo-backend
git clone https://github.com/Tade17/archivo_frontend.git archivo-frontend
cd archivo-backend
docker compose -f docker-compose.yml up -d postgres
```

**2. Backend** (misma carpeta). Flyway crea las tablas solo. El OCR es opcional para ver los datos; sin él el backend funciona igual:

```powershell
$env:OCR_ENABLED = 'false'          # omite esta línea si levantaste el servicio OCR
.\mvnw.cmd spring-boot:run
```

**3. Frontend** (carpeta `archivo-frontend`):

```powershell
npm install
npm start                           # abre http://localhost:4200
```

**4. Datos de prueba** (carpeta `archivo-backend\scripts\seed-demo`), cuando el backend ya responda:

```powershell
cd archivo-backend\scripts\seed-demo
node seed-demo.mjs
```

Entra a http://localhost:4200 con `rosa.chero@sanjose.gob.pe` / `Demo2026!` (gestor) o con el administrador.

## Ya tengo el proyecto y hago pull

```powershell
# En cada repositorio
git pull

# Backend: reinícialo (Ctrl+C y de nuevo). Si el pull trajo migraciones nuevas, Flyway las aplica solo.
.\mvnw.cmd spring-boot:run

# Frontend: solo si cambió package.json
npm install
npm start

# Datos: agrega lo que falte sin duplicar lo que ya existe
cd archivo-backend\scripts\seed-demo
node seed-demo.mjs
```

El seeder se puede ejecutar cuantas veces haga falta: lo que ya existe (áreas, tipos, usuarios, ubicaciones, archivadores, expedientes, préstamos, actividad) se omite.

## Empezar de cero

Borra **toda** la base de desarrollo (y con ella los documentos de prueba):

```powershell
cd archivo-backend
docker compose -f docker-compose.yml down -v
docker compose -f docker-compose.yml up -d postgres
# reinicia el backend y vuelve a correr: node seed-demo.mjs
```

Si usaste el almacenamiento local de archivos, borra también la carpeta indicada en `STORAGE_BASE_DIR` (por defecto `storage/documentos`) para no dejar PDF huérfanos.

## Variables opcionales

| Variable | Para qué | Valor por defecto |
|---|---|---|
| `API_URL` | Backend en otra dirección | `http://localhost:8080/api` |
| `ADMIN_PASSWORD` | Si cambiaste la contraseña del administrador | la inicial de la migración V2 |
| `PG_CONTAINER` | Si tu contenedor de Postgres no se llama así | `archivo-postgres` |

Ejemplo: `$env:ADMIN_PASSWORD = 'mi-clave'; node seed-demo.mjs`

## Cómo funciona

Lo único que se ajusta por SQL (con `docker exec` sobre el contenedor de Postgres) son las fechas y el estado, porque la API siempre crea los registros con la fecha de hoy: la fecha de los expedientes y, en los préstamos, el momento de salida, el vencimiento y la devolución. Las fechas relativas ("hace 12 días") se calculan en hora de Lima.

| Archivo | Contenido |
|---|---|
| `data.mjs` | Usuarios, áreas, tipos y los expedientes con su documentación |
| `templates.mjs` | Plantillas de documentos administrativos |
| `pdf.mjs` | Generador de PDF sin dependencias |
| `seed-demo.mjs` | El script que lo carga todo |
