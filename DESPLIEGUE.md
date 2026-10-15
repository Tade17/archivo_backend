# Despliegue completo con Docker

Un solo comando levanta base de datos, OCR, backend y frontend.

## Requisitos
- Docker (Docker Desktop en Windows con la virtualización activada, o Docker Engine en Linux).
- Unos 4 GB de RAM libres (el OCR es el servicio que más consume).
- Los repositorios `archivo_backend` y `archivo_frontend` clonados **uno al lado del otro**:

```
carpeta/
├── archivo-backend/
└── archivo-frontend/
```

## Primera instalación
1. En `archivo-backend`, copia `.env.example` como `.env` y complétalo:
   - `DB_PASSWORD`: contraseña de la base (inventa una larga).
   - `JWT_SECRET`: cadena al azar de 32 caracteres o más.
   - `APP_PORT`: puerto web (80 por defecto).
2. Levanta todo:

   ```
   docker compose up -d --build
   ```

   (Con `COMPOSE_FILE=docker-compose.prod.yml` en el `.env`. Si no, usa
   `docker compose -f docker-compose.prod.yml up -d --build`.)
3. La primera construcción tarda varios minutos. Después abre
   `http://localhost` (o `http://IP-DEL-SERVIDOR`).
4. Inicia sesión con el administrador semilla (`admin@sanjose.gob.pe`) y
   **cambia su contraseña** y crea los usuarios reales. Los usuarios de
   demostración no existen en una instalación nueva.

Las tablas se crean solas al arrancar (Flyway). No hay que ejecutar scripts.

## Operación
| Tarea | Comando |
|---|---|
| Ver estado | `docker compose ps` |
| Ver registros del backend | `docker compose logs -f backend` |
| Detener (conserva datos) | `docker compose stop` |
| Volver a arrancar | `docker compose up -d` |
| Actualizar a una versión nueva | `git pull` en ambos repos y `docker compose up -d --build` |

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
