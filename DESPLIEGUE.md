# Despliegue completo con Docker

Un solo comando levanta base de datos, OCR, backend y frontend.

## Requisitos
- Docker (Docker Desktop en Windows con la virtualización activada, o Docker Engine en Linux) con Docker Compose v2.
- Git (o descargar el repositorio como ZIP desde GitHub).
- Conexión a internet durante la primera construcción: descarga las imágenes base, las dependencias y el código del frontend.
- Unos 4 GB de RAM libres (el OCR es el servicio que más consume) y 15 GB de disco.

No hace falta instalar Java, Node, Python ni Postgres: todo corre dentro de los contenedores.

## Primera instalación
1. Clona **solo este repositorio** (el frontend se descarga solo al construir):

   ```
   git clone https://github.com/Tade17/archivo_backend.git
   cd archivo_backend
   ```
2. Copia la plantilla de configuración y complétala:

   ```
   copy .env.example .env      (en Linux: cp .env.example .env)
   ```

   Edita `.env` y completa:
   - `DB_PASSWORD`: contraseña de la base (inventa una larga; solo letras y números).
   - `JWT_SECRET`: cadena al azar de 32 caracteres o más.
   - `APP_PORT`: puerto web (80 por defecto).
   - Deja `COMPOSE_FILE=docker-compose.prod.yml` tal como viene.
3. Construye y levanta todo (la primera vez tarda de 10 a 30 minutos según la conexión):

   ```
   docker compose up -d --build
   ```
4. Abre `http://localhost` (o `http://IP-DEL-SERVIDOR`). Si cambiaste `APP_PORT`, añade el puerto.
5. Inicia sesión con el administrador semilla (`admin@sanjose.gob.pe`) y
   **cambia su contraseña**; luego crea los usuarios reales. Los usuarios de
   demostración no existen en una instalación nueva.

Las tablas, el Archivo Central y los tipos documentales iniciales se crean solos al arrancar (Flyway). No hay que ejecutar scripts.

Los siguientes arranques usan solo `docker compose up -d` (sin `--build`).

> Si te interesa construir un frontend que tienes en una carpeta local en vez del de
> GitHub, define `FRONTEND_CONTEXT=../carpeta-del-frontend` en el `.env`.

## Operación
| Tarea | Comando |
|---|---|
| Ver estado | `docker compose ps` |
| Ver registros del backend | `docker compose logs -f backend` |
| Detener (conserva datos) | `docker compose stop` |
| Volver a arrancar | `docker compose up -d` |
| Actualizar a una versión nueva | `git pull` y `docker compose up -d --build` |

**Nunca** uses `docker compose down -v` en producción: borra la base de datos y los
documentos.

## Datos y respaldos
Todo vive en volúmenes de Docker:
- `archivo_postgres_data`: base de datos.
- `archivo_documentos`: escaneos y PDF con OCR.
- `archivo_ocr_artifacts`: caché temporal del OCR (no necesita respaldo).

Respaldo de la base:

```
docker compose exec -T postgres pg_dump -U postgres -Fc archivo_sanjose > respaldo.dump
```

Respaldo de los documentos: copia el contenido del volumen `archivo_documentos`
(por ejemplo con `docker run --rm -v archivo-municipal_archivo_documentos:/d -v ${PWD}:/b alpine tar czf /b/documentos.tgz -C /d .`).
Guarda ambas copias en otro servidor.

## Seguridad mínima
- Solo el puerto web queda expuesto; la base y el OCR son internos.
- Pon el sistema detrás de HTTPS si se accede fuera de la red local.
- Cambia la contraseña del administrador y no reutilices la de desarrollo.
