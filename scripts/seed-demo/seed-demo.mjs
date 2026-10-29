// Carga los datos de prueba a través de la API real del sistema.
// Uso (con backend y Postgres levantados):  node seed-demo.mjs
// Es idempotente: si algo ya existe (área, tipo, usuario, ubicación, contenedor,
// expediente, préstamos, actividad de ejemplo) lo omite.
// Variables opcionales: API_URL, ADMIN_PASSWORD (si cambió la clave del admin), PG_CONTAINER. Los documentos de la demo se reparten en archivadores
// físicos (Sección -> Nivel -> Área -> Archivador), uno por oficina de origen.
import { execFileSync } from 'node:child_process';
import { AREAS, EXPEDIENTES, PASSWORD_DEMO, TIPOS, USUARIOS, areaVigente, tipoVigente } from './data.mjs';
import { scanPdf, textPdf } from './pdf.mjs';

const API = process.env.API_URL ?? 'http://localhost:8080/api';
const ADMIN = { correo: 'admin@sanjose.gob.pe', password: process.env.ADMIN_PASSWORD ?? 'CambiarInmediatamente123!' };
const CONTAINER = process.env.PG_CONTAINER ?? 'archivo-postgres';

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

// Las fechas relativas ("hace 12 días") se calculan en hora de Lima, igual que la aplicación.
const sqlLima = (statement) => sql(`SET TIME ZONE 'America/Lima'; ${statement}`);

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
// Una cuenta suspendida de ejemplo (para el reporte de usuarios).
const SUSPENDIDO = { nombre: 'Carlos Eduardo Vega Alama', correo: 'carlos.vega@sanjose.gob.pe', rol: 'GESTOR_DOCUMENTAL' };
if (!existentes.has(SUSPENDIDO.correo)) {
  await call('POST', '/usuarios', { token: adminToken, json: { nombre: SUSPENDIDO.nombre, correo: SUSPENDIDO.correo, password: PASSWORD_DEMO, rolId: roles.get(SUSPENDIDO.rol), activo: false } });
  console.log('  usuario suspendido creado:', SUSPENDIDO.correo);
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

// ── Fechas realistas (la API siempre crea el documento con la fecha de "hoy") ──────
// El estado del documento es único ("Registrado"): lo pone el sistema al recibirlo.
const UUID = /^[0-9a-f-]{36}$/i;
for (const e of creados) {
  if (!UUID.test(e.id) || !/^\d{4}-\d{2}-\d{2}$/.test(e.fecha)) throw new Error('Dato inesperado: ' + e.numeroDocumento);
  sql(`UPDATE expediente SET fecha_registro='${e.fecha} 09:15:00'::timestamp + interval '1 day', fecha_actualizacion='${e.fecha} 09:15:00'::timestamp + interval '1 day' WHERE expediente_id='${e.id}'`);
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


// ── Préstamos de ejemplo (semáforo y reportes de préstamos) ─────────────────
const rosa = sesiones.get('rosa');
const totalPrestamos = (await call('GET', '/workspace/prestamos?estado=TODOS&size=1', { token: rosa.token })).totalElementos;
if (totalPrestamos > 0) {
  console.log(`
Préstamos: ya hay ${totalPrestamos}, no se agregan.`);
} else {
  const idDe = async (n) => items(await call('GET', `/workspace/buscar?texto=${encodeURIComponent(EXPEDIENTES[n].numeroDocumento)}&size=5`, { token: rosa.token })).find((x) => x.numeroDocumento === EXPEDIENTES[n].numeroDocumento).id;
  const prestar = (json) => call('POST', '/workspace/prestamos', { token: rosa.token, json });
  // salioHace/venceEn/devueltoHace se ajustan por SQL: la API siempre registra "hoy" + los días pedidos.
  const PRESTAMOS = [
    { exp: 0, solicitante: 'Contraloría General de la República', dias: 7, salioHace: 12, venceEn: -4 },
    { exp: 1, solicitante: 'Poder Judicial - Juzgado Civil de Sullana', dias: 5, salioHace: 3, venceEn: 2 },
    { exp: 2, solicitante: 'Defensoría del Pueblo - Oficina de Piura', dias: 10 },
    { exp: 3, solicitante: 'Contraloría General de la República', dias: 3, salioHace: 40, venceEn: -35, devueltoHace: 30 },
    { exp: 4, solicitante: 'Fiscalía Provincial de Sullana', dias: 6, salioHace: 7, venceEn: -1 },
  ];
  let hechos = 0;
  for (const p of PRESTAMOS) {
    const creado = await prestar({ expedienteId: await idDe(p.exp), solicitante: p.solicitante, dias: p.dias, observaciones: 'Préstamo de demostración' });
    if (p.devueltoHace) await call('POST', `/workspace/prestamos/${creado.id}/devolver`, { token: rosa.token });
    if (!UUID.test(creado.id)) throw new Error('Dato inesperado en préstamo');
    const sets = [];
    if (p.salioHace) sets.push(`fecha_solicitud = now() - interval '${p.salioHace} days'`);
    if (p.venceEn !== undefined) sets.push(`fecha_devolucion_prevista = current_date + (${p.venceEn})`);
    if (p.devueltoHace) sets.push(`fecha_devolucion_real = now() - interval '${p.devueltoHace} days'`);
    if (sets.length) sqlLima(`UPDATE prestamo SET ${sets.join(', ')} WHERE prestamo_id = '${creado.id}'`);
    hechos++;
  }
  // Un archivador completo (el de una oficina cuyos documentos no están prestados).
  try {
    const usados = new Set(PRESTAMOS.map((p) => EXPEDIENTES[p.exp].area));
    const libre = oficinas.find((o) => !usados.has(o) && !pendientes.has(contenedorDe.get(o)));
    await prestar({ contenedorId: contenedorDe.get(libre), solicitante: 'Fiscalía Provincial de Sullana', dias: 15, observaciones: 'Archivador completo, demostración' });
    hechos++;
  } catch (e) {
    console.log('  (no se pudo prestar un archivador de ejemplo:', e.message.slice(0, 80) + ')');
  }
  console.log(`
Préstamos de ejemplo: ${hechos} (vencidos, por vencer, en plazo y devuelto).`);
}

// ── Actividad de ejemplo: consultas y descargas de los lectores ────────────
const consultasLector = (await call('GET', '/workspace/reportes/descargas-consultas?texto=miriam', { token: adminToken })).filas.length;
if (consultasLector === 0) {
  const docs = items(await call('GET', '/documentos-digitales?size=6', { token: adminToken }));
  const toca = (token, ruta) => fetch(API + ruta, { headers: { Authorization: `Bearer ${token}` } }).then((r) => r.arrayBuffer());
  for (const [i, d] of docs.entries()) {
    await toca(sesiones.get('miriam').token, `/workspace/documentos/${d.id}/vista`);
    if (i % 2 === 0) await toca(sesiones.get('segundo').token, `/workspace/documentos/${d.id}/vista`);
    if (i < 3) await toca(sesiones.get('miriam').token, `/documentos-digitales/${d.id}/archivo`);
  }
  console.log(`Actividad de ejemplo: ${docs.length} documentos consultados/descargados por los lectores.`);
}

// ── Dos eliminaciones de ejemplo (reporte "Eliminaciones realizadas") ──────
const yaEliminados = (await call('GET', '/workspace/reportes/eliminaciones?texto=DEMO-ELIM', { token: adminToken })).filas.length;
if (yaEliminados === 0) {
  const destino = [...pendientes][0];
  for (const n of [1, 2]) {
    const exp = await call('POST', '/workspace/recepcion', {
      token: rosa.token,
      json: {
        numeroDocumento: `DEMO-ELIM-0${n}`, remitente: 'Registro de demostración', areaDestinoId: areas.get(AREAS[0][0]), tipoId: tipos.get(TIPOS[0]),
        fechaDocumento: new Date().toISOString().slice(0, 10), asunto: `DEMO-ELIM ${n} · Registro duplicado de demostración`, glosa: 'Se crea y se elimina para ilustrar el reporte.',
        numeroFolios: 1, contenedorId: destino,
      },
    });
    await call('DELETE', `/expedientes/${exp.id}`, { token: adminToken });
  }
  console.log('Eliminaciones de ejemplo: 2 (quedan en la auditoría con su nombre).');
}

console.log(`\nListo: ${nuevos} expedientes nuevos cargados (${EXPEDIENTES.length - nuevos} ya existían).`);
console.log(`Usuarios de prueba (contraseña ${PASSWORD_DEMO}):`);
for (const u of USUARIOS) console.log(`  ${u.correo}  ${u.rol}`);
console.log(`  ${SUSPENDIDO.correo}  ${SUSPENDIDO.rol} (suspendida)`);
