// Carga los datos de prueba a través de la API real del sistema.
// Uso (con backend y Postgres levantados):  node seed-demo.mjs
// Es idempotente: si algo ya existe (área, tipo, usuario, ubicación, contenedor,
// expediente) lo omite. Los documentos de la demo se reparten en archivadores
// físicos (Sección -> Nivel -> Área -> Archivador), uno por oficina de origen.
import { execFileSync } from 'node:child_process';
import { AREAS, EXPEDIENTES, PASSWORD_DEMO, TIPOS, USUARIOS, areaVigente, tipoVigente } from './data.mjs';
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

// ── Ubicación física: Sección -> Nivel -> Área (estante/nivel/caja) ──
const MATRIZ = { secciones: ['1', '2'], niveles: ['1', '2', '3'], areas: ['1', '2'] };
let ubic = await call('GET', '/workspace/ubicaciones', { token: adminToken });
const archivoId = ubic.archivoCentral.id ?? (await call('POST', '/archivos-centrales', { token: adminToken, json: { nombre: 'Archivo Central Municipal' } })).id;
const huecos = []; // áreas disponibles, en orden físico
for (const sec of MATRIZ.secciones) {
  let seccion = ubic.secciones.find((x) => x.codigo === sec);
  if (!seccion) seccion = await call('POST', '/estantes', { token: adminToken, json: { archivoCentralId: archivoId, codigo: sec } });
  for (const niv of MATRIZ.niveles) {
    let nivel = ubic.niveles.find((x) => x.seccionId === seccion.id && x.codigo === niv);
    if (!nivel) nivel = await call('POST', '/niveles', { token: adminToken, json: { estanteId: seccion.id, codigo: niv } });
    for (const ar of MATRIZ.areas) {
      let area = ubic.areas.find((x) => x.nivelId === nivel.id && x.codigo === ar);
      if (!area) area = await call('POST', '/cajas', { token: adminToken, json: { nivelId: nivel.id, codigo: ar } });
      huecos.push(area.id);
    }
  }
}
console.log(`Ubicaciones: ${huecos.length} áreas (${MATRIZ.secciones.length} secciones x ${MATRIZ.niveles.length} niveles x ${MATRIZ.areas.length} áreas)`);

// Un archivador por oficina de origen, con la descripción del lomo.
const COLORES = ['negro', 'azul', 'verde', 'rojo', 'gris', 'marrón'];
const oficinas = [...new Set(EXPEDIENTES.map((e) => e.area))];
const contenedorDe = new Map();
for (const [i, oficina] of oficinas.entries()) {
  const descripcion = `Archivador ${COLORES[i % COLORES.length]} - ${oficina}`;
  const existente = items(await call('GET', `/workspace/contenedores?texto=${encodeURIComponent(descripcion)}&size=5`, { token: adminToken })).find((c) => c.descripcion === descripcion);
  const id = existente?.id ?? (await call('POST', '/workspace/contenedores', { token: adminToken, json: { cajaId: huecos[i % huecos.length], tipo: 'ARCHIVADOR', descripcion } })).id;
  contenedorDe.set(oficina, id);
}
console.log(`Contenedores: ${contenedorDe.size} archivadores`);

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
  const ficha = {
    numeroDocumento: e.numeroDocumento, remitente: e.remitente, destinatario: e.destinatario ?? null,
    areaDestinoId: areas.get(areaVigente(e.area)), tipoId: tipos.get(tipoVigente(e.tipo)), fechaDocumento: e.fecha,
    asunto: e.asunto, glosa: e.glosa, numeroFolios: e.docs.length * 2 + 1, contenedorId: contenedorDe.get(e.area),
  };
  if (yaExiste) {
    // Registrado antes de la ubicación física: se mueve a su archivador.
    if (yaExiste.contenedorId !== ficha.contenedorId && yaExiste.contenedorEstado === 'PENDIENTE') {
      await call('PUT', `/workspace/expedientes/${yaExiste.id}`, { token: adminToken, json: { ...ficha, areaDestinoId: yaExiste.areaDestinoId, tipoId: yaExiste.tipoId, glosa: yaExiste.glosa } });
      console.log('  reubicado:', e.numeroDocumento);
    } else {
      console.log('  ya existe:', e.numeroDocumento);
    }
    continue;
  }
  const exp = await call('POST', '/workspace/recepcion', { token: sesion.token, json: ficha });
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
// Los archivadores quedan completados salvo los dos últimos, que se dejan
// pendientes para mostrar cómo se retoma un contenedor a medio digitalizar.
const pendientes = new Set(oficinas.slice(-2).map((o) => contenedorDe.get(o)));
for (const id of contenedorDe.values()) {
  const c = await call('GET', `/workspace/contenedores/${id}`, { token: adminToken });
  if (!pendientes.has(id) && c.estado === 'PENDIENTE' && c.totalDocumentos > 0) {
    await call('POST', `/workspace/contenedores/${id}/completar`, { token: adminToken });
  }
}
// El contenedor provisional de la migración V9 queda vacío: se retira.
for (const c of items(await call('GET', '/workspace/contenedores?texto=antes%20de%20la%20ubicaci&estado=&size=10', { token: adminToken }))) {
  if (c.totalDocumentos === 0) await call('DELETE', `/workspace/contenedores/${c.id}`, { token: adminToken });
}

console.log(`\nListo: ${nuevos} expedientes nuevos cargados (${EXPEDIENTES.length - nuevos} ya existían).`);
console.log(`Usuarios de prueba (contraseña ${PASSWORD_DEMO}):`);
for (const u of USUARIOS) console.log(`  ${u.correo}  ${u.rol}`);
