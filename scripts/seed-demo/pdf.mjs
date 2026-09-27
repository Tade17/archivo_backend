// Generador mínimo de PDF (sin dependencias) para los datos de prueba.
// Produce PDFs con texto real (Helvetica, WinAnsi) y paginación automática,
// y también "escaneos" simulados sin capa de texto.

const W = 595;
const H = 842;
const MARGIN = 62;
const TOP = H - 70;
const BOTTOM = 84;

const ASCII = { '‘': "'", '’': "'", '“': '"', '”': '"', '–': '-', '—': '-', '…': '...', '•': '-' };
const clean = (s) => s.replace(/[‘’“”–—…•]/g, (c) => ASCII[c]);
const esc = (s) => clean(s).replace(/\\/g, '\\\\').replace(/\(/g, '\\(').replace(/\)/g, '\\)');

function wrap(text, max) {
  const lines = [];
  let line = '';
  for (const word of clean(text).split(/\s+/)) {
    if ((line + ' ' + word).trim().length > max) {
      lines.push(line);
      line = word;
    } else {
      line = (line + ' ' + word).trim();
    }
  }
  if (line) lines.push(line);
  return lines;
}

const centerX = (text, size) => Math.max(MARGIN, (W - clean(text).length * size * 0.5) / 2);

/** Reparte los bloques en páginas. Cada línea: {font, size, x, y, text}. */
function layout(blocks) {
  const pages = [[]];
  let y = TOP;
  const line = (font, size, x, text, lead) => {
    if (y < BOTTOM) {
      pages.push([]);
      y = TOP;
    }
    pages[pages.length - 1].push({ font, size, x, y, text });
    y -= lead;
  };
  for (const b of blocks) {
    switch (b.t) {
      case 'center':
        line(b.bold ? 2 : 1, b.size ?? 11, centerX(b.text, b.size ?? 11), b.text, (b.size ?? 11) + 6);
        break;
      case 'h':
        y -= 6;
        line(2, 11.5, MARGIN, b.text, 18);
        break;
      case 'p':
        for (const l of wrap(b.text, 84)) line(1, 11, MARGIN, l, 15);
        y -= 6;
        break;
      case 'li':
        wrap(b.text, 78).forEach((l, i) => line(1, 11, MARGIN + (i ? 26 : 12), (i ? '' : '- ') + l, 15));
        y -= 2;
        break;
      case 'kv':
        wrap(b.value, 66).forEach((l, i) => {
          if (i === 0) line(2, 11, MARGIN, b.key, 0);
          line(1, 11, MARGIN + 92, l, 15);
        });
        break;
      case 'space':
        y -= b.h ?? 14;
        break;
      default:
        break;
    }
  }
  return pages;
}

function assemble(pageStreams) {
  const objects = [];
  const n = pageStreams.length;
  objects[1] = Buffer.from('<< /Type /Catalog /Pages 2 0 R >>');
  const kids = pageStreams.map((_, i) => `${6 + 2 * i} 0 R`).join(' ');
  objects[2] = Buffer.from(`<< /Type /Pages /Kids [${kids}] /Count ${n} >>`);
  const font = (name) => Buffer.from(`<< /Type /Font /Subtype /Type1 /BaseFont /${name} /Encoding /WinAnsiEncoding >>`);
  objects[3] = font('Helvetica');
  objects[4] = font('Helvetica-Bold');
  objects[5] = font('Helvetica-Oblique');
  pageStreams.forEach((stream, i) => {
    const data = Buffer.from(stream, 'latin1');
    objects[6 + 2 * i] = Buffer.from(
      `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${W} ${H}] /Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R >> >> /Contents ${7 + 2 * i} 0 R >>`,
    );
    objects[7 + 2 * i] = Buffer.concat([
      Buffer.from(`<< /Length ${data.length} >>\nstream\n`),
      data,
      Buffer.from('\nendstream'),
    ]);
  });
  let out = Buffer.from('%PDF-1.4\n%\xE2\xE3\xCF\xD3\n', 'latin1');
  const offsets = [];
  for (let i = 1; i < objects.length; i++) {
    offsets[i] = out.length;
    out = Buffer.concat([out, Buffer.from(`${i} 0 obj\n`), objects[i], Buffer.from('\nendobj\n')]);
  }
  const xref = out.length;
  let table = `xref\n0 ${objects.length}\n0000000000 65535 f \n`;
  for (let i = 1; i < objects.length; i++) table += `${String(offsets[i]).padStart(10, '0')} 00000 n \n`;
  table += `trailer\n<< /Size ${objects.length} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.concat([out, Buffer.from(table)]);
}

/** PDF de texto. `codigo` aparece en el pie de cada página. */
export function textPdf(blocks, codigo) {
  const pages = layout(blocks);
  const streams = pages.map((lines, index) => {
    let s = '';
    for (const l of lines) s += `BT /F${l.font} ${l.size} Tf ${l.x.toFixed(1)} ${l.y.toFixed(1)} Td (${esc(l.text)}) Tj ET\n`;
    s += `0.6 G 0.5 w ${MARGIN} 62 m ${W - MARGIN} 62 l S\n`;
    s += `BT /F3 8 Tf ${MARGIN} 48 Td (${esc(`Municipalidad Distrital de San José  -  ${codigo}  -  Página ${index + 1} de ${pages.length}`)}) Tj ET\n`;
    return s;
  });
  return assemble(streams);
}

/** "Escaneo" simulado: hoja con barras grises y sin capa de texto (no se puede buscar). */
export function scanPdf(pageCount, seed = 7) {
  let r = seed;
  const rnd = () => ((r = (r * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
  const streams = [];
  for (let p = 0; p < pageCount; p++) {
    let s = '0.93 g 0 0 595 842 re f\n0.75 G 1.5 w 30 30 535 782 re S\n0.25 g\n';
    for (let y = 760; y > 110; y -= 17) {
      if (rnd() < 0.12) continue;
      const x = MARGIN + (rnd() < 0.15 ? 24 : 0);
      s += `${x} ${y} ${(W - 2 * MARGIN - (x - MARGIN)) * (0.55 + rnd() * 0.45)} 6 re f\n`;
    }
    s += '0.45 G 1 w 380 100 m 520 100 l S\n';
    streams.push(s);
  }
  return assemble(streams);
}
