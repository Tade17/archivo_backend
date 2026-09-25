// Carga los datos de prueba a través de la API real del sistema.
// Uso (con backend y Postgres levantados):  node seed-demo.mjs
// Es idempotente: si algo ya existe (área, tipo, usuario, expediente) lo omite.
import { execFileSync } from 'node:child_process';
import { AREAS, EXPEDIENTES, PASSWORD_DEMO, TIPOS, USUARIOS } from './data.mjs';
import { scanPdf, textPdf } from './pdf.mjs';

const API = process.env.API_URL ?? 'http://localhost:8080/api';
const ADMIN = { correo: 'admin@sanjose.gob.pe', password: 'CambiarInmediatamente123!' };
const CONTAINER = 'archivo-postgres';

async function call(method, path, { token, json, form } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (json) headers['Content-Type'] = 'application/json';
  const res = await fetch(API + path, { method, headers, body: json ? JSON.stringify(json) : form });
  const text = await res.text();
  const body = text ? JSON.parse(text) : null;
  if (!res.ok) throw Object.assign(new Error(`${method} ${path} -> ${res.status} ${text.slice(0, 200)}`), { status: res.status });
  return body;
}
const items = (page) => (Array.isArray(page) ? page : page?.contenido ?? []);
const login = (correo, password) => call('POST', '/auth/login', { json: { correo, password } });

const sql = (statement) =>
  execFileSync('docker', ['exec', CONTAINER, 'psql', '-U', 'postgres', '-d', 'archivo_sanjose', '-v', 'ON_ERROR_STOP=1', '-c', statement], { stdio: ['ignore', 'pipe', 'inherit'] });

const admin = await login(ADMIN.correo, ADMIN.password);
const adminToken = admin.token;
console.log('Sesión de administrador: OK');

// ── Catálogos ───────────────────────────────────────────────────
const areas = new Map(items(await call('GET', '/areas-responsables?size=200', { token: adminToken })).map((a) => [a.nombre, a.id]));
for (const [nombre, codigo] of AREAS) {
  if (areas.has(nombre)) continue;
  const creada = await call('POST', '/areas-responsables', { token: adminToken, json: { nombre, codigo } });
  areas.set(nombre, creada.id);
  console.log('  área creada:', nombre);
}
const tipos = new Map(items(await call('GET', '/tipos-documentales?size=200', { token: adminToken })).map((t) => [t.nombre, t.id]));
for (const nombre of TIPOS) {
  if (tipos.has(nombre)) continue;
  const creado = await call('POST', '/tipos-documentales', { token: adminToken, json: { nombre } });
  tipos.set(nombre, creado.id);
  console.log('  tipo creado:', nombre);
}

// ── Usuarios ────────────────────────────────────────────────────
const roles = new Map(items(await call('GET', '/roles?size=50', { token: adminToken })).map((r) => [r.nombre, r.id]));
const existentes = new Set(items(await call('GET', '/usuarios?size=200', { token: adminToken })).map((u) => u.correo));
for (const u of USUARIOS) {
  if (existentes.has(u.correo)) continue;
  await call('POST', '/usuarios', { token: adminToken, json: { nombre: u.nombre, correo: u.correo, password: PASSWORD_DEMO, rolId: roles.get(u.rol), activo: true } });
  console.log('  usuario creado:', u.correo, `(${u.rol})`);
}
const sesiones = new Map();
for (const u of USUARIOS) sesiones.set(u.clave, await login(u.correo, PASSWORD_DEMO));

// ── Expedientes y documentos ────────────────────────────────────
const creados = [];
let nuevos = 0;
for (const e of EXPEDIENTES) {
  const sesion = sesiones.get(e.autor);
  const yaExiste = items(await call('GET', `/workspace/buscar?texto=${encodeURIComponent(e.numeroDocumento)}&size=10`, { token: sesion.token })).find((x) => x.numeroDocumento === e.numeroDocumento);
  if (yaExiste) {
    console.log('  ya existe:', e.numeroDocumento);
    continue;
  }
  const exp = await call('POST', '/workspace/recepcion', {
    token: sesion.token,
    json: { numeroDocumento: e.numeroDocumento, remitente: e.remitente, areaDestinoId: areas.get(e.area), tipoId: tipos.get(e.tipo), fechaDocumento: e.fecha, asunto: e.asunto, glosa: e.glosa },
  });
  const archivos = new FormData();
  archivos.append('expedienteId', exp.id);
  archivos.append('tecnicoResponsableId', sesion.usuarioId);
  archivos.append('resolucionDpi', '300');
  for (const d of e.docs) {
    const bytes = d.escaneo ? scanPdf(d.escaneo, d.seed) : textPdf(d.bloques, exp.codigoUnico);
    archivos.append('archivos', new Blob([bytes], { type: 'application/pdf' }), d.nombre);
    if (d.escaneo) archivos.set('escanerUtilizado', 'Escáner de cama plana Epson DS-1630');
  }
  await call('POST', '/documentos-digitales', { token: sesion.token, form: archivos });
  creados.push({ id: exp.id, ...e });
  nuevos++;
  console.log(`  ${exp.codigoUnico}  ${e.numeroDocumento}  (${e.docs.length} doc.)  ${e.asunto.slice(0, 60)}`);
}

// ── Estado y fechas realistas (la API siempre crea "Registrado" y "hoy") ──────────
const UUID = /^[0-9a-f-]{36}$/i;
const ESTADOS = new Set(['Registrado', 'En trámite', 'Activo', 'Archivado']);
for (const e of creados) {
  if (!UUID.test(e.id) || !ESTADOS.has(e.estado) || !/^\d{4}-\d{2}-\d{2}$/.test(e.fecha)) throw new Error('Dato inesperado: ' + e.numeroDocumento);
  sql(`UPDATE expediente SET estado_id=(SELECT estado_id FROM estado_expediente WHERE nombre='${e.estado}'), fecha_registro='${e.fecha} 09:15:00'::timestamp + interval '1 day', fecha_actualizacion='${e.fecha} 09:15:00'::timestamp + interval '1 day' WHERE expediente_id='${e.id}'`);
  sql(`UPDATE documento_digital SET fecha_digitalizacion='${e.fecha} 11:40:00'::timestamp + interval '2 days' WHERE expediente_id='${e.id}'`);
}
console.log(`\nListo: ${nuevos} expedientes nuevos cargados (${EXPEDIENTES.length - nuevos} ya existían).`);
console.log(`Usuarios de prueba (contraseña ${PASSWORD_DEMO}):`);
for (const u of USUARIOS) console.log(`  ${u.correo}  ${u.rol}`);
