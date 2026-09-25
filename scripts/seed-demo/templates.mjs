// Plantillas de documentos administrativos de una municipalidad distrital.
// Cada plantilla devuelve una lista de bloques que pdf.mjs convierte en páginas.
// Todos los nombres, DNI, placas y montos son ficticios.

const MUNI = 'MUNICIPALIDAD DISTRITAL DE SAN JOSÉ';
const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'setiembre', 'octubre', 'noviembre', 'diciembre'];

export const fecha = (iso) => {
  const [y, m, d] = iso.split('-').map(Number);
  return `${d} de ${MESES[m - 1]} de ${y}`;
};
export const soles = (n) => 'S/ ' + n.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const cabecera = (sub) => [
  { t: 'center', text: MUNI, bold: true, size: 13 },
  ...(sub ? [{ t: 'center', text: sub, size: 10 }] : []),
  { t: 'space', h: 14 },
];
const firma = (nombre, cargo) => [
  { t: 'space', h: 34 },
  { t: 'center', text: '________________________________' },
  { t: 'center', text: nombre, bold: true },
  { t: 'center', text: cargo },
];
const lista = (items) => items.map((text) => ({ t: 'li', text }));
const parrafos = (items) => items.map((text) => ({ t: 'p', text }));

const BASE = {
  constitucion: 'Que, el artículo 194 de la Constitución Política del Perú, modificado por la Ley N° 30305, establece que las municipalidades son órganos de gobierno local con autonomía política, económica y administrativa en los asuntos de su competencia.',
  loam: 'Que, la Ley N° 27972, Ley Orgánica de Municipalidades, dispone en su artículo 43 que las resoluciones de alcaldía aprueban y resuelven los asuntos de carácter administrativo.',
  tuo: 'Que, el Texto Único Ordenado de la Ley N° 27444, Ley del Procedimiento Administrativo General, aprobado por Decreto Supremo N° 004-2019-JUS, regula las actuaciones de la función administrativa del Estado.',
};

export function solicitud(f) {
  return [
    { t: 'center', text: 'SOLICITUD', bold: true, size: 15 },
    { t: 'space', h: 8 },
    { t: 'p', text: `SUMILLA: ${f.sumilla}` },
    { t: 'p', text: `SEÑOR ${f.para}:` },
    { t: 'p', text: `Yo, ${f.nombre}, identificado(a) con DNI N° ${f.dni}, con domicilio en ${f.domicilio}, ante usted me presento y expongo:` },
    ...parrafos(f.exposicion),
    { t: 'p', text: `Por lo expuesto, solicito a usted ${f.pedido}.` },
    { t: 'h', text: 'Documentos que adjunto:' },
    ...lista(f.anexos),
    { t: 'space' },
    { t: 'p', text: `San José, ${fecha(f.fecha)}.` },
    ...firma(f.nombre, `DNI N° ${f.dni}`),
  ];
}

export function informe(f) {
  return [
    ...cabecera(f.area),
    { t: 'center', text: `${f.titulo ?? 'INFORME'} N° ${f.num}`, bold: true, size: 13 },
    { t: 'space', h: 10 },
    { t: 'kv', key: 'A', value: f.a },
    { t: 'kv', key: 'DE', value: f.de },
    { t: 'kv', key: 'ASUNTO', value: f.asunto },
    ...(f.ref ? [{ t: 'kv', key: 'REFERENCIA', value: f.ref }] : []),
    { t: 'kv', key: 'FECHA', value: `San José, ${fecha(f.fecha)}` },
    { t: 'h', text: 'I. ANTECEDENTES' },
    ...parrafos(f.antecedentes),
    { t: 'h', text: 'II. ANÁLISIS' },
    ...parrafos(f.analisis),
    { t: 'h', text: 'III. CONCLUSIONES' },
    ...lista(f.conclusiones),
    { t: 'h', text: 'IV. RECOMENDACIONES' },
    ...lista(f.recomendaciones),
    { t: 'p', text: 'Es todo cuanto informo a usted para su conocimiento y fines pertinentes.' },
    { t: 'p', text: 'Atentamente,' },
    ...firma(f.firmante, f.cargo),
  ];
}

export function resolucion(f) {
  return [
    ...cabecera(),
    { t: 'center', text: `${f.tipo ?? 'RESOLUCIÓN DE ALCALDÍA'} N° ${f.num}`, bold: true, size: 13 },
    { t: 'space', h: 6 },
    { t: 'p', text: `San José, ${fecha(f.fecha)}` },
    { t: 'h', text: 'VISTO:' },
    ...parrafos(f.visto),
    { t: 'h', text: 'CONSIDERANDO:' },
    ...parrafos([BASE.constitucion, ...(f.baseLegal ?? [BASE.loam, BASE.tuo]), ...f.considerandos]),
    { t: 'p', text: 'Estando a lo expuesto y en uso de las facultades conferidas por la Ley N° 27972, Ley Orgánica de Municipalidades;' },
    { t: 'h', text: 'SE RESUELVE:' },
    ...f.articulos.map((text, i) => ({ t: 'p', text: `ARTÍCULO ${['PRIMERO', 'SEGUNDO', 'TERCERO', 'CUARTO', 'QUINTO'][i]}.- ${text}` })),
    { t: 'p', text: 'REGÍSTRESE, COMUNÍQUESE Y CÚMPLASE.' },
    ...firma(f.firmante, f.cargo),
  ];
}

export function oficio(f) {
  return [
    ...cabecera(f.area),
    { t: 'p', text: `San José, ${fecha(f.fecha)}` },
    { t: 'center', text: `OFICIO N° ${f.num}`, bold: true, size: 13 },
    { t: 'space', h: 8 },
    { t: 'p', text: `Señor(a): ${f.destinatario}` },
    { t: 'p', text: f.cargoDestinatario },
    { t: 'p', text: `Presente.-` },
    { t: 'kv', key: 'ASUNTO', value: f.asunto },
    ...(f.ref ? [{ t: 'kv', key: 'REFERENCIA', value: f.ref }] : []),
    { t: 'space' },
    { t: 'p', text: 'Es grato dirigirme a usted para expresarle mi cordial saludo y, a la vez, manifestarle lo siguiente:' },
    ...parrafos(f.cuerpo),
    { t: 'p', text: 'Sin otro particular, hago propicia la oportunidad para reiterarle las muestras de mi consideración.' },
    { t: 'p', text: 'Atentamente,' },
    ...firma(f.firmante, f.cargo),
  ];
}

export function carta(f) {
  return [
    { t: 'p', text: `San José, ${fecha(f.fecha)}` },
    { t: 'center', text: f.num ? `CARTA N° ${f.num}` : 'CARTA', bold: true, size: 13 },
    { t: 'space', h: 8 },
    { t: 'p', text: `Señores: ${f.destinatario}` },
    { t: 'p', text: 'Presente.-' },
    { t: 'kv', key: 'ASUNTO', value: f.asunto },
    { t: 'space' },
    { t: 'p', text: 'De mi consideración:' },
    ...parrafos(f.cuerpo),
    { t: 'p', text: 'Sin otro particular, quedo de usted.' },
    { t: 'p', text: 'Atentamente,' },
    ...firma(f.firmante, f.cargo),
  ];
}

export function memorando(f) {
  return [
    ...cabecera(f.area),
    { t: 'center', text: `MEMORANDO N° ${f.num}`, bold: true, size: 13 },
    { t: 'space', h: 10 },
    { t: 'kv', key: 'A', value: f.a },
    { t: 'kv', key: 'DE', value: f.de },
    { t: 'kv', key: 'ASUNTO', value: f.asunto },
    { t: 'kv', key: 'FECHA', value: `San José, ${fecha(f.fecha)}` },
    { t: 'space' },
    ...parrafos(f.cuerpo),
    { t: 'p', text: 'Atentamente,' },
    ...firma(f.firmante, f.cargo),
  ];
}

export function contrato(f) {
  return [
    ...cabecera(),
    { t: 'center', text: f.titulo, bold: true, size: 13 },
    { t: 'center', text: f.numero, size: 10 },
    { t: 'space', h: 10 },
    { t: 'p', text: f.partes },
    ...f.clausulas.flatMap(([titulo, texto]) => [{ t: 'h', text: titulo }, { t: 'p', text: texto }]),
    { t: 'p', text: `En señal de conformidad, las partes suscriben el presente documento en dos ejemplares de igual valor, en San José, a los ${fecha(f.fecha)}.` },
    { t: 'space', h: 20 },
    { t: 'center', text: '____________________________          ____________________________' },
    { t: 'center', text: f.firmaEntidad + '          ' + f.firmaContratista, bold: true },
    { t: 'center', text: 'LA ENTIDAD                                      EL CONTRATISTA' },
  ];
}

export function acta(f) {
  return [
    ...cabecera(f.area),
    { t: 'center', text: f.titulo, bold: true, size: 13 },
    { t: 'space', h: 10 },
    { t: 'p', text: `En la ciudad de San José, siendo las ${f.hora} horas del día ${fecha(f.fecha)}, en ${f.lugar}, se reunieron los siguientes miembros:` },
    ...lista(f.asistentes),
    { t: 'h', text: 'AGENDA' },
    ...lista(f.agenda),
    { t: 'h', text: 'DESARROLLO DE LA SESIÓN' },
    ...parrafos(f.desarrollo),
    { t: 'h', text: 'ACUERDOS' },
    ...f.acuerdos.map((text, i) => ({ t: 'p', text: `Acuerdo ${i + 1}: ${text}` })),
    { t: 'p', text: `No habiendo otro asunto que tratar, se levantó la sesión siendo las ${f.horaFin} horas, firmando los presentes en señal de conformidad.` },
    ...firma(f.firmante, f.cargo),
  ];
}

export function ordenanza(f) {
  return [
    ...cabecera(),
    { t: 'center', text: `ORDENANZA MUNICIPAL N° ${f.num}`, bold: true, size: 13 },
    { t: 'space', h: 6 },
    { t: 'p', text: `EL ALCALDE DE LA MUNICIPALIDAD DISTRITAL DE SAN JOSÉ` },
    { t: 'p', text: `POR CUANTO: El Concejo Municipal, en Sesión Ordinaria de fecha ${fecha(f.fecha)}, ha aprobado la siguiente Ordenanza:` },
    { t: 'h', text: 'CONSIDERANDO:' },
    ...parrafos([BASE.constitucion, 'Que, el artículo 40 de la Ley N° 27972, Ley Orgánica de Municipalidades, establece que las ordenanzas de las municipalidades son las normas de carácter general de mayor jerarquía en la estructura normativa municipal, por medio de las cuales se aprueba la organización interna, la regulación, administración y supervisión de los servicios públicos y las materias en las que la municipalidad tiene competencia normativa.', ...f.considerandos]),
    { t: 'h', text: f.titulo },
    ...f.articulos.map((text, i) => ({ t: 'p', text: `Artículo ${i + 1}.- ${text}` })),
    { t: 'p', text: 'POR TANTO: Mando se registre, publique y cumpla.' },
    ...firma(f.firmante, f.cargo),
  ];
}

export function certificado(f) {
  return [
    ...cabecera(f.area),
    { t: 'center', text: f.titulo, bold: true, size: 14 },
    { t: 'center', text: `N° ${f.num}`, size: 11 },
    { t: 'space', h: 12 },
    ...parrafos(f.cuerpo),
    ...(f.datos ? f.datos.map(([key, value]) => ({ t: 'kv', key, value })) : []),
    { t: 'space' },
    { t: 'p', text: `Se expide el presente a solicitud del interesado, en San José, a los ${fecha(f.fecha)}.` },
    ...firma(f.firmante, f.cargo),
  ];
}

export function orden(f) {
  const filas = f.items.map(([desc, cant, unidad, precio]) => ({ t: 'li', text: `${desc} - ${cant} ${unidad} x ${soles(precio)} = ${soles(cant * precio)}` }));
  const total = f.items.reduce((s, [, c, , p]) => s + c * p, 0);
  return [
    ...cabecera(f.area),
    { t: 'center', text: `ORDEN DE COMPRA N° ${f.num}`, bold: true, size: 13 },
    { t: 'space', h: 8 },
    { t: 'kv', key: 'PROVEEDOR', value: f.proveedor },
    { t: 'kv', key: 'RUC', value: f.ruc },
    { t: 'kv', key: 'FECHA', value: `San José, ${fecha(f.fecha)}` },
    { t: 'kv', key: 'FUENTE', value: 'Recursos Directamente Recaudados' },
    { t: 'h', text: 'DETALLE DE LA ADQUISICIÓN' },
    ...filas,
    { t: 'p', text: `MONTO TOTAL: ${soles(total)} (incluye IGV)` },
    { t: 'p', text: 'Plazo de entrega: 07 días calendario contados desde la notificación de la presente orden. Lugar de entrega: almacén de la Sub Gerencia de Logística y Control Patrimonial.' },
    ...firma(f.firmante, f.cargo),
  ];
}
