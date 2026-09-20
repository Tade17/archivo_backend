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
   docker compose up -d
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

Estas migraciones conservan los expedientes, documentos y usuarios existentes.

## Verificar que la actualización terminó

En el inicio del backend debe aparecer un mensaje similar a:

```text
Current version of schema "public": 5
Schema "public" is up to date
```

También se puede consultar directamente dentro del contenedor:

```bash
docker exec archivo-postgres psql -U postgres -d archivo_sanjose -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

Todas las filas deben tener `success = true` y la última versión debe ser `5`.

## Primera instalación

En un equipo sin datos previos basta con ejecutar:

```bash
docker compose up -d
./mvnw spring-boot:run
```

Flyway creará el esquema completo y aplicará las migraciones de `V1` a `V5`.

## Reglas importantes

- No edites una migración `V*.sql` que ya haya sido aplicada. Los cambios futuros deben ir en una migración nueva (`V6`, `V7`, etc.).
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
