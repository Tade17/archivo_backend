# Actualizar sin perder datos

Esta guía actualiza una instalación local existente. Para una instalación nueva, sigue [README.md](README.md). Flyway administra el esquema: actualmente existen migraciones **V1 a V13**.

## 1. Preparar y respaldar

1. Espera, si es posible, a que terminen los trabajos OCR. Si quedan trabajos pendientes, conserva su volumen y no cambies modelos, DPI o hilos durante la actualización.
2. Detén **el backend anterior** con `Ctrl+C` en su terminal. No ejecutes dos versiones a la vez.
3. Conserva el contenedor PostgreSQL y ambos volúmenes. No uses `docker compose down -v`.
4. Respalda la base y la carpeta de documentos antes de migrar.

En PowerShell, desde `archivo_backend`, con PostgreSQL disponible:

```powershell
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = Join-Path (Split-Path (Get-Location).Path -Parent) "backups/archivo-$stamp"
New-Item -ItemType Directory -Path $backup -Force | Out-Null
$containerDump = "/tmp/archivo-$stamp.dump"
docker exec archivo-postgres pg_dump -U postgres -d archivo_sanjose -Fc -f $containerDump
if ($LASTEXITCODE -ne 0) { throw 'No se pudo respaldar PostgreSQL. No continuar.' }
docker cp "archivo-postgres:$containerDump" (Join-Path $backup 'database.dump')
if ($LASTEXITCODE -ne 0) { throw 'No se pudo copiar el respaldo. No continuar.' }
docker exec archivo-postgres pg_restore --list $containerDump
if ($LASTEXITCODE -ne 0) { throw 'El respaldo no pudo leerse. No continuar.' }
```

El comando escribe el binario dentro del contenedor y luego lo copia: evita la redirección binaria de PowerShell. Adapta usuario, base y contenedor si no usas Compose predeterminado. No escribas contraseñas en comandos compartidos.

Copia también la **carpeta real** configurada en `STORAGE_BASE_DIR`. Para la ubicación predeterminada:

```powershell
Copy-Item -LiteralPath '.\storage\documentos' -Destination (Join-Path $backup 'documentos') -Recurse
```

Si nunca hubo documentos, esa carpeta puede no existir. Si ya había documentos y falta, localiza el almacenamiento real antes de continuar. Leer el índice del dump confirma que es legible, no que una restauración completa haya sido probada.

El volumen `archivo_ocr_artifacts` contiene trabajos y puntos de control; **no está dentro del dump ni de la carpeta de documentos**. No lo borres ni cambies su nombre/proyecto Compose. Para trasladar el equipo o recuperar toda la instalación necesitas un respaldo coherente de ese volumen, además de la base y los documentos. Mantén los respaldos fuera del repositorio y con acceso restringido.

## 2. Obtener la versión actual

Dentro del repositorio, con cambios propios guardados o confirmados:

```powershell
git status
git switch main
git pull --ff-only origin main
```

Si hay divergencias o cambios locales incompatibles, detente y coordina la integración. No descartes cambios ni fuerces el historial.

## 3. Actualizar OCR antes que Java

```powershell
docker compose up -d postgres
docker compose up -d --build ocr
docker compose ps
Invoke-RestMethod http://localhost:8091/health
Invoke-RestMethod http://localhost:8091/v2/jobs/config
```

Espera `jobsReady: true` y una respuesta de configuración sin 404. Si usas otro puerto OCR, adapta las direcciones y `OCR_URL` del backend. Reconstruir OCR conserva los volúmenes declarados; no elimina documentos.

## 4. Iniciar Java y comprobar Flyway

En la misma terminal que contiene tus variables de configuración:

```powershell
.\mvnw.cmd spring-boot:run
```

En Linux/macOS: `./mvnw spring-boot:run`. Deja esta terminal abierta. Flyway aplica solo las migraciones pendientes; **no ejecutes los SQL manualmente**.

En otra terminal puedes comprobar el historial:

```powershell
docker exec archivo-postgres psql -U postgres -d archivo_sanjose -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

Todas las migraciones deben ser exitosas y, para esta versión, la última es **13**. Comprueba también Swagger en 8080, inicio de sesión y una carga sintética pequeña. Actualiza el frontend siguiendo [su README](https://github.com/Tade17/archivo_frontend#readme).

La disponibilidad de servicios y un historial correcto no sustituyen una prueba completa de carga y OCR.

## Qué cambió en el esquema

- V1–V3: esquema y datos iniciales.
- V4–V6: flujo digital, perfiles y correlativos, búsqueda sin tildes.
- V7–V8: reconocimiento automático y PDF con texto posicionado.
- V9–V11: contenedores, estado registrado y préstamos externos.
- V12: manifiesto de imágenes originales.
- V13: trabajos OCR, lease, reintentos y progreso por página.

Las migraciones no convierten ni borran los originales existentes. La cola no reintenta automáticamente todos los documentos históricos en ERROR.

## Si algo falla

- Conserva respaldos, archivos, volúmenes y el error exacto; no improvises una restauración sobre datos más nuevos.
- No edites migraciones ya aplicadas. Añade nuevas versiones para cambios futuros.
- No uses `repair`, `skipExecutingMigrations`, eliminación de columnas o reinicialización como soluciones generales.
- Si hay un checksum distinto, compara el historial y la versión del repositorio; una base antigua puede requerir reconciliación específica por un responsable, después de respaldar.
- Un rollback del código no implica un rollback seguro del esquema o de los trabajos OCR. Una versión antigua no puede continuar necesariamente los trabajos nuevos.
- `docker compose down` conserva volúmenes, pero no es necesario para esta actualización. **`down -v` los elimina.**

Para errores de red, puertos y selector Java, consulta [README.md](README.md) y [OCR.md](OCR.md).
