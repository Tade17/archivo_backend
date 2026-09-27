# Datos de prueba (seed-demo)

Carga una municipalidad de ejemplo en el sistema para probar el flujo completo: 4 usuarios, 4 áreas (Alcaldía, Gerencia Municipal, Secretaría General, Trámite Documentario) y 4 tipos documentales (Oficio, Solicitud, Carta, Informe) y **30 expedientes con 59 documentos PDF** (informes, resoluciones, contratos, actas, licencias…). Incluye 2 escaneos simulados sin texto, útiles para probar el OCR.

Todos los nombres, DNI, RUC, placas y montos son ficticios.

## Uso

Con Postgres y el backend levantados (`docker compose up -d` y `./mvnw spring-boot:run`):

```bash
cd scripts/seed-demo
node seed-demo.mjs
```

Requiere Node 18 o superior y que el contenedor de Postgres se llame `archivo-postgres` (el de `docker-compose.yml`). Si el backend no está en `http://localhost:8080/api`, definir `API_URL`.

Es seguro repetirlo: lo que ya existe (áreas, tipos, usuarios, expedientes) se omite, no se duplica.

## Usuarios creados

Contraseña de todos: `Demo2026!`

| Correo | Rol |
|---|---|
| rosa.chero@sanjose.gob.pe | GESTOR_DOCUMENTAL |
| jorge.panta@sanjose.gob.pe | GESTOR_DOCUMENTAL |
| miriam.torres@sanjose.gob.pe | LECTOR |
| segundo.damian@sanjose.gob.pe | LECTOR |

El administrador (`admin@sanjose.gob.pe`) ya existe por la migración V2.

## Cómo funciona

Todo entra por la API real (login, `/workspace/recepcion`, `/documentos-digitales`), así que también genera auditoría y correlativos como en uso normal. Lo único que se ajusta por SQL es el estado y las fechas de cada expediente, porque la API siempre crea "Registrado" con la fecha de hoy.

| Archivo | Contenido |
|---|---|
| `data.mjs` | Usuarios, áreas, tipos y los expedientes con su documentación |
| `templates.mjs` | Plantillas de documentos administrativos |
| `pdf.mjs` | Generador de PDF sin dependencias |
| `seed-demo.mjs` | El script que lo carga todo |

## Empezar de cero

Para borrar todos los datos y volver a cargar, `docker compose down -v` elimina el volumen de la base (y con él todo lo que contenga), y luego `docker compose up -d`. No usar en un entorno con información real.
