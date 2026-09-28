# Actualización de la base de datos Docker

Esta guía permite levantar o actualizar la base de datos del proyecto sin perder la información existente. El esquema se administra con **Flyway**: al iniciar el backend, las migraciones pendientes se ejecutan automáticamente y una sola vez.

## Actualizar una instalación existente

1. Detén el backend, pero no elimines el volumen de PostgreSQL.
2. Actualiza el repositorio:

   ```bash
   git pull origin main
   ```

3. Inicia o confirma el contenedor de PostgreSQL:

   ```bash
   docker compose up -d --build
   docker compose ps
   ```

4. Opcionalmente, crea una copia de seguridad antes de migrar:

   ```bash
   docker exec archivo-postgres pg_dump -U postgres -d archivo_sanjose > backup_antes_de_actualizar.sql
   ```

5. Inicia el backend desde la raíz de `archivo_backend`:

   ```bash
   # Windows PowerShell
   .\mvnw.cmd spring-boot:run

   # Linux, macOS o Git Bash
   ./mvnw spring-boot:run
   ```

El backend se conecta por defecto a `localhost:5433`, que es el puerto publicado por `docker-compose.yml`. Flyway detectará la versión existente y aplicará únicamente las migraciones nuevas.

## Cambios de base incluidos

- `V4__modo_solo_digital.sql`: permite expedientes sin caja ni ubicación física.
- `V5__roles_claros_y_correlativos.sql`:
  - crea los perfiles `GESTOR_DOCUMENTAL` y `LECTOR`;
  - migra automáticamente los usuarios con roles anteriores;
  - crea el contador anual utilizado para generar números de expediente sin intervención del usuario.
- `V6__busqueda_sin_tildes.sql`: habilita `unaccent` para buscar sin escribir tildes.
- `V7__ocr_automatico.sql`: añade estados, confianza, errores y recuperación del reconocimiento automático sin eliminar transcripciones existentes.

Estas migraciones conservan los expedientes, documentos y usuarios existentes.

## Verificar que la actualización terminó

En el inicio del backend debe aparecer un mensaje similar a:

```text
Current version of schema "public": 7
Schema "public" is up to date
```

También se puede consultar directamente dentro del contenedor:

```bash
docker exec archivo-postgres psql -U postgres -d archivo_sanjose -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

Todas las filas deben tener `success = true` y la última versión debe ser `7`.

## Primera instalación

En un equipo sin datos previos basta con ejecutar:

```bash
docker compose up -d --build
./mvnw spring-boot:run
```

Flyway creará el esquema completo y aplicará las migraciones de `V1` a `V7`.

## Reglas importantes

- No edites una migración `V*.sql` que ya haya sido aplicada. Los cambios futuros deben ir en una migración nueva (`V8`, `V9`, etc.).
- `docker compose down` detiene los servicios, pero conserva la base.
- **No uses `docker compose down -v` en un entorno con información real**: la opción `-v` elimina completamente el volumen y sus datos.
- No ejecutes manualmente el contenido de las migraciones; deja que Flyway controle el orden y registre cada actualización.
- Los documentos cargados se guardan fuera de PostgreSQL, en `storage/documentos` por defecto. Conserva también esa carpeta al realizar respaldos o trasladar la instalación.

## Si Flyway informa un error de validación

1. No borres el volumen ni ejecutes `repair` de forma automática.
2. Confirma que el repositorio esté actualizado y que las migraciones antiguas no hayan sido modificadas localmente:

   ```bash
   git status
   git pull origin main
   ```

3. Guarda una copia con `pg_dump` y revisa el mensaje concreto de Flyway antes de realizar cualquier corrección.

En una instalación de desarrollo sin información que conservar, se puede reconstruir todo con `docker compose down -v` y `docker compose up -d`, pero esta operación es destructiva y no debe utilizarse en producción.

### Caso: una versión antigua aplicó OCR como V6

Algunas bases recibieron `V6__ocr_automatico.sql` antes de integrar la migración de búsqueda. En el repositorio actual, V6 corresponde a `busqueda_sin_tildes` y OCR es V7. Esto produce `Migration checksum mismatch for migration version 6` aunque los datos sigan intactos.

Antes de reconciliar una de estas bases, confirma en `flyway_schema_history` que la versión 6 dice `ocr automatico`, que las columnas `ocr_estado` y `ocr_confianza` ya existen y que falta la extensión `unaccent`. Haz un respaldo con `pg_dump`. Solo entonces:

1. Ejecuta `CREATE EXTENSION IF NOT EXISTS unaccent;` en esa base para aplicar el contenido de la V6 actual.
2. Ejecuta Flyway `repair` con las migraciones de este repositorio para alinear el registro de V6.
3. Ejecuta Flyway `migrate` con `skipExecutingMigrations=true` y `target=7` para registrar V7 sin repetir los cambios OCR que ya existen.
4. Inicia el backend normalmente y comprueba que Flyway valide las siete migraciones.

No uses `skipExecutingMigrations` en una base que todavía no tenga el esquema OCR: en ese caso V7 debe ejecutarse normalmente.
