// Datos de prueba de una municipalidad distrital. Nombres, DNI, RUC, placas y montos: ficticios.
import { solicitud, informe, resolucion, oficio, carta, memorando, contrato, acta, ordenanza, certificado, orden, soles } from './templates.mjs';

export const PASSWORD_DEMO = 'Demo2026!';

export const USUARIOS = [
  { clave: 'rosa', nombre: 'Rosa Milagros Chero Santamaría', correo: 'rosa.chero@sanjose.gob.pe', rol: 'GESTOR_DOCUMENTAL' },
  { clave: 'jorge', nombre: 'Jorge Luis Panta Reque', correo: 'jorge.panta@sanjose.gob.pe', rol: 'GESTOR_DOCUMENTAL' },
  { clave: 'miriam', nombre: 'Miriam Yesenia Torres Llontop', correo: 'miriam.torres@sanjose.gob.pe', rol: 'LECTOR' },
  { clave: 'segundo', nombre: 'Segundo Anselmo Damián Cajusol', correo: 'segundo.damian@sanjose.gob.pe', rol: 'LECTOR' },
];

// Catálogos vigentes del sistema: solo 4 áreas y 4 tipos documentales.
export const AREAS = [
  ['Alcaldía', 'ALC'],
  ['Gerencia Municipal', 'GM'],
  ['Secretaría General', 'SG'],
  ['Unidad de Trámite Documentario y Archivo', 'TDA'],
];

export const TIPOS = ['Oficio', 'Solicitud', 'Carta', 'Informe'];

// Los expedientes de abajo conservan su área/tipo "de origen" (más descriptivo);
// al sembrar se traducen a los catálogos vigentes con estos mapas.
const AREA_A_VIGENTE = {
  'Alcaldía': 'Alcaldía', 'Oficina de Planeamiento y Presupuesto': 'Alcaldía',
  'Órgano de Control Institucional': 'Alcaldía', 'Oficina de Asesoría Jurídica': 'Alcaldía',
  'Secretaría General': 'Secretaría General', 'Registro Civil': 'Secretaría General',
  'Defensoría Municipal del Niño y del Adolescente': 'Secretaría General',
  'Oficina Municipal de Atención a las Personas con Discapacidad': 'Secretaría General',
  'Gerencia de Desarrollo Social': 'Secretaría General',
  'Unidad de Trámite Documentario y Archivo': 'Unidad de Trámite Documentario y Archivo',
};
export const areaVigente = (nombre) => AREA_A_VIGENTE[nombre] ?? 'Gerencia Municipal';

const TIPO_A_VIGENTE = {
  'Licencia de Funcionamiento': 'Solicitud', 'Licencia de Edificación': 'Solicitud', 'Certificado': 'Solicitud', 'Constancia': 'Solicitud',
  'Informe Técnico': 'Informe', 'Acta': 'Informe',
  'Contrato': 'Carta', 'Orden de Compra': 'Carta',
};
export const tipoVigente = (nombre) => TIPO_A_VIGENTE[nombre] ?? 'Oficio';

// Autoridades ficticias.
const P = {
  alcalde: ['Ing. Roberto Carlos Santamaría Guevara', 'Alcalde'],
  gm: ['Abog. Luis Fernando Ordinola Neciosup', 'Gerente Municipal'],
  gaf: ['CPC Lidia Marisol Reque Carrasco', 'Gerente de Administración y Finanzas'],
  gdur: ['Arq. Wilmer Orlando Chapoñán Díaz', 'Gerente de Desarrollo Urbano y Rural'],
  sgopc: ['Arq. Katherine Milagros Vásquez Puicón', 'Sub Gerente de Obras Privadas y Catastro'],
  sgop: ['Ing. Hugo César Bances Zapata', 'Sub Gerente de Obras Públicas'],
  sgdel: ['Lic. Gloria Elizabeth Monteza Ruiz', 'Sub Gerente de Desarrollo Económico y Licencias'],
  gatr: ['CPC Julio César Rojas Farro', 'Gerente de Administración Tributaria y Rentas'],
  sg: ['Abog. Nélida Estefany Cruz Tenorio', 'Secretaria General'],
  oaj: ['Abog. Manuel Ernesto Gonzales Pisfil', 'Jefe de la Oficina de Asesoría Jurídica'],
  opp: ['Econ. Sandra Patricia Ipanaqué Lamas', 'Jefa de la Oficina de Planeamiento y Presupuesto'],
  sglcp: ['Lic. Pedro Pablo Seclén Cubas', 'Sub Gerente de Logística y Control Patrimonial'],
  sgt: ['CPC Ana Lucía Puse Guerrero', 'Sub Gerente de Tesorería'],
  sgrh: ['Lic. Marco Antonio Sánchez Delgado', 'Sub Gerente de Recursos Humanos'],
  sgggrd: ['Ing. Juan Carlos Uypan Bravo', 'Sub Gerente de Gestión de Riesgos y Defensa Civil'],
  demuna: ['Abog. Cecilia Valdera Ruiz', 'Defensora Municipal del Niño y del Adolescente'],
  omaped: ['Lic. Rosario del Pilar Gil Estela', 'Responsable de la OMAPED'],
  gspma: ['Ing. Jaime Alberto Coronel Pérez', 'Gerente de Servicios Públicos y Medio Ambiente'],
  gds: ['Lic. Mirtha Isabel Chinchay Núñez', 'Gerente de Desarrollo Social'],
  rc: ['Sra. Teresa de Jesús Barturén Coronado', 'Registradora Civil'],
  oci: ['CPC Segundo Anselmo Damián Cajusol', 'Jefe del Órgano de Control Institucional'],
};
const f = (p) => ({ firmante: p[0], cargo: p[1] });
const pdf = (nombre, bloques) => ({ nombre: `${nombre}.pdf`, bloques });
const scan = (nombre, paginas, seed) => ({ nombre: `${nombre}.pdf`, escaneo: paginas, seed });

export const EXPEDIENTES = [
  // 1 ─────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'SOL-0412-2026', area: 'Sub Gerencia de Desarrollo Económico y Licencias', tipo: 'Licencia de Funcionamiento',
    remitente: 'Juana Marleny Fernández Sánchez', fecha: '2026-02-09', estado: 'Archivado', autor: 'rosa',
    asunto: "Licencia de funcionamiento para bodega 'Santa Rosa'",
    glosa: 'Persona natural solicita licencia de funcionamiento (giro: comercio de abarrotes) para un establecimiento de 28 m2 en Av. Los Pescadores N° 214. Nivel de riesgo bajo.',
    docs: [
      pdf('Solicitud de licencia de funcionamiento', solicitud({
        sumilla: 'Licencia de funcionamiento - bodega', para: 'SUB GERENTE DE DESARROLLO ECONÓMICO Y LICENCIAS DE LA MUNICIPALIDAD DISTRITAL DE SAN JOSÉ',
        nombre: 'Juana Marleny Fernández Sánchez', dni: '70412518', domicilio: 'Av. Los Pescadores N° 214, distrito de San José', fecha: '2026-02-09',
        exposicion: ['Que, de conformidad con la Ley N° 28976, Ley Marco de Licencia de Funcionamiento, deseo desarrollar la actividad comercial de venta de abarrotes al por menor en el local ubicado en Av. Los Pescadores N° 214, el cual cuenta con un área de 28 m2 y es de mi propiedad.', 'Que, el establecimiento no requiere inspección técnica de seguridad en edificaciones previa por ser de riesgo bajo, y se cuenta con la zonificación compatible con el giro solicitado.'],
        pedido: 'se sirva otorgarme la licencia de funcionamiento para la bodega denominada "Santa Rosa", con giro de comercio de abarrotes',
        anexos: ['Copia del DNI del solicitante', 'Declaración jurada de cumplimiento de condiciones de seguridad', 'Copia de la ficha RUC N° 10704125181', 'Recibo de pago por derecho de trámite N° 004871'],
      })),
      pdf('Informe de inspección del establecimiento', informe({
        num: '021-2026-SGDEL/MDSJ', area: 'Sub Gerencia de Desarrollo Económico y Licencias', a: 'Gerencia de Desarrollo Urbano y Rural', de: 'Sub Gerencia de Desarrollo Económico y Licencias',
        asunto: 'Verificación de condiciones para licencia de funcionamiento - Bodega Santa Rosa', ref: 'Solicitud SOL-0412-2026', fecha: '2026-02-16',
        antecedentes: ['Mediante solicitud de fecha 9 de febrero de 2026, la señora Juana Marleny Fernández Sánchez solicita licencia de funcionamiento para una bodega ubicada en Av. Los Pescadores N° 214.'],
        analisis: ['El día 13 de febrero de 2026 se realizó la inspección ocular al establecimiento. Se verificó que el local cuenta con área de 28 m2, extintor vigente, señalización de evacuación y botiquín de primeros auxilios.', 'El giro solicitado, comercio de abarrotes, es compatible con la zonificación Residencial de Densidad Media (RDM) según el índice de usos vigente. El nivel de riesgo determinado es BAJO, por lo que corresponde una ITSE posterior.'],
        conclusiones: ['El establecimiento cumple con las condiciones exigidas por la Ley N° 28976.', 'Corresponde otorgar la licencia de funcionamiento solicitada.'],
        recomendaciones: ['Emitir la licencia de funcionamiento con vigencia indeterminada.', 'Programar la fiscalización posterior dentro de los doce meses siguientes.'], ...f(P.sgdel),
      })),
      pdf('Licencia de funcionamiento N° 0058-2026', certificado({
        titulo: 'LICENCIA DE FUNCIONAMIENTO', num: '0058-2026', area: 'Sub Gerencia de Desarrollo Económico y Licencias', fecha: '2026-02-18',
        cuerpo: ['La Sub Gerencia de Desarrollo Económico y Licencias de la Municipalidad Distrital de San José, en cumplimiento de la Ley N° 28976, otorga la presente licencia de funcionamiento con vigencia indeterminada.'],
        datos: [['TITULAR', 'Juana Marleny Fernández Sánchez'], ['NOMBRE COMERCIAL', 'Bodega Santa Rosa'], ['GIRO', 'Comercio de abarrotes al por menor'], ['UBICACIÓN', 'Av. Los Pescadores N° 214'], ['ÁREA', '28 m2'], ['NIVEL DE RIESGO', 'Bajo']], ...f(P.sgdel),
      })),
    ],
  },
  // 2 ─────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'EDIF-2026-017', area: 'Sub Gerencia de Obras Privadas y Catastro', tipo: 'Licencia de Edificación',
    remitente: 'Percy Alexander Llontop Baldera', fecha: '2026-03-16', estado: 'Activo', autor: 'jorge',
    asunto: 'Licencia de edificación para vivienda unifamiliar de dos pisos, Mz. C Lote 12 Urb. Las Brisas',
    glosa: 'Licencia de edificación en la modalidad B para vivienda unifamiliar de dos niveles, área techada de 142.60 m2. Incluye verificación administrativa y técnica.',
    docs: [
      pdf('Solicitud de licencia de edificación', solicitud({
        sumilla: 'Licencia de edificación - modalidad B', para: 'SUB GERENTE DE OBRAS PRIVADAS Y CATASTRO DE LA MUNICIPALIDAD DISTRITAL DE SAN JOSÉ',
        nombre: 'Percy Alexander Llontop Baldera', dni: '41873256', domicilio: 'Calle Las Gaviotas N° 118, Urb. Las Brisas', fecha: '2026-03-16',
        exposicion: ['Que, al amparo de la Ley N° 29090, Ley de Regulación de Habilitaciones Urbanas y de Edificaciones, y su Reglamento de Licencias, soy propietario del predio ubicado en Mz. C Lote 12 de la Urbanización Las Brisas, con un área de 160.00 m2.', 'Que, proyecto construir una vivienda unifamiliar de dos pisos con un área techada total de 142.60 m2, conforme a los planos y memoria descriptiva elaborados por profesionales colegiados.'],
        pedido: 'se sirva otorgar la licencia de edificación en la modalidad B para la construcción de vivienda unifamiliar',
        anexos: ['Formulario Único de Edificación (FUE) debidamente suscrito', 'Copia literal de dominio con antigüedad no mayor a 30 días', 'Planos de arquitectura, estructuras e instalaciones (juego completo)', 'Memoria descriptiva y cálculos estructurales', 'Póliza CAR', 'Comprobante de pago por derecho de trámite'],
      })),
      pdf('Informe técnico de verificación', informe({
        titulo: 'INFORME TÉCNICO', num: '034-2026-SGOPC/GDUR/MDSJ', area: 'Sub Gerencia de Obras Privadas y Catastro', a: 'Gerencia de Desarrollo Urbano y Rural', de: 'Sub Gerencia de Obras Privadas y Catastro',
        asunto: 'Verificación administrativa y técnica del expediente EDIF-2026-017', ref: 'Solicitud de licencia de edificación del 16 de marzo de 2026', fecha: '2026-04-03',
        antecedentes: ['El señor Percy Alexander Llontop Baldera presentó expediente de licencia de edificación en la modalidad B para vivienda unifamiliar en Mz. C Lote 12, Urbanización Las Brisas.'],
        analisis: ['Revisada la documentación, se verificó que el administrado acredita la titularidad del predio y que el expediente contiene los requisitos exigidos por el Reglamento de Licencias de Habilitación Urbana y Licencias de Edificación.', 'En la revisión técnica se comprobó que el proyecto cumple con los parámetros urbanísticos: altura de 2 pisos y azotea, retiro frontal de 3.00 m, coeficiente de edificación de 1.79 y área libre de 38.4 %.', 'El proyecto de estructuras fue calculado conforme a la Norma E.030 de Diseño Sismorresistente y la Norma E.060 de Concreto Armado.'],
        conclusiones: ['El expediente cumple con los requisitos administrativos y técnicos.', 'Procede otorgar la licencia de edificación solicitada.'],
        recomendaciones: ['Emitir la resolución de licencia de edificación.', 'Notificar al administrado la obligación de comunicar el inicio de obra y solicitar la conformidad de obra al término de la construcción.'], ...f(P.sgopc),
      })),
      pdf('Resolución de licencia de edificación', resolucion({
        tipo: 'RESOLUCIÓN DE SUB GERENCIA', num: '034-2026-SGOPC/GDUR/MDSJ', fecha: '2026-04-06',
        visto: ['El Expediente EDIF-2026-017 presentado por Percy Alexander Llontop Baldera y el Informe Técnico N° 034-2026-SGOPC/GDUR/MDSJ.'],
        baseLegal: ['Que, la Ley N° 29090, Ley de Regulación de Habilitaciones Urbanas y de Edificaciones, establece el procedimiento para la obtención de licencias de edificación.'],
        considerandos: ['Que, el informe técnico concluye que el proyecto cumple con la normativa vigente y los parámetros urbanísticos y edificatorios de la zona.'],
        articulos: ['OTORGAR la Licencia de Edificación en la modalidad B a favor de Percy Alexander Llontop Baldera para la construcción de una vivienda unifamiliar de dos pisos con un área techada de 142.60 m2 en el predio ubicado en Mz. C Lote 12, Urbanización Las Brisas.', 'La presente licencia tiene una vigencia de treinta y seis (36) meses contados desde su emisión.', 'NOTIFICAR la presente resolución al administrado y a la Gerencia de Administración Tributaria y Rentas.'], ...f(P.sgopc),
      })),
    ],
  },
  // 3 ─────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'CPUE-2026-009', area: 'Sub Gerencia de Obras Privadas y Catastro', tipo: 'Certificado',
    remitente: 'Inmobiliaria Costa Norte S.A.C.', fecha: '2026-04-06', estado: 'Archivado', autor: 'rosa',
    asunto: 'Certificado de parámetros urbanísticos y edificatorios - Lote 5 Mz. F', glosa: 'Certificado de parámetros para proyecto multifamiliar de cuatro pisos.',
    docs: [
      pdf('Solicitud de certificado de parámetros', solicitud({
        sumilla: 'Certificado de parámetros urbanísticos y edificatorios', para: 'GERENTE DE DESARROLLO URBANO Y RURAL', nombre: 'Elena Patricia Sandoval Ruiz (representante legal de Inmobiliaria Costa Norte S.A.C.)', dni: '17402963',
        domicilio: 'Av. Central N° 455, oficina 302', fecha: '2026-04-06', exposicion: ['Que, mi representada es propietaria del Lote 5 de la Mz. F, con un área de 240.00 m2, donde proyecta desarrollar un edificio multifamiliar de cuatro pisos.'],
        pedido: 'se sirva expedir el certificado de parámetros urbanísticos y edificatorios del predio indicado', anexos: ['Vigencia de poder de la representante legal', 'Copia literal de dominio', 'Plano de ubicación y localización', 'Recibo de pago por derecho de trámite'],
      })),
      pdf('Certificado de parámetros urbanísticos', certificado({
        titulo: 'CERTIFICADO DE PARÁMETROS URBANÍSTICOS Y EDIFICATORIOS', num: '009-2026-SGOPC/GDUR', area: 'Sub Gerencia de Obras Privadas y Catastro', fecha: '2026-04-14',
        cuerpo: ['Que, revisado el Plan de Desarrollo Urbano vigente, se certifican los siguientes parámetros para el predio de la Mz. F Lote 5:'],
        datos: [['ZONIFICACIÓN', 'Residencial de Densidad Media (RDM)'], ['USO', 'Vivienda multifamiliar'], ['ALTURA MÁXIMA', '4 pisos (12.00 m)'], ['ÁREA LIBRE', '30 %'], ['RETIRO FRONTAL', '3.00 m'], ['ESTACIONAMIENTOS', '1 por cada vivienda'], ['VIGENCIA', 'Treinta y seis (36) meses']], ...f(P.sgopc),
      })),
    ],
  },
  // 4 ─────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'POS-2026-003', area: 'Gerencia de Desarrollo Urbano y Rural', tipo: 'Constancia',
    remitente: 'Asociación de Vivienda Nueva Esperanza', fecha: '2026-01-22', estado: 'En trámite', autor: 'jorge',
    asunto: 'Constancia de posesión para acceso a servicios básicos - AA.HH. Nueva Esperanza Mz. A', glosa: 'Trámite en curso. Pendiente la resolución de la constancia de posesión colectiva tras la inspección ocular.',
    docs: [
      pdf('Solicitud de constancia de posesión', solicitud({
        sumilla: 'Constancia de posesión para servicios básicos', para: 'GERENTE DE DESARROLLO URBANO Y RURAL', nombre: 'Carmen Rosa Cajusol Fernández (presidenta de la Asociación de Vivienda Nueva Esperanza)', dni: '43617829',
        domicilio: 'AA.HH. Nueva Esperanza, Mz. A Lote 1', fecha: '2026-01-22', exposicion: ['Que, la Asociación que represento agrupa a 46 familias que ocupan de manera pacífica y continua, desde el año 2018, los lotes de la Mz. A del AA.HH. Nueva Esperanza.', 'Que, los pobladores requieren acceder a los servicios de agua potable, alcantarillado y energía eléctrica, para lo cual necesitan la constancia de posesión correspondiente.'],
        pedido: 'se sirva expedir las constancias de posesión de los 46 lotes de la Mz. A, previa inspección ocular', anexos: ['Padrón de posesionarios con firma y huella', 'Plano perimétrico y de ubicación', 'Declaración jurada de posesión', 'Acta de la asamblea general de la asociación'],
      })),
      pdf('Informe de inspección ocular', informe({
        titulo: 'INFORME', num: '011-2026-GDUR/MDSJ', area: 'Gerencia de Desarrollo Urbano y Rural', a: 'Gerencia Municipal', de: 'Gerencia de Desarrollo Urbano y Rural', asunto: 'Inspección ocular AA.HH. Nueva Esperanza Mz. A', ref: 'Solicitud POS-2026-003', fecha: '2026-02-11',
        antecedentes: ['La Asociación de Vivienda Nueva Esperanza solicitó constancias de posesión para los lotes de la Mz. A con el fin de acceder a los servicios básicos.'],
        analisis: ['Se realizó la inspección el 6 de febrero de 2026 con participación de los dirigentes. Se comprobó la ocupación efectiva de 44 de los 46 lotes; dos lotes se encuentran desocupados.', 'Los lotes ocupados no se superponen con áreas de riesgo ni con zonas reservadas para equipamiento urbano ni con propiedad de terceros según el catastro municipal.'],
        conclusiones: ['Procede la constancia de posesión para 44 lotes.', 'Los dos lotes desocupados deben ser excluidos del padrón.'], recomendaciones: ['Emitir las constancias de posesión de los 44 lotes acreditados.', 'Solicitar a la asociación la actualización del padrón.'], ...f(P.gdur),
      })),
    ],
  },
  // 5 ─────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'RA-2026-012', area: 'Alcaldía', tipo: 'Resolución de Alcaldía', remitente: 'Alcaldía', fecha: '2026-01-05', estado: 'Archivado', autor: 'rosa',
    asunto: 'Designación del Gerente de Administración y Finanzas', glosa: 'Designa como funcionaria de confianza a la CPC Lidia Marisol Reque Carrasco.',
    docs: [pdf('Resolución de Alcaldía N° 012-2026-A-MDSJ', resolucion({
      num: '012-2026-A/MDSJ', fecha: '2026-01-05', visto: ['El Informe N° 003-2026-SGRH/MDSJ de la Sub Gerencia de Recursos Humanos, y la necesidad de designar al responsable de la Gerencia de Administración y Finanzas.'],
      considerandos: ['Que, el artículo 20, numeral 17, de la Ley N° 27972 faculta al alcalde a designar y cesar al gerente municipal y a propuesta de este a los demás funcionarios de confianza.', 'Que, resulta necesario designar a la profesional que asumirá la conducción de la Gerencia de Administración y Finanzas, considerando su experiencia en gestión financiera pública.'],
      articulos: ['DESIGNAR, a partir del 6 de enero de 2026, a la CPC Lidia Marisol Reque Carrasco en el cargo de confianza de Gerente de Administración y Finanzas de la Municipalidad Distrital de San José.', 'ENCARGAR a la Secretaría General la notificación de la presente resolución a la interesada y a la Sub Gerencia de Recursos Humanos.', 'DISPONER su publicación en el portal institucional.'], ...f(P.alcalde),
    }))],
  },
  // 6 ─────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'PAC-2026-001', area: 'Oficina de Planeamiento y Presupuesto', tipo: 'Resolución de Alcaldía', remitente: 'Oficina de Planeamiento y Presupuesto', fecha: '2026-01-30', estado: 'Archivado', autor: 'jorge',
    asunto: 'Aprobación del Plan Anual de Contrataciones 2026', glosa: 'Aprueba el PAC 2026 por un monto total de S/ 4,215,880.00, con 38 procedimientos de selección.',
    docs: [
      pdf('Informe de consolidación del PAC 2026', informe({
        num: '009-2026-OPP/MDSJ', area: 'Oficina de Planeamiento y Presupuesto', a: 'Gerencia Municipal', de: 'Oficina de Planeamiento y Presupuesto', asunto: 'Consolidación del Plan Anual de Contrataciones 2026', ref: 'Presupuesto Institucional de Apertura 2026', fecha: '2026-01-27',
        antecedentes: ['Las áreas usuarias remitieron sus cuadros de necesidades para el ejercicio 2026, los cuales fueron consolidados por la Sub Gerencia de Logística y Control Patrimonial.'],
        analisis: ['El Plan Anual de Contrataciones 2026 consolida treinta y ocho (38) procedimientos de selección y contrataciones directas por un valor estimado total de S/ 4,215,880.00, distribuidos en bienes (S/ 1,102,340.00), servicios (S/ 1,298,760.00) y obras (S/ 1,814,780.00).', 'Se verificó que todas las contrataciones cuentan con la previsión presupuestal en el Presupuesto Institucional de Apertura y que se ha incluido el cronograma de convocatorias por trimestres.'],
        conclusiones: ['El PAC 2026 se encuentra conforme y alineado al Plan Operativo Institucional.'], recomendaciones: ['Aprobar el Plan Anual de Contrataciones 2026 mediante resolución.', 'Publicar el plan en el portal del organismo supervisor de las contrataciones del Estado.'], ...f(P.opp),
      })),
      pdf('Resolución de Alcaldía N° 031-2026-A-MDSJ', resolucion({
        num: '031-2026-A/MDSJ', fecha: '2026-01-30', visto: ['El Informe N° 009-2026-OPP/MDSJ y el Memorando N° 044-2026-GM/MDSJ.'],
        considerandos: ['Que, la normativa de contrataciones del Estado dispone que cada entidad debe elaborar su plan anual de contrataciones y aprobarlo dentro del plazo establecido.', 'Que, el plan consolidado se encuentra conforme y cuenta con opinión favorable de la Oficina de Planeamiento y Presupuesto.'],
        articulos: ['APROBAR el Plan Anual de Contrataciones de la Municipalidad Distrital de San José para el ejercicio fiscal 2026 por un monto total de S/ 4,215,880.00.', 'DISPONER que la Sub Gerencia de Logística y Control Patrimonial publique el plan en la plataforma correspondiente dentro de los plazos de ley.'], ...f(P.alcalde),
      })),
    ],
  },
  // 7 ─────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'ORD-2026-004', area: 'Secretaría General', tipo: 'Ordenanza Municipal', remitente: 'Concejo Municipal', fecha: '2026-02-27', estado: 'Archivado', autor: 'rosa',
    asunto: 'Ordenanza que aprueba el marco tributario de arbitrios municipales 2026', glosa: 'Aprueba las tasas de arbitrios de limpieza pública, parques y jardines y serenazgo para 2026.',
    docs: [
      pdf('Informe técnico de costos de arbitrios', informe({
        titulo: 'INFORME TÉCNICO', num: '004-2026-GATR/MDSJ', area: 'Gerencia de Administración Tributaria y Rentas', a: 'Gerencia Municipal', de: 'Gerencia de Administración Tributaria y Rentas', asunto: 'Estructura de costos y tasas de arbitrios municipales 2026', fecha: '2026-02-10',
        antecedentes: ['El Tribunal Constitucional ha establecido criterios de validez para la aprobación de arbitrios, entre ellos la publicación de los informes técnicos de costos y de distribución.'],
        analisis: ['El costo total del servicio de limpieza pública asciende a S/ 612,480.00, el de parques y jardines a S/ 214,930.00 y el de serenazgo a S/ 388,240.00, lo que da un total de S/ 1,215,650.00.', 'La distribución del costo entre los predios se ha realizado con criterios de uso, área construida y frecuencia del servicio, aplicando un reajuste de 3.2 % respecto al año anterior, conforme al Índice de Precios al Consumidor de Lima Metropolitana.'],
        conclusiones: ['Las tasas propuestas cumplen los criterios de razonabilidad y proporcionalidad.', 'El monto total recaudable por arbitrios en 2026 asciende a S/ 1,215,650.00.'], recomendaciones: ['Elevar el proyecto de ordenanza al Concejo Municipal para su aprobación.', 'Publicar los informes técnicos junto con la ordenanza.'], ...f(P.gatr),
      })),
      pdf('Ordenanza Municipal N° 004-2026-MDSJ', ordenanza({
        num: '004-2026-MDSJ', fecha: '2026-02-24', considerandos: ['Que, el artículo 74 de la Constitución Política del Perú y la Norma IV del Título Preliminar del Código Tributario reconocen a los gobiernos locales la potestad de crear, modificar y suprimir contribuciones y tasas dentro de su jurisdicción.', 'Que, con el Informe Técnico N° 004-2026-GATR/MDSJ se sustenta la estructura de costos y la distribución de los arbitrios municipales para el ejercicio 2026.'],
        titulo: 'ORDENANZA QUE APRUEBA EL MARCO TRIBUTARIO DE LOS ARBITRIOS MUNICIPALES 2026',
        articulos: ['APROBAR el marco tributario de los arbitrios municipales de limpieza pública, parques y jardines y serenazgo para el ejercicio fiscal 2026, así como las tasas que constan en los anexos de la presente ordenanza.', 'ESTABLECER que los arbitrios se pagarán en cuatro cuotas trimestrales, con vencimiento el último día hábil de los meses de marzo, junio, setiembre y diciembre.', 'PRECISAR que los predios de propiedad de personas adultas mayores, pensionistas, cuyo único ingreso sea la pensión, gozarán de una reducción del 50 % del monto de los arbitrios, previa solicitud.', 'ENCARGAR a la Gerencia de Administración Tributaria y Rentas la emisión y distribución de los recibos, y a la Secretaría General la publicación de la presente ordenanza.', 'DISPONER que la presente ordenanza entra en vigencia al día siguiente de su publicación.'], ...f(P.alcalde),
      })),
    ],
  },
  // 8 ─────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'AC-2026-011', area: 'Secretaría General', tipo: 'Acuerdo de Concejo', remitente: 'Concejo Municipal', fecha: '2026-05-12', estado: 'Activo', autor: 'jorge',
    asunto: 'Acuerdo de Concejo que autoriza suscribir convenio de cooperación con la UGEL', glosa: 'Autoriza al alcalde a suscribir convenio con la UGEL para el mantenimiento de locales escolares.',
    docs: [
      pdf('Acuerdo de Concejo N° 011-2026-MDSJ', resolucion({
        tipo: 'ACUERDO DE CONCEJO', num: '011-2026-MDSJ', fecha: '2026-05-12', visto: ['En Sesión Ordinaria de Concejo de fecha 12 de mayo de 2026, el Oficio N° 087-2026-UGEL y el Informe Legal N° 019-2026-OAJ/MDSJ.'],
        baseLegal: ['Que, el artículo 41 de la Ley N° 27972 establece que los acuerdos son decisiones que toma el concejo, referidas a asuntos específicos de interés público, vecinal o institucional.'],
        considerandos: ['Que, la Unidad de Gestión Educativa Local propone la suscripción de un convenio de cooperación interinstitucional para ejecutar trabajos de mantenimiento en los locales escolares del distrito.', 'Que, la Oficina de Asesoría Jurídica ha emitido opinión favorable sobre la viabilidad legal del convenio.'],
        articulos: ['AUTORIZAR al señor alcalde a suscribir el Convenio de Cooperación Interinstitucional con la Unidad de Gestión Educativa Local para el mantenimiento de locales escolares.', 'ENCARGAR a la Gerencia Municipal y a la Gerencia de Desarrollo Social el seguimiento del cumplimiento del convenio.'], ...f(P.alcalde),
      })),
      pdf('Convenio de cooperación interinstitucional', contrato({
        titulo: 'CONVENIO DE COOPERACIÓN INTERINSTITUCIONAL', numero: 'Entre la Municipalidad Distrital de San José y la UGEL', fecha: '2026-05-20', firmaEntidad: 'Alcalde', firmaContratista: 'Director UGEL',
        partes: 'Conste por el presente documento el Convenio de Cooperación Interinstitucional que celebran, de una parte, la MUNICIPALIDAD DISTRITAL DE SAN JOSÉ, representada por su alcalde, Ing. Roberto Carlos Santamaría Guevara, y de la otra, la UNIDAD DE GESTIÓN EDUCATIVA LOCAL, representada por su director, en los términos siguientes:',
        clausulas: [['CLÁUSULA PRIMERA: OBJETO', 'El presente convenio tiene por objeto establecer mecanismos de cooperación para el mantenimiento, reparación y mejoramiento de los locales escolares públicos ubicados en el distrito.'], ['CLÁUSULA SEGUNDA: COMPROMISOS DE LA MUNICIPALIDAD', 'La Municipalidad aportará personal técnico, maquinaria y materiales para trabajos de pintura, reparación de techos, servicios higiénicos y áreas verdes, hasta por un monto referencial de S/ 65,000.00.'], ['CLÁUSULA TERCERA: COMPROMISOS DE LA UGEL', 'La UGEL priorizará y remitirá el listado de locales a intervenir, supervisará la ejecución y otorgará la conformidad de los trabajos.'], ['CLÁUSULA CUARTA: VIGENCIA', 'El convenio tendrá una vigencia de dos (2) años contados desde su suscripción y podrá ser renovado por acuerdo de las partes.'], ['CLÁUSULA QUINTA: SOLUCIÓN DE CONTROVERSIAS', 'Las controversias que surjan serán resueltas mediante trato directo entre las partes, bajo el principio de buena fe.']],
      })),
    ],
  },
  // 9 ─────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'ACTA-2026-007', area: 'Secretaría General', tipo: 'Acta', remitente: 'Concejo Municipal', fecha: '2026-07-09', estado: 'Activo', autor: 'rosa',
    asunto: 'Acta de la sesión ordinaria de Concejo Municipal N° 007-2026', glosa: 'Aprobación del acta anterior, informes de Alcaldía y regidores, modificación presupuestal y exoneración de arbitrios a una asociación de adultos mayores.',
    docs: [pdf('Acta de sesión ordinaria N° 007-2026', acta({
      titulo: 'ACTA DE SESIÓN ORDINARIA DE CONCEJO MUNICIPAL N° 007-2026', area: 'Secretaría General', fecha: '2026-07-09', hora: '09:10', horaFin: '12:35', lugar: 'la sala de sesiones de la Municipalidad Distrital de San José',
      asistentes: ['Ing. Roberto Carlos Santamaría Guevara, Alcalde', 'Regidor Ángel Ramiro Cumpa Zurita', 'Regidora Norma Beatriz Céspedes Lluén', 'Regidor Óscar Manuel Tuñoque Yovera', 'Regidora Sofía Lucero Rojas Purihuamán', 'Regidor Elmer Segundo Bustamante Chapoñán'],
      agenda: ['Lectura y aprobación del acta de la sesión anterior', 'Informe de Alcaldía sobre la ejecución presupuestal del primer semestre', 'Modificación presupuestaria en el nivel funcional programático', 'Solicitud de exoneración de arbitrios a la Asociación de Adultos Mayores "Vida Nueva"', 'Pedidos y mociones de orden del día'],
      desarrollo: ['Se dio lectura al acta de la Sesión Ordinaria N° 006-2026, la cual fue aprobada por unanimidad, con la observación de la regidora Céspedes Lluén sobre la corrección del número de un acuerdo.', 'El señor alcalde informó que la ejecución presupuestal al 30 de junio alcanzó el 47.3 % del Presupuesto Institucional Modificado, destacando el avance de la obra de mejoramiento de pistas y veredas del Jr. Bolognesi y el cumplimiento de las metas del Programa de Incentivos.', 'La Oficina de Planeamiento y Presupuesto sustentó la modificación presupuestaria por S/ 138,500.00 para atender la adquisición de un vehículo recolector y el mantenimiento de maquinaria pesada. El regidor Tuñoque Yovera consultó sobre el origen de los recursos y se explicó que provienen de saldos por menor ejecución de gasto corriente.', 'Sobre la solicitud de exoneración de arbitrios, la Gerencia de Administración Tributaria y Rentas informó que la asociación agrupa a 52 adultos mayores y que el local es de propiedad municipal cedido en uso. Los regidores debatieron la procedencia y acordaron aprobar una exoneración parcial por el ejercicio 2026.', 'En el punto de pedidos, la regidora Rojas Purihuamán solicitó reforzar el alumbrado público en la Av. Los Pescadores, y el regidor Bustamante Chapoñán pidió un informe sobre las quejas por ruidos molestos en la Calle Grau.'],
      acuerdos: ['Aprobar por unanimidad el acta de la Sesión Ordinaria N° 006-2026.', 'Aprobar por unanimidad la modificación presupuestaria por S/ 138,500.00 presentada por la Oficina de Planeamiento y Presupuesto.', 'Aprobar, por mayoría, la exoneración del 60 % de los arbitrios municipales 2026 a la Asociación de Adultos Mayores "Vida Nueva".', 'Encargar a la Gerencia de Servicios Públicos y Medio Ambiente el reforzamiento del alumbrado público en la Av. Los Pescadores.', 'Solicitar a la Gerencia de Servicios Públicos y Medio Ambiente un informe sobre los ruidos molestos en la Calle Grau para la próxima sesión.'], ...f(P.sg),
    }))],
  },
  // 10 ────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'CONT-2026-031', area: 'Sub Gerencia de Logística y Control Patrimonial', tipo: 'Contrato', remitente: 'Taller Mecánico Los Andes E.I.R.L.', fecha: '2026-03-30', estado: 'Activo', autor: 'jorge',
    asunto: 'Servicio de mantenimiento y reparación del camión compactador de residuos sólidos', glosa: 'Contrato de servicio por S/ 18,450.00, plazo de 30 días. Incluye informe de conformidad.',
    docs: [
      pdf('Contrato de servicio de mantenimiento', contrato({
        titulo: 'CONTRATO DE SERVICIO DE MANTENIMIENTO Y REPARACIÓN', numero: 'Contrato N° 031-2026-MDSJ', fecha: '2026-03-30', firmaEntidad: 'Gerente Municipal', firmaContratista: 'Representante',
        partes: 'Conste por el presente documento el contrato que celebran, de una parte, la MUNICIPALIDAD DISTRITAL DE SAN JOSÉ, en adelante LA ENTIDAD, y de la otra, TALLER MECÁNICO LOS ANDES E.I.R.L., con RUC N° 20604871932, en adelante EL CONTRATISTA, bajo los términos y condiciones siguientes:',
        clausulas: [['CLÁUSULA PRIMERA: OBJETO', 'El contratista se obliga a prestar el servicio de mantenimiento correctivo y reparación del sistema hidráulico, motor y caja de compactación del camión compactador de residuos sólidos, placa V4X-812, de propiedad de la entidad.'], ['CLÁUSULA SEGUNDA: MONTO CONTRACTUAL', `El monto total del contrato asciende a ${soles(18450)} (dieciocho mil cuatrocientos cincuenta con 00/100 soles), incluidos los impuestos de ley y cualquier otro concepto que incida en el costo del servicio.`], ['CLÁUSULA TERCERA: PLAZO', 'El plazo de ejecución del servicio es de treinta (30) días calendario, contados desde el día siguiente de la suscripción del contrato.'], ['CLÁUSULA CUARTA: FORMA DE PAGO', 'La entidad realizará el pago en una sola armada, luego de la recepción del servicio y otorgada la conformidad por la Gerencia de Servicios Públicos y Medio Ambiente.'], ['CLÁUSULA QUINTA: GARANTÍA', 'El contratista garantiza los trabajos realizados por un periodo de seis (6) meses contra defectos de mano de obra y repuestos.'], ['CLÁUSULA SEXTA: PENALIDAD', 'En caso de retraso injustificado se aplicará una penalidad diaria equivalente al 0.10 x monto / (0.25 x plazo en días), hasta un máximo del 10 % del monto del contrato.']],
      })),
      pdf('Carta de presentación de repuestos', carta({
        num: '018-2026-TMLA', fecha: '2026-04-08', destinatario: 'Municipalidad Distrital de San José - Sub Gerencia de Logística y Control Patrimonial', asunto: 'Remisión de repuestos y avance del servicio de reparación',
        cuerpo: ['Por medio de la presente les comunico que se ha concluido el desmontaje del sistema hidráulico del camión compactador placa V4X-812 y se han recibido los repuestos originales: bomba hidráulica, cilindros de compactación y sellos.', 'Los trabajos de instalación se iniciarán mañana y se estima su culminación en un plazo de doce (12) días calendario, dentro del plazo contractual. Adjuntamos las guías de remisión de los repuestos.'],
        firmante: 'Hilario Wilder Cubas Cabanillas', cargo: 'Gerente General - Taller Mecánico Los Andes E.I.R.L.',
      })),
      pdf('Informe de conformidad del servicio', informe({
        num: '027-2026-GSPMA/MDSJ', area: 'Gerencia de Servicios Públicos y Medio Ambiente', a: 'Sub Gerencia de Logística y Control Patrimonial', de: 'Gerencia de Servicios Públicos y Medio Ambiente', asunto: 'Conformidad del servicio de mantenimiento del camión compactador', ref: 'Contrato N° 031-2026-MDSJ', fecha: '2026-04-27',
        antecedentes: ['Mediante Contrato N° 031-2026-MDSJ se contrató a Taller Mecánico Los Andes E.I.R.L. para la reparación del camión compactador placa V4X-812 por el monto de S/ 18,450.00.'],
        analisis: ['El contratista culminó los trabajos el 24 de abril de 2026, dentro del plazo contractual. Se realizaron pruebas de funcionamiento del sistema hidráulico y de compactación, con resultados satisfactorios.', 'Se verificó la instalación de los repuestos originales conforme a lo ofertado.'],
        conclusiones: ['El servicio se ha ejecutado conforme a los términos de referencia y al contrato.'], recomendaciones: ['Otorgar la conformidad del servicio y tramitar el pago.'], ...f(P.gspma),
      })),
    ],
  },
  // 11 ────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'OC-2026-118', area: 'Sub Gerencia de Logística y Control Patrimonial', tipo: 'Orden de Compra', remitente: 'Librería y Suministros El Estudiante S.R.L.', fecha: '2026-02-19', estado: 'Archivado', autor: 'rosa',
    asunto: 'Adquisición de útiles y materiales de oficina - primer trimestre', glosa: 'Compra menor a 8 UIT. Incluye cotización y orden de compra.',
    docs: [
      pdf('Cotización del proveedor', carta({
        num: '0091-2026', fecha: '2026-02-12', destinatario: 'Municipalidad Distrital de San José - Sub Gerencia de Logística', asunto: 'Cotización de útiles de escritorio y papelería',
        cuerpo: ['En atención a su solicitud de cotización, remitimos nuestra propuesta económica: papel bond A4 de 75 g (60 millares) a S/ 19.50 el paquete de 500 hojas, folders manila (400 unidades) a S/ 0.55, lapiceros (240 unidades) a S/ 0.90, archivadores de palanca (80 unidades) a S/ 6.80, tóner para impresora láser (6 unidades) a S/ 185.00.', 'La propuesta tiene una vigencia de quince (15) días calendario y el plazo de entrega es de siete (7) días.'],
        firmante: 'Nancy Pilar Guevara Lozano', cargo: 'Gerente - Librería y Suministros El Estudiante S.R.L.',
      })),
      pdf('Orden de compra N° 118-2026', orden({
        num: '118-2026', area: 'Sub Gerencia de Logística y Control Patrimonial', proveedor: 'Librería y Suministros El Estudiante S.R.L.', ruc: '20481936527', fecha: '2026-02-19',
        items: [['Papel bond A4 de 75 g, paquete de 500 hojas', 60, 'paquetes', 19.5], ['Folder manila oficio', 400, 'unidades', 0.55], ['Lapicero de tinta seca color azul', 240, 'unidades', 0.9], ['Archivador de palanca oficio', 80, 'unidades', 6.8], ['Tóner para impresora láser', 6, 'unidades', 185]], ...f(P.sglcp),
      })),
    ],
  },
  // 12 ────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'OBR-2026-002', area: 'Sub Gerencia de Obras Públicas', tipo: 'Contrato', remitente: 'Consorcio Vial San José', fecha: '2026-04-22', estado: 'Activo', autor: 'jorge',
    asunto: 'Mejoramiento de pistas y veredas del Jr. Bolognesi, cuadras 1 a 4', glosa: 'Obra por S/ 1,286,540.00, plazo de 120 días. Expediente técnico, resolución de aprobación, contrato de obra y acta de entrega de terreno.',
    docs: [
      pdf('Informe de aprobación del expediente técnico', informe({
        titulo: 'INFORME TÉCNICO', num: '015-2026-SGOP/MDSJ', area: 'Sub Gerencia de Obras Públicas', a: 'Gerencia de Desarrollo Urbano y Rural', de: 'Sub Gerencia de Obras Públicas', asunto: 'Revisión del expediente técnico de la obra Mejoramiento de pistas y veredas del Jr. Bolognesi', ref: 'Proyecto de inversión con CUI aprobado', fecha: '2026-03-20',
        antecedentes: ['La obra forma parte del Plan Anual de Contrataciones 2026 y cuenta con financiamiento con recursos del canon y recursos ordinarios.'],
        analisis: ['El expediente técnico contempla el pavimento flexible con carpeta asfáltica en caliente de 5 cm en 3,200 m2, la construcción de veredas de concreto de f\'c = 175 kg/cm2 en 1,480 m2, sardineles, rampas para personas con discapacidad, señalización horizontal y vertical, y arborización.', 'El presupuesto de obra asciende a S/ 1,286,540.00, con un plazo de ejecución de ciento veinte (120) días calendario. Se revisaron los metrados, los análisis de precios unitarios, el cronograma y el estudio de suelos, encontrándose conformes.'],
        conclusiones: ['El expediente técnico cumple los requisitos y puede ser aprobado.'], recomendaciones: ['Aprobar el expediente técnico mediante resolución gerencial.', 'Remitir a la Sub Gerencia de Logística para el proceso de selección.'], ...f(P.sgop),
      })),
      pdf('Resolución gerencial que aprueba el expediente técnico', resolucion({
        tipo: 'RESOLUCIÓN GERENCIAL', num: '041-2026-GDUR/MDSJ', fecha: '2026-03-27', visto: ['El Informe Técnico N° 015-2026-SGOP/MDSJ de la Sub Gerencia de Obras Públicas.'],
        considerandos: ['Que, el expediente técnico de la obra "Mejoramiento de pistas y veredas del Jr. Bolognesi, cuadras 1 a 4" ha sido revisado y cuenta con opinión favorable de la Sub Gerencia de Obras Públicas.'],
        articulos: ['APROBAR el expediente técnico de la obra "Mejoramiento de pistas y veredas del Jr. Bolognesi, cuadras 1 a 4" por un monto de S/ 1,286,540.00 y un plazo de ejecución de ciento veinte (120) días calendario.', 'REMITIR el expediente a la Sub Gerencia de Logística y Control Patrimonial para la convocatoria del procedimiento de selección.'], ...f(P.gdur),
      })),
      pdf('Contrato de ejecución de obra', contrato({
        titulo: 'CONTRATO DE EJECUCIÓN DE OBRA', numero: 'Contrato N° 019-2026-MDSJ', fecha: '2026-04-22', firmaEntidad: 'Gerente Municipal', firmaContratista: 'Rep. Consorcio',
        partes: 'Conste por el presente documento el contrato de ejecución de obra que celebran la MUNICIPALIDAD DISTRITAL DE SAN JOSÉ, en adelante LA ENTIDAD, y el CONSORCIO VIAL SAN JOSÉ, integrado por Constructora Pacífico S.A.C. y Ingenieros Asociados Norte S.R.L., en adelante EL CONTRATISTA.',
        clausulas: [['CLÁUSULA PRIMERA: OBJETO', 'El contratista se obliga a ejecutar la obra "Mejoramiento de pistas y veredas del Jr. Bolognesi, cuadras 1 a 4", conforme al expediente técnico aprobado con Resolución Gerencial N° 041-2026-GDUR/MDSJ.'], ['CLÁUSULA SEGUNDA: MONTO', `El monto del contrato es de ${soles(1286540)}, bajo el sistema de contratación a suma alzada.`], ['CLÁUSULA TERCERA: PLAZO', 'El plazo de ejecución de la obra es de ciento veinte (120) días calendario, contado desde el día siguiente de la entrega del terreno.'], ['CLÁUSULA CUARTA: GARANTÍAS', 'El contratista ha presentado la carta fianza de fiel cumplimiento por el 10 % del monto contractual y la carta fianza por adelanto directo por el 20 % del monto contractual.'], ['CLÁUSULA QUINTA: SUPERVISIÓN', 'La entidad designa como inspector de obra a un ingeniero civil colegiado, quien velará por el cumplimiento del contrato y del expediente técnico.'], ['CLÁUSULA SEXTA: VALORIZACIONES', 'Las valorizaciones serán mensuales y se pagarán dentro de los quince (15) días siguientes a su aprobación por el inspector.']],
      })),
      pdf('Acta de entrega de terreno', acta({
        titulo: 'ACTA DE ENTREGA DE TERRENO', area: 'Sub Gerencia de Obras Públicas', fecha: '2026-04-29', hora: '10:00', horaFin: '11:20', lugar: 'el Jr. Bolognesi, cuadra 1, distrito de San José',
        asistentes: ['Ing. Hugo César Bances Zapata, Sub Gerente de Obras Públicas', 'Ing. Víctor Hugo Peralta Sánchez, inspector de obra', 'Ing. Fernando Augusto Calderón Vera, residente de obra del Consorcio Vial San José'],
        agenda: ['Verificación del estado del terreno', 'Entrega del área a intervenir', 'Inicio del plazo de ejecución'],
        desarrollo: ['Se realizó el recorrido de las cuatro cuadras del Jr. Bolognesi y se constató que el terreno se encuentra libre de ocupaciones e interferencias, sin restos de instalaciones que impidan el inicio de los trabajos.', 'Se dejó constancia de la ubicación de las redes de agua y desagüe existentes, las cuales serán protegidas durante la ejecución.'],
        acuerdos: ['La entidad hace entrega formal del terreno al contratista.', 'El plazo de ejecución de 120 días calendario se inicia el 30 de abril de 2026.'], ...f(P.sgop),
      })),
    ],
  },
  // 13 ────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'CONV-2026-003', area: 'Alcaldía', tipo: 'Convenio', remitente: 'I.E. N° 10123 Santa Ana', fecha: '2026-06-02', estado: 'Activo', autor: 'rosa',
    asunto: 'Convenio con la I.E. N° 10123 para mantenimiento de áreas verdes', glosa: 'Convenio de colaboración para el mantenimiento y riego de áreas verdes escolares.',
    docs: [pdf('Convenio con la I.E. N° 10123', contrato({
      titulo: 'CONVENIO DE COLABORACIÓN', numero: 'Convenio N° 003-2026-MDSJ', fecha: '2026-06-02', firmaEntidad: 'Alcalde', firmaContratista: 'Director I.E.',
      partes: 'Conste por el presente documento el convenio de colaboración que celebran la MUNICIPALIDAD DISTRITAL DE SAN JOSÉ y la INSTITUCIÓN EDUCATIVA N° 10123 "SANTA ANA", representada por su director, para el mantenimiento y riego de las áreas verdes del local escolar.',
      clausulas: [['CLÁUSULA PRIMERA: OBJETO', 'Establecer las condiciones para que la Municipalidad brinde el servicio de mantenimiento, poda y riego de las áreas verdes de la institución educativa, con participación de los estudiantes en actividades de educación ambiental.'], ['CLÁUSULA SEGUNDA: COMPROMISOS', 'La Municipalidad destinará dos operarios con frecuencia semanal y proveerá las plantas ornamentales. La institución educativa facilitará el acceso al local y designará a un docente coordinador.'], ['CLÁUSULA TERCERA: VIGENCIA', 'El convenio tiene una vigencia de un (1) año, renovable por acuerdo expreso de las partes.']],
    }))],
  },
  // 14 ────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'ACC-2026-027', area: 'Secretaría General', tipo: 'Constancia', remitente: 'Erick Manuel Vílchez Bravo', fecha: '2026-06-03', estado: 'Archivado', autor: 'jorge',
    asunto: 'Solicitud de acceso a la información pública: contratos de servicios 2025', glosa: 'Solicitud amparada en la Ley de Transparencia y Acceso a la Información Pública. Respondida dentro del plazo de 10 días hábiles.',
    docs: [
      pdf('Solicitud de acceso a la información', solicitud({
        sumilla: 'Acceso a la información pública', para: 'FUNCIONARIO RESPONSABLE DE ACCESO A LA INFORMACIÓN PÚBLICA', nombre: 'Erick Manuel Vílchez Bravo', dni: '46231785', domicilio: 'Calle Bolívar N° 322, distrito de San José', fecha: '2026-06-03',
        exposicion: ['Que, en ejercicio del derecho reconocido en el artículo 2, numeral 5, de la Constitución Política del Perú y en el Texto Único Ordenado de la Ley N° 27806, Ley de Transparencia y Acceso a la Información Pública, requiero información de carácter público.'],
        pedido: 'copia simple en formato digital de los contratos de locación de servicios suscritos por la entidad durante el ejercicio 2025, con indicación del monto y el objeto de cada contrato', anexos: ['Copia del DNI', 'Comprobante de pago del costo de reproducción'],
      })),
      pdf('Oficio de respuesta', oficio({
        num: '0184-2026-SG/MDSJ', area: 'Secretaría General', fecha: '2026-06-12', destinatario: 'Erick Manuel Vílchez Bravo', cargoDestinatario: 'Solicitante', asunto: 'Respuesta a la solicitud de acceso a la información pública', ref: 'Solicitud ACC-2026-027',
        cuerpo: ['En atención a su solicitud, se remite en formato digital copia de los contratos de locación de servicios suscritos durante el ejercicio 2025, los cuales suman ciento doce (112) contratos con sus respectivos términos de referencia.', 'De conformidad con el artículo 17 del TUO de la Ley N° 27806, se han omitido los datos personales protegidos, tales como los números de cuenta bancaria y los domicilios.'],
        ...f(P.sg),
      })),
    ],
  },
  // 15 ────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'ITSE-2026-014', area: 'Sub Gerencia de Gestión de Riesgos y Defensa Civil', tipo: 'Certificado', remitente: 'I.E. N° 10123 Santa Ana', fecha: '2026-03-02', estado: 'Archivado', autor: 'rosa',
    asunto: 'Inspección técnica de seguridad en edificaciones (ITSE) - Institución Educativa', glosa: 'ITSE de riesgo alto para institución educativa. Certificado con vigencia de dos años.',
    docs: [
      pdf('Oficio de solicitud de ITSE', oficio({
        num: '045-2026-IE10123', area: 'Institución Educativa N° 10123 "Santa Ana"', fecha: '2026-03-02', destinatario: 'Ing. Juan Carlos Uypan Bravo', cargoDestinatario: 'Sub Gerente de Gestión de Riesgos y Defensa Civil', asunto: 'Solicitud de inspección técnica de seguridad en edificaciones',
        cuerpo: ['Solicito que se programe la inspección técnica de seguridad en edificaciones (ITSE) para el local de la Institución Educativa N° 10123 "Santa Ana", que alberga a 486 estudiantes y 28 docentes, a fin de renovar el certificado que vence el próximo mes.'],
        firmante: 'Prof. Luis Alberto Mego Silva', cargo: 'Director de la I.E. N° 10123',
      })),
      pdf('Informe de inspección técnica', informe({
        num: '008-2026-SGGRDC/MDSJ', area: 'Sub Gerencia de Gestión de Riesgos y Defensa Civil', a: 'Gerencia Municipal', de: 'Sub Gerencia de Gestión de Riesgos y Defensa Civil', asunto: 'Resultado de la ITSE a la I.E. N° 10123 Santa Ana', ref: 'Oficio N° 045-2026-IE10123', fecha: '2026-03-12',
        antecedentes: ['El Decreto Supremo N° 002-2018-PCM aprueba el Nuevo Reglamento de Inspecciones Técnicas de Seguridad en Edificaciones. Los locales escolares se clasifican como de riesgo alto.'],
        analisis: ['La inspección se realizó el 9 de marzo de 2026. Se verificó la señalización de seguridad y rutas de evacuación, el sistema de extintores, las luces de emergencia, las instalaciones eléctricas y el plan de contingencia.', 'Se encontraron observaciones menores: dos extintores próximos a vencer su recarga y una luz de emergencia inoperativa en el pabellón B, las cuales fueron subsanadas durante la misma inspección.'],
        conclusiones: ['El establecimiento cumple con las condiciones de seguridad exigidas por la normativa.'], recomendaciones: ['Emitir el certificado de ITSE con vigencia de dos años.'], ...f(P.sgggrd),
      })),
      pdf('Certificado de ITSE N° 014-2026', certificado({
        titulo: 'CERTIFICADO DE INSPECCIÓN TÉCNICA DE SEGURIDAD EN EDIFICACIONES', num: '014-2026-SGGRDC', area: 'Sub Gerencia de Gestión de Riesgos y Defensa Civil', fecha: '2026-03-13',
        cuerpo: ['Se certifica que, como resultado de la inspección técnica de seguridad en edificaciones posterior a la ejecución, el establecimiento indicado cumple con las condiciones de seguridad.'],
        datos: [['ESTABLECIMIENTO', 'I.E. N° 10123 Santa Ana'], ['USO', 'Educación básica regular'], ['NIVEL DE RIESGO', 'Alto'], ['AFORO MÁXIMO', '540 personas'], ['VIGENCIA', 'Dos (2) años']], ...f(P.sgggrd),
      })),
    ],
  },
  // 16 ────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'FISC-2026-045', area: 'Gerencia de Servicios Públicos y Medio Ambiente', tipo: 'Informe Técnico', remitente: 'Vecinos de la Calle Grau, cuadra 3', fecha: '2026-08-11', estado: 'En trámite', autor: 'jorge',
    asunto: 'Denuncia vecinal por ruidos molestos de establecimiento comercial', glosa: 'Vecinos denuncian ruidos molestos nocturnos de una discoteca. Se realizó la medición de decibeles. Pendiente la imposición de sanción.',
    docs: [
      pdf('Carta de denuncia vecinal', carta({
        fecha: '2026-08-11', destinatario: 'Gerencia de Servicios Públicos y Medio Ambiente', asunto: 'Denuncia por ruidos molestos - Calle Grau cuadra 3',
        cuerpo: ['Los vecinos que suscribimos la presente denunciamos que, desde hace tres meses, el establecimiento denominado "Discobar Oasis", ubicado en la Calle Grau N° 341, emite música a un volumen excesivo hasta las tres de la madrugada los días viernes y sábados, afectando el descanso de las familias, de los niños y de los adultos mayores.', 'Solicitamos que se realice la fiscalización correspondiente, se mida el nivel de ruido y se apliquen las sanciones que establece la ordenanza vigente. Adjuntamos firmas de 27 vecinos y tres videos de evidencia.'],
        firmante: 'María Elena Chávarry Gonzales', cargo: 'Vecina - a nombre de los firmantes (DNI 16789430)',
      })),
      pdf('Informe de fiscalización', informe({
        num: '033-2026-GSPMA/MDSJ', area: 'Gerencia de Servicios Públicos y Medio Ambiente', a: 'Gerencia Municipal', de: 'Gerencia de Servicios Públicos y Medio Ambiente', asunto: 'Fiscalización por ruidos molestos - Discobar Oasis', ref: 'Denuncia vecinal FISC-2026-045', fecha: '2026-08-22',
        antecedentes: ['Los vecinos de la Calle Grau cuadra 3 presentaron una denuncia por ruidos molestos provenientes del establecimiento "Discobar Oasis".'],
        analisis: ['El día sábado 15 de agosto, a las 00:40 horas, personal de fiscalización realizó la medición del nivel de presión sonora en el exterior del establecimiento, obteniendo 78 dB(A), superior al límite de 60 dB(A) establecido para zona residencial en horario nocturno por el Estándar de Calidad Ambiental para Ruido.', 'Se levantó el acta de constatación y se comprobó que el establecimiento cuenta con licencia de funcionamiento con horario hasta las 24:00 horas, por lo que se encuentra fuera del horario autorizado.'],
        conclusiones: ['El establecimiento infringe los límites de ruido y el horario autorizado.'], recomendaciones: ['Iniciar el procedimiento sancionador y notificar el cargo al administrado.', 'Ordenar la suspensión temporal de la actividad musical fuera del horario.'], ...f(P.gspma),
      })),
      pdf('Memorando de derivación a fiscalización', memorando({
        num: '112-2026-GM/MDSJ', area: 'Gerencia Municipal', a: 'Sub Gerencia de Desarrollo Económico y Licencias', de: 'Gerencia Municipal', asunto: 'Inicio de procedimiento sancionador - Discobar Oasis', fecha: '2026-08-25',
        cuerpo: ['Se remite el Informe N° 033-2026-GSPMA/MDSJ para que, en el marco de sus competencias, inicie el procedimiento sancionador correspondiente y evalúe la medida complementaria de clausura temporal del establecimiento.'], ...f(P.gm),
      })),
    ],
  },
  // 17 ────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'VL-2026-001', area: 'Gerencia de Desarrollo Social', tipo: 'Informe Técnico', remitente: 'Gerencia de Desarrollo Social', fecha: '2026-02-13', estado: 'Activo', autor: 'rosa',
    asunto: 'Actualización del padrón de beneficiarios del Programa Vaso de Leche 2026', glosa: 'Empadronamiento de 1,186 beneficiarios en 24 comités de base. Ración de 250 ml diarios por beneficiario.',
    docs: [
      pdf('Informe de actualización del padrón', informe({
        num: '006-2026-GDS/MDSJ', area: 'Gerencia de Desarrollo Social', a: 'Gerencia Municipal', de: 'Gerencia de Desarrollo Social', asunto: 'Actualización del padrón de beneficiarios del Programa del Vaso de Leche', ref: 'Ley N° 24059 y sus modificatorias', fecha: '2026-02-13',
        antecedentes: ['La Ley N° 24059 crea el Programa del Vaso de Leche en todos los concejos provinciales y distritales del país, con el objetivo de ofrecer una ración diaria de alimentos a la población en situación de pobreza.'],
        analisis: ['Se realizó el empadronamiento entre el 3 y el 12 de febrero de 2026. El padrón consolidado comprende 1,186 beneficiarios distribuidos en 24 comités de base: 612 niños de 0 a 6 años, 187 madres gestantes y lactantes, 214 niños de 7 a 13 años, 96 adultos mayores y 77 personas con tuberculosis.', 'La ración diaria es de 250 ml de leche evaporada entera o su equivalente, con un costo mensual estimado de S/ 41,800.00, financiado con recursos del Programa.'],
        conclusiones: ['El padrón se encuentra depurado y validado por los comités de base.'], recomendaciones: ['Aprobar el padrón de beneficiarios 2026.', 'Remitir el padrón a la Contraloría General de la República conforme a la normativa.'], ...f(P.gds),
      })),
      pdf('Oficio de remisión del padrón', oficio({
        num: '0071-2026-GDS/MDSJ', area: 'Gerencia de Desarrollo Social', fecha: '2026-02-18', destinatario: 'Presidente del Comité de Administración del Programa del Vaso de Leche', cargoDestinatario: 'Municipalidad Distrital de San José', asunto: 'Remisión del padrón de beneficiarios 2026',
        cuerpo: ['Se remite el padrón de beneficiarios 2026 aprobado, para su conocimiento y la programación de la distribución de raciones entre los veinticuatro comités de base, a partir del 1 de marzo.'], ...f(P.gds),
      })),
    ],
  },
  // 18 ────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'DEMUNA-2026-022', area: 'Defensoría Municipal del Niño y del Adolescente', tipo: 'Acta', remitente: 'DEMUNA', fecha: '2026-05-28', estado: 'Archivado', autor: 'jorge',
    asunto: 'Acta de conciliación extrajudicial - régimen de visitas', glosa: 'Acuerdo conciliatorio entre progenitores sobre el régimen de visitas de un menor. Partes ficticias.',
    docs: [pdf('Acta de conciliación N° 022-2026', acta({
      titulo: 'ACTA DE CONCILIACIÓN N° 022-2026-DEMUNA', area: 'Defensoría Municipal del Niño y del Adolescente', fecha: '2026-05-28', hora: '15:00', horaFin: '16:10', lugar: 'las oficinas de la DEMUNA',
      asistentes: ['Abog. Cecilia Valdera Ruiz, Defensora Municipal del Niño y del Adolescente (conciliadora)', 'Sra. Yolanda Patricia Ruiz Castillo, madre del menor', 'Sr. Dennis Alexander Paz Otiniano, padre del menor'],
      agenda: ['Régimen de visitas a favor del padre', 'Horarios y lugares de entrega y recojo del menor'],
      desarrollo: ['La conciliadora explicó a las partes el procedimiento y las ventajas de un acuerdo basado en el interés superior del niño, conforme al Código de los Niños y Adolescentes, Ley N° 27337.', 'Las partes manifestaron su voluntad de conciliar y, luego de un diálogo, llegaron a un acuerdo que respeta la rutina escolar y de descanso del menor.'],
      acuerdos: ['El padre visitará al menor los sábados de 10:00 a 18:00 horas, y en las vacaciones escolares dispondrá de una semana continua.', 'La entrega y el recojo se realizarán en el domicilio de la madre.', 'Las partes se comprometen a comunicar con anticipación cualquier cambio en el horario acordado.'], ...f(P.demuna),
    }))],
  },
  // 19 ────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'OMAPED-2026-008', area: 'Oficina Municipal de Atención a las Personas con Discapacidad', tipo: 'Constancia', remitente: 'Hilda Rosario Purisaca Ramos', fecha: '2026-04-20', estado: 'En trámite', autor: 'rosa',
    asunto: 'Inscripción en el Registro Municipal de Personas con Discapacidad', glosa: 'Solicitud de inscripción amparada en la Ley General de la Persona con Discapacidad. Falta la evaluación del certificado médico.',
    docs: [
      pdf('Solicitud de inscripción en la OMAPED', solicitud({
        sumilla: 'Inscripción en el registro municipal', para: 'RESPONSABLE DE LA OMAPED', nombre: 'Hilda Rosario Purisaca Ramos', dni: '32784156', domicilio: 'Calle Miraflores N° 78, distrito de San José', fecha: '2026-04-20',
        exposicion: ['Que, de conformidad con la Ley N° 29973, Ley General de la Persona con Discapacidad, los gobiernos locales deben implementar el registro de personas con discapacidad de su jurisdicción.', 'Que, presento una discapacidad física de tipo motora, con dificultad para la marcha, acreditada con certificado médico.'],
        pedido: 'se sirva inscribirme en el Registro Municipal de Personas con Discapacidad y orientarme en el trámite de la certificación y el carné correspondiente', anexos: ['Copia del DNI', 'Certificado médico', 'Dos fotografías tamaño carné', 'Recibo de servicio que acredite el domicilio'],
      })),
      pdf('Informe social', informe({
        num: '004-2026-OMAPED/MDSJ', area: 'Oficina Municipal de Atención a las Personas con Discapacidad', a: 'Gerencia de Desarrollo Social', de: 'OMAPED', asunto: 'Evaluación social de la solicitante', fecha: '2026-04-30',
        antecedentes: ['La señora Hilda Rosario Purisaca Ramos solicitó su inscripción en el Registro Municipal de Personas con Discapacidad.'],
        analisis: ['Se realizó la visita domiciliaria el 27 de abril. La solicitante vive con su hijo y su nuera en una vivienda de un solo nivel, sin rampas de acceso. Depende de un bastón para desplazarse. Su ingreso familiar proviene de la venta de productos de bazar.'],
        conclusiones: ['La solicitante califica para la inscripción y para el apoyo con un bastón de cuatro puntas.'], recomendaciones: ['Proceder a la inscripción una vez verificado el certificado médico.', 'Incluirla en el programa de ayudas técnicas del segundo semestre.'], ...f(P.omaped),
      })),
    ],
  },
  // 20 ────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'CAS-2026-002', area: 'Sub Gerencia de Recursos Humanos', tipo: 'Acta', remitente: 'Comité de Selección CAS', fecha: '2026-03-25', estado: 'Archivado', autor: 'rosa',
    asunto: 'Proceso de selección CAS N° 002-2026 - Asistente Administrativo', glosa: 'Convocatoria, evaluación y contrato administrativo de servicios (D. Leg. 1057).',
    docs: [
      pdf('Acta de evaluación curricular y entrevista', acta({
        titulo: 'ACTA DE EVALUACIÓN - PROCESO CAS N° 002-2026', area: 'Sub Gerencia de Recursos Humanos', fecha: '2026-03-25', hora: '09:00', horaFin: '13:30', lugar: 'la sala de reuniones de la Gerencia Municipal',
        asistentes: ['Lic. Marco Antonio Sánchez Delgado, presidente del comité', 'CPC Lidia Marisol Reque Carrasco, miembro', 'Abog. Nélida Estefany Cruz Tenorio, miembro'],
        agenda: ['Evaluación curricular de los postulantes aptos', 'Entrevista personal', 'Determinación del cuadro de méritos'],
        desarrollo: ['Se evaluaron los currículos de nueve (9) postulantes que superaron la etapa de verificación de requisitos mínimos. Cinco pasaron a la etapa de entrevista personal.', 'Los puntajes finales se calcularon sobre la base de 40 % de evaluación curricular y 60 % de entrevista, con un puntaje mínimo aprobatorio de 60 puntos.'],
        acuerdos: ['Declarar ganadora a la postulante Kelly Johana Farroñán Puicón con 84.5 puntos.', 'Declarar accesitaria a la postulante Diana Carolina Tarrillo Vásquez con 79.0 puntos.', 'Publicar los resultados en el portal institucional y notificar a la ganadora para la suscripción del contrato.'], ...f(P.sgrh),
      })),
      pdf('Contrato administrativo de servicios', contrato({
        titulo: 'CONTRATO ADMINISTRATIVO DE SERVICIOS', numero: 'Contrato CAS N° 014-2026-MDSJ', fecha: '2026-04-01', firmaEntidad: 'Gerente Municipal', firmaContratista: 'Contratada',
        partes: 'Conste por el presente documento el contrato administrativo de servicios que celebran la MUNICIPALIDAD DISTRITAL DE SAN JOSÉ y la señorita KELLY JOHANA FARROÑÁN PUICÓN, identificada con DNI N° 72019634, al amparo del Decreto Legislativo N° 1057.',
        clausulas: [['CLÁUSULA PRIMERA: OBJETO', 'La contratada prestará servicios como Asistente Administrativo en la Sub Gerencia de Recursos Humanos, realizando labores de apoyo en el control de asistencia, trámite documentario y archivo de legajos.'], ['CLÁUSULA SEGUNDA: PLAZO', 'El contrato se inicia el 1 de abril de 2026 y culmina el 31 de diciembre de 2026, pudiendo ser prorrogado.'], ['CLÁUSULA TERCERA: REMUNERACIÓN', `La contratada percibirá una retribución mensual de ${soles(1800)}, sujeta a los descuentos de ley, y gozará de los derechos que reconoce el régimen CAS: aguinaldos, vacaciones de treinta días y afiliación a EsSalud.`], ['CLÁUSULA CUARTA: JORNADA', 'La jornada de trabajo es de cuarenta y ocho (48) horas semanales.']],
      })),
    ],
  },
  // 21 ────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'RRHH-2026-064', area: 'Sub Gerencia de Recursos Humanos', tipo: 'Informe Técnico', remitente: 'Ricardo Ernesto Alarcón Diaz', fecha: '2026-06-17', estado: 'Registrado', autor: 'jorge',
    asunto: 'Solicitud de licencia con goce de haber por capacitación', glosa: 'Trabajador solicita licencia por curso de gestión pública.',
    docs: [
      pdf('Solicitud de licencia por capacitación', solicitud({
        sumilla: 'Licencia con goce de haber por capacitación', para: 'SUB GERENTE DE RECURSOS HUMANOS', nombre: 'Ricardo Ernesto Alarcón Diaz', dni: '40871239', domicilio: 'Calle Sucre N° 205, distrito de San José', fecha: '2026-06-17',
        exposicion: ['Que, soy servidor de la Sub Gerencia de Logística y Control Patrimonial y he sido aceptado en el curso de especialización en Gestión Pública y Contrataciones del Estado, dictado por la Escuela Nacional de Administración Pública, del 6 al 24 de julio de 2026 en horario de 08:00 a 13:00 horas.'],
        pedido: 'se me otorgue licencia con goce de haber por capacitación oficializada, comprometiéndome a compartir lo aprendido con el equipo de trabajo', anexos: ['Constancia de admisión al curso', 'Sílabo y cronograma', 'Conformidad del jefe inmediato'],
      })),
      pdf('Memorando de respuesta', memorando({
        num: '088-2026-SGRH/MDSJ', area: 'Sub Gerencia de Recursos Humanos', a: 'Ricardo Ernesto Alarcón Diaz', de: 'Sub Gerencia de Recursos Humanos', asunto: 'Respuesta a la solicitud de licencia por capacitación', fecha: '2026-06-25',
        cuerpo: ['En atención a su solicitud, se comunica que se ha autorizado la licencia con goce de haber por capacitación del 6 al 24 de julio de 2026, en el horario de 08:00 a 13:00 horas, debiendo presentar al término del curso el certificado de aprobación y un informe de aplicación.'], ...f(P.sgrh),
      })),
    ],
  },
  // 22 ────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'TES-2026-006', area: 'Sub Gerencia de Tesorería', tipo: 'Informe Técnico', remitente: 'Sub Gerencia de Tesorería', fecha: '2026-04-08', estado: 'Archivado', autor: 'rosa',
    asunto: 'Rendición de caja chica - primer trimestre 2026', glosa: 'Rendición de gastos menores del fondo de caja chica: S/ 4,860.30 sobre un fondo de S/ 5,000.00.',
    docs: [pdf('Informe de rendición de caja chica', informe({
      num: '006-2026-SGT/MDSJ', area: 'Sub Gerencia de Tesorería', a: 'Gerencia de Administración y Finanzas', de: 'Sub Gerencia de Tesorería', asunto: 'Rendición del fondo de caja chica - primer trimestre', fecha: '2026-04-08',
      antecedentes: ['Mediante Resolución de Gerencia Municipal se asignó un fondo de caja chica de S/ 5,000.00 para gastos menores, urgentes y no previsibles del primer trimestre.'],
      analisis: ['Se rindieron gastos por S/ 4,860.30 con comprobantes de pago debidamente sustentados: movilidad local S/ 1,120.00, refrigerios para reuniones institucionales S/ 845.50, mantenimiento menor de equipos S/ 1,290.00, útiles y suministros urgentes S/ 934.80 y servicios de mensajería S/ 670.00.', 'El saldo no utilizado de S/ 139.70 fue depositado en la cuenta institucional el 5 de abril de 2026, con boleta de depósito N° 4471.'],
      conclusiones: ['La rendición se encuentra conforme y no se han detectado gastos observables.'], recomendaciones: ['Aprobar la rendición del fondo de caja chica.', 'Reponer el fondo para el segundo trimestre.'], ...f(P.sgt),
    }))],
  },
  // 23 ────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'RC-2026-019', area: 'Registro Civil', tipo: 'Constancia', remitente: 'Fabiola Karina Montenegro Aguilar', fecha: '2026-07-14', estado: 'En trámite', autor: 'jorge',
    asunto: 'Inscripción extemporánea de nacimiento', glosa: 'Inscripción de nacimiento fuera del plazo. Se requiere declaración jurada y dos testigos.',
    docs: [
      pdf('Solicitud de inscripción extemporánea', solicitud({
        sumilla: 'Inscripción extemporánea de nacimiento', para: 'REGISTRADORA CIVIL DE LA MUNICIPALIDAD DISTRITAL DE SAN JOSÉ', nombre: 'Fabiola Karina Montenegro Aguilar', dni: '45102873', domicilio: 'Calle Los Cedros N° 41, distrito de San José', fecha: '2026-07-14',
        exposicion: ['Que, mi menor hijo nació el 3 de octubre de 2024 en el domicilio familiar, con asistencia de partera, y no fue inscrito dentro del plazo de ley por desconocimiento del procedimiento.'],
        pedido: 'se sirva disponer la inscripción extemporánea del nacimiento de mi menor hijo, conforme a la normativa de RENIEC', anexos: ['Certificado de nacido vivo', 'Copia del DNI de la madre', 'Declaración jurada de dos testigos', 'Constancia de no inscripción'],
      })),
      pdf('Informe de evaluación registral', informe({
        num: '009-2026-RC/MDSJ', area: 'Registro Civil', a: 'Gerencia de Desarrollo Social', de: 'Registro Civil', asunto: 'Evaluación de la solicitud de inscripción extemporánea', fecha: '2026-07-21',
        antecedentes: ['La solicitante presentó su expediente con la documentación requerida para la inscripción extemporánea de nacimiento.'],
        analisis: ['Se verificó, en el sistema de consulta de RENIEC, que no existe inscripción previa del menor. La documentación presentada es conforme, salvo que falta el certificado de nacido vivo original, pues se presentó una copia.'],
        conclusiones: ['El expediente está incompleto en un requisito.'], recomendaciones: ['Notificar a la solicitante para que presente el certificado original en un plazo de tres días hábiles.'], ...f(P.rc),
      })),
    ],
  },
  // 24 ────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'REN-2026-088', area: 'Gerencia de Administración Tributaria y Rentas', tipo: 'Resolución Gerencial', remitente: 'Bodegas del Norte E.I.R.L.', fecha: '2026-05-05', estado: 'Activo', autor: 'rosa',
    asunto: 'Solicitud de fraccionamiento de deuda tributaria', glosa: 'Fraccionamiento de deuda por arbitrios en 12 cuotas. Aprobado.',
    docs: [
      pdf('Solicitud de fraccionamiento', solicitud({
        sumilla: 'Fraccionamiento de deuda tributaria', para: 'GERENTE DE ADMINISTRACIÓN TRIBUTARIA Y RENTAS', nombre: 'Gilberto Alfredo Reyes Fernández (gerente de Bodegas del Norte E.I.R.L.)', dni: '18905427', domicilio: 'Av. Los Pescadores N° 502', fecha: '2026-05-05',
        exposicion: ['Que, mi representada mantiene una deuda tributaria por concepto de arbitrios municipales de los ejercicios 2024 y 2025 por un total de S/ 3,940.60, incluidos intereses moratorios.', 'Que, por dificultades de liquidez, no podemos cancelar el monto en una sola armada.'],
        pedido: 'se apruebe el fraccionamiento de la deuda en doce (12) cuotas mensuales, con la cuota inicial que corresponda', anexos: ['Copia del RUC', 'Poder del representante legal', 'Estado de cuenta corriente del contribuyente', 'Comprobante de pago de la cuota inicial'],
      })),
      pdf('Resolución de fraccionamiento', resolucion({
        tipo: 'RESOLUCIÓN GERENCIAL', num: '077-2026-GATR/MDSJ', fecha: '2026-05-19', visto: ['La solicitud de fraccionamiento de Bodegas del Norte E.I.R.L. y el Informe N° 052-2026-GATR/MDSJ.'],
        baseLegal: ['Que, el artículo 36 del Texto Único Ordenado del Código Tributario, aprobado por Decreto Supremo N° 133-2013-EF, faculta a la administración tributaria a conceder aplazamiento y/o fraccionamiento para el pago de la deuda tributaria.'],
        considerandos: ['Que, el contribuyente ha cumplido con presentar los requisitos y ha cancelado la cuota inicial equivalente al 10 % de la deuda, por S/ 394.06.'],
        articulos: ['APROBAR el fraccionamiento de la deuda tributaria por arbitrios municipales de los ejercicios 2024 y 2025 de Bodegas del Norte E.I.R.L. en doce (12) cuotas mensuales de S/ 295.55, con vencimiento el último día hábil de cada mes.', 'PRECISAR que la falta de pago de dos cuotas consecutivas dará lugar a la pérdida del fraccionamiento y a la exigibilidad inmediata de la deuda.'], ...f(P.gatr),
      })),
    ],
  },
  // 25 ────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'REN-2026-102', area: 'Gerencia de Administración Tributaria y Rentas', tipo: 'Informe Técnico', remitente: 'Santos Agustín Bernal Cieza', fecha: '2026-07-21', estado: 'En trámite', autor: 'jorge',
    asunto: 'Recurso de reconsideración contra Resolución de Determinación por impuesto predial 2025', glosa: 'El contribuyente alega error en el cálculo del autoavalúo.',
    docs: [
      pdf('Recurso de reconsideración', solicitud({
        sumilla: 'Recurso de reconsideración', para: 'GERENTE DE ADMINISTRACIÓN TRIBUTARIA Y RENTAS', nombre: 'Santos Agustín Bernal Cieza', dni: '16741895', domicilio: 'Jr. Ayacucho N° 133, distrito de San José', fecha: '2026-07-21',
        exposicion: ['Que, fui notificado con la Resolución de Determinación N° 0342-2026-GATR, que determina una deuda de impuesto predial 2025 de S/ 1,528.40, considerando un autoavalúo de S/ 96,250.00.', 'Que, el autoavalúo consignado no corresponde a mi predio, pues el área construida real es de 74 m2 y no de 118 m2 como figura en la base de datos, según el plano de la conformidad de obra que adjunto como nueva prueba.'],
        pedido: 'se declare fundado el presente recurso de reconsideración y se rectifique el cálculo del impuesto predial 2025', anexos: ['Copia de la resolución impugnada', 'Plano y conformidad de obra', 'Copia del último recibo de pago'],
      })),
      pdf('Informe legal sobre el recurso', informe({
        titulo: 'INFORME LEGAL', num: '041-2026-OAJ/MDSJ', area: 'Oficina de Asesoría Jurídica', a: 'Gerencia de Administración Tributaria y Rentas', de: 'Oficina de Asesoría Jurídica', asunto: 'Recurso de reconsideración de Santos Agustín Bernal Cieza', ref: 'REN-2026-102', fecha: '2026-08-05',
        antecedentes: ['El administrado interpone recurso de reconsideración contra la Resolución de Determinación N° 0342-2026-GATR dentro del plazo de veinte (20) días hábiles que establece el Código Tributario.'],
        analisis: ['El recurso presenta nueva prueba, el plano y la conformidad de obra, que acredita un área construida distinta de la registrada. Por tanto, cumple el requisito del artículo 124 del Código Tributario para ser evaluado.', 'De la verificación en campo se comprobó que el área construida es de 74 m2. Ello modifica el valor de la construcción y, en consecuencia, el autoavalúo y el impuesto predial determinado.'],
        conclusiones: ['El recurso es procedente y debe ser declarado fundado.'], recomendaciones: ['Emitir nueva determinación con el área construida de 74 m2.', 'Actualizar la base de datos del contribuyente en el registro predial.'], ...f(P.oaj),
      })),
    ],
  },
  // 26 ────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'OAJ-2026-015', area: 'Oficina de Asesoría Jurídica', tipo: 'Informe Técnico', remitente: 'Oficina de Asesoría Jurídica', fecha: '2026-08-19', estado: 'Registrado', autor: 'rosa',
    asunto: 'Informe legal sobre proyecto de ordenanza que regula ferias y comercio ambulatorio', glosa: 'Opinión legal favorable con observaciones sobre el proyecto de ordenanza.',
    docs: [pdf('Informe legal sobre el proyecto de ordenanza', informe({
      titulo: 'INFORME LEGAL', num: '052-2026-OAJ/MDSJ', area: 'Oficina de Asesoría Jurídica', a: 'Gerencia Municipal', de: 'Oficina de Asesoría Jurídica', asunto: 'Proyecto de ordenanza que regula ferias y comercio ambulatorio', ref: 'Memorando N° 118-2026-GM/MDSJ', fecha: '2026-08-19',
      antecedentes: ['La Gerencia Municipal remitió el proyecto de ordenanza que regula el comercio en la vía pública y la realización de ferias en el distrito, para su opinión legal.'],
      analisis: ['La Ley N° 27972 reconoce a las municipalidades la competencia para regular el comercio ambulatorio y autorizar las ferias en su jurisdicción. La materia es, por tanto, de competencia municipal.', 'Sin embargo, se observa que el artículo 8 del proyecto establece una tasa por ocupación de vía pública sin sustento técnico de costos, lo que podría vulnerar el principio de reserva de ley y los criterios de razonabilidad. Asimismo, el artículo 12 no precisa el procedimiento para impugnar las sanciones.'],
      conclusiones: ['El proyecto es legalmente viable, condicionado a subsanar las observaciones.'], recomendaciones: ['Sustentar la tasa con un informe técnico de costos.', 'Incluir el procedimiento de impugnación de sanciones.', 'Remitir el proyecto corregido al Concejo Municipal.'], ...f(P.oaj),
    }))],
  },
  // 27 ────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'OCI-2026-005', area: 'Órgano de Control Institucional', tipo: 'Informe Técnico', remitente: 'Órgano de Control Institucional', fecha: '2026-09-01', estado: 'Registrado', autor: 'jorge',
    asunto: 'Requerimiento de información para servicio de control posterior', glosa: 'Solicita documentación de las contrataciones de obras del ejercicio 2025.',
    docs: [pdf('Oficio del OCI', oficio({
      num: '0053-2026-OCI/MDSJ', area: 'Órgano de Control Institucional', fecha: '2026-09-01', destinatario: 'Abog. Luis Fernando Ordinola Neciosup', cargoDestinatario: 'Gerente Municipal', asunto: 'Requerimiento de información para servicio de control posterior', ref: 'Plan Anual de Control 2026',
      cuerpo: ['En el marco del Plan Anual de Control 2026, se ha programado el servicio de control posterior a las contrataciones de obras públicas del ejercicio 2025. Para tal fin, solicito remitir, en un plazo de cinco (5) días hábiles, la siguiente documentación: expedientes de contratación completos, actas de recepción de obra, valorizaciones y liquidaciones de los contratos ejecutados.', 'Se agradecerá que la información sea remitida en formato digital ordenado por contrato.'], ...f(P.oci),
    }))],
  },
  // 28 ────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'ARCH-1998-015', area: 'Unidad de Trámite Documentario y Archivo', tipo: 'Acta', remitente: 'Archivo Central', fecha: '1998-06-18', estado: 'Archivado', autor: 'rosa',
    asunto: 'Acta de sesión de Concejo N° 015-1998 (documento histórico escaneado)', glosa: 'Documento histórico digitalizado desde el libro de actas. Sin texto reconocible: pendiente de OCR.',
    docs: [scan('Acta de sesión N° 015-1998 - parte 1', 3, 11), scan('Acta de sesión N° 015-1998 - parte 2', 2, 23)],
  },
  // 29 ────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'RESID-2026-002', area: 'Gerencia de Servicios Públicos y Medio Ambiente', tipo: 'Informe Técnico', remitente: 'Gerencia de Servicios Públicos y Medio Ambiente', fecha: '2026-05-26', estado: 'Activo', autor: 'jorge',
    asunto: 'Plan de manejo de residuos sólidos municipales 2026', glosa: 'Diagnóstico y metas del plan de manejo de residuos: 21.4 toneladas diarias generadas, cobertura de recolección del 87 %.',
    docs: [pdf('Plan de manejo de residuos sólidos 2026', informe({
      titulo: 'INFORME TÉCNICO', num: '019-2026-GSPMA/MDSJ', area: 'Gerencia de Servicios Públicos y Medio Ambiente', a: 'Gerencia Municipal', de: 'Gerencia de Servicios Públicos y Medio Ambiente', asunto: 'Plan de manejo de residuos sólidos municipales 2026', ref: 'Decreto Legislativo N° 1278', fecha: '2026-05-26',
      antecedentes: ['El Decreto Legislativo N° 1278, Ley de Gestión Integral de Residuos Sólidos, y su reglamento, obligan a las municipalidades a formular y ejecutar el plan de manejo de residuos sólidos de su jurisdicción.', 'El plan anterior culminó en 2025. Se requiere su actualización con un nuevo diagnóstico, metas y presupuesto.'],
      analisis: ['El distrito genera 21.4 toneladas diarias de residuos sólidos municipales, con una generación per cápita de 0.62 kg por habitante al día. La composición es de 54 % de residuos orgánicos, 17 % de plásticos, 9 % de papel y cartón, 6 % de vidrio, 4 % de metales y 10 % de otros.', 'La cobertura de recolección alcanza el 87 % de la población y se realiza con dos camiones compactadores y una camioneta, con frecuencia diaria en el casco urbano e interdiaria en las zonas periféricas. La disposición final se efectúa en un relleno sanitario autorizado ubicado a 24 km.', 'Se propone implementar el programa de segregación en la fuente en 1,200 viviendas, instalar veinte puntos de acopio de residuos reciclables, formalizar a los recicladores y ampliar la cobertura al 95 %.'],
      conclusiones: ['Es necesario aprobar el plan de manejo de residuos sólidos 2026 con metas de reciclaje del 12 % y cobertura del 95 %.', 'El presupuesto total del plan es de S/ 486,300.00.'], recomendaciones: ['Aprobar el plan mediante resolución de alcaldía.', 'Adquirir un nuevo camión compactador durante el segundo semestre.', 'Fortalecer las campañas de educación ambiental en las instituciones educativas.'], ...f(P.gspma),
    }))],
  },
  // 30 ────────────────────────────────────────────────────────────
  {
    numeroDocumento: 'PIP-2026-004', area: 'Oficina de Planeamiento y Presupuesto', tipo: 'Informe Técnico', remitente: 'Oficina de Planeamiento y Presupuesto', fecha: '2026-06-09', estado: 'Activo', autor: 'rosa',
    asunto: 'Mejoramiento del servicio de agua potable - perfil de inversión', glosa: 'Perfil del proyecto de inversión para 640 familias del sector Nuevo Horizonte. Monto estimado S/ 2,318,000.00.',
    docs: [
      pdf('Informe técnico del perfil de inversión', informe({
        titulo: 'INFORME TÉCNICO', num: '022-2026-OPP/MDSJ', area: 'Oficina de Planeamiento y Presupuesto', a: 'Gerencia Municipal', de: 'Oficina de Planeamiento y Presupuesto', asunto: 'Evaluación del perfil "Mejoramiento del servicio de agua potable en el sector Nuevo Horizonte"', fecha: '2026-06-09',
        antecedentes: ['El sector Nuevo Horizonte cuenta con 640 familias que se abastecen de agua mediante cisternas, con un costo elevado y una calidad no garantizada, según el diagnóstico de la Gerencia de Desarrollo Social.'],
        analisis: ['El proyecto contempla la construcción de un pozo tubular, una caseta de bombeo, un reservorio elevado de 150 m3, 9.8 km de redes de distribución y 640 conexiones domiciliarias con medidores, por un monto estimado de S/ 2,318,000.00.', 'El horizonte de evaluación es de veinte años. La evaluación social arroja un valor actual neto social positivo de S/ 412,600.00 y una relación beneficio-costo de 1.18, por lo que el proyecto resulta socialmente rentable.'],
        conclusiones: ['El perfil cumple con los contenidos mínimos exigidos por el Sistema Nacional de Programación Multianual y Gestión de Inversiones.', 'El proyecto es viable.'], recomendaciones: ['Declarar la viabilidad del proyecto.', 'Registrar la inversión en el banco de inversiones e incorporarla en la programación multianual.'], ...f(P.opp),
      })),
      pdf('Resolución que declara la viabilidad', resolucion({
        tipo: 'RESOLUCIÓN GERENCIAL', num: '063-2026-GM/MDSJ', fecha: '2026-06-16', visto: ['El Informe Técnico N° 022-2026-OPP/MDSJ de la Oficina de Planeamiento y Presupuesto.'],
        considerandos: ['Que, la unidad formuladora ha elaborado el perfil del proyecto de inversión y la Oficina de Planeamiento y Presupuesto, como unidad evaluadora, ha emitido opinión técnica favorable.'],
        articulos: ['DECLARAR la viabilidad del proyecto de inversión "Mejoramiento del servicio de agua potable en el sector Nuevo Horizonte" por el monto estimado de S/ 2,318,000.00.', 'DISPONER el registro de la inversión y el inicio de la elaboración del expediente técnico.'], ...f(P.gm),
      })),
    ],
  },
];
