package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.dto.expediente.*;
import com.municipalidadsanjose.archivo.exception.*;
import com.municipalidadsanjose.archivo.security.UsuarioPrincipal;
import com.municipalidadsanjose.archivo.service.*;
import com.municipalidadsanjose.archivo.storage.FileStorageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.*;
import java.util.*;

/** Consultas de la mesa de archivo; las escrituras reutilizan los servicios auditados. */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {
  private final JdbcTemplate db;
  private final ExpedienteService expedientes;
  private final DocumentoDigitalService documentos;
  private final FileStorageService storage;
  public WorkspaceController(JdbcTemplate db, ExpedienteService e, DocumentoDigitalService d, FileStorageService s) {
    this.db=db; expedientes=e; documentos=d; storage=s;
  }
  private static final String JOINS = """
    FROM expediente e JOIN area_responsable a ON a.area_id=e.area_destino_id
    JOIN tipo_documental t ON t.tipo_id=e.tipo_id JOIN estado_expediente es ON es.estado_id=e.estado_id
    """;
  private static final String COLUMNS = """
    e.expediente_id AS id, e.codigo_unico AS "codigoUnico",e.numero_documento AS "numeroDocumento",
    e.numero_tramite AS "numeroTramite",e.anio_ingreso AS "anioIngreso",e.remitente,e.asunto,e.glosa,
    e.area_destino_id AS "areaDestinoId",a.nombre AS "areaDestinoNombre",e.tipo_id AS "tipoId",t.nombre AS "tipoNombre",
    e.estado_id AS "estadoId",es.nombre AS "estadoNombre",e.fecha_documento AS "fechaDocumento",
    e.fecha_registro AS "fechaRegistro",
    (SELECT d.documento_id FROM documento_digital d WHERE d.expediente_id=e.expediente_id ORDER BY d.fecha_digitalizacion,d.documento_id LIMIT 1) AS "documentoId",
    (SELECT d.nombre_archivo FROM documento_digital d WHERE d.expediente_id=e.expediente_id ORDER BY d.fecha_digitalizacion,d.documento_id LIMIT 1) AS "documentoNombre",
    (SELECT count(*) FROM documento_digital d WHERE d.expediente_id=e.expediente_id) AS "totalDocumentos"
    """;
  private Map<String,Object> page(String columns,String from,List<Object> args,int page,int size,String order) {
    page=Math.max(page,0); size=Math.max(1,Math.min(size,100));
    long total=db.queryForObject("SELECT count(*) "+from,Long.class,args.toArray());
    var values=new ArrayList<>(args); values.add(size); values.add(page*size);
    return Map.of("contenido",db.queryForList("SELECT "+columns+" "+from+" ORDER BY "+order+" LIMIT ? OFFSET ?",values.toArray()),
      "pagina",page,"tamano",size,"totalElementos",total,"totalPaginas",(total+size-1)/size);
  }
  private void equal(StringBuilder where,List<Object> args,Map<String,String> q,String key,String field) {
    if(q.containsKey(key)&&!q.get(key).isBlank()){where.append(" AND ").append(field).append("=?");args.add(q.get(key));}
  }
  @GetMapping("/buscar")
  public Map<String,Object> search(@RequestParam Map<String,String> q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="4") int size) {
    var where=new StringBuilder(JOINS+" WHERE true");var args=new ArrayList<Object>();
    String text=q.getOrDefault("texto","").trim();
    if(!text.isEmpty()){
      where.append(" AND (unaccent(concat_ws(' ',e.codigo_unico,e.numero_documento,e.remitente,e.asunto,e.glosa)) ILIKE unaccent(?) OR EXISTS(SELECT 1 FROM documento_digital d WHERE d.expediente_id=e.expediente_id AND d.ocr_tsv @@ websearch_to_tsquery('spanish',?)))");
      args.add("%"+text+"%");args.add(text);
    }
    equal(where,args,q,"codigo","e.codigo_unico");
    equal(where,args,q,"areaDestinoId","e.area_destino_id::text");equal(where,args,q,"tipoId","e.tipo_id::text");
    equal(where,args,q,"cajaId","e.caja_id::text");equal(where,args,q,"anio","extract(year from e.fecha_documento)::text");
    for(String key:List.of("desde","hasta"))if(q.containsKey(key)&&!q.get(key).isBlank()){
      where.append(" AND e.fecha_registro::date ").append(key.equals("desde")?">=":"<=").append(" ?::date");args.add(q.get(key));
    }
    return page(COLUMNS,where.toString(),args,page,size,"e.fecha_registro DESC,e.expediente_id");
  }
  @GetMapping("/expedientes/{id}")
  public Map<String,Object> detail(@PathVariable UUID id) {
    var rows=db.queryForList("SELECT "+COLUMNS+" "+JOINS+" WHERE e.expediente_id=?",id);
    if(rows.isEmpty())throw new RecursoNoEncontradoException("Expediente",id);
    return rows.get(0);
  }
  @GetMapping("/catalogos")
  public Map<String,Object> catalogs(){
    return Map.of(
      "areas",db.queryForList("SELECT area_id AS id,nombre FROM area_responsable ORDER BY nombre"),
      "tipos",db.queryForList("SELECT tipo_id AS id,nombre FROM tipo_documental ORDER BY nombre"),
      "roles",db.queryForList("SELECT rol_id AS id,nombre FROM rol ORDER BY nombre"));
  }
  public record Reception(@NotBlank @Size(max=50) String numeroDocumento,@NotBlank @Size(max=255) String remitente,
    @NotNull UUID areaDestinoId,@NotNull UUID tipoId,@NotNull LocalDate fechaDocumento,
    @NotBlank @Size(max=500) String asunto,String glosa) {}
  @PostMapping("/recepcion")
  @PreAuthorize("hasAnyRole('ADMIN','GESTOR_DOCUMENTAL')")
  @Transactional
  public ExpedienteResponseDTO receive(@Valid @RequestBody Reception r,@AuthenticationPrincipal UsuarioPrincipal user){
    UUID state=db.queryForObject("SELECT estado_id FROM estado_expediente WHERE nombre='Registrado'",UUID.class);
    int anio=Year.now().getValue();
    int correlativo=db.queryForObject("""
      INSERT INTO correlativo_expediente(anio,ultimo_numero) VALUES (?,1)
      ON CONFLICT(anio) DO UPDATE SET ultimo_numero=correlativo_expediente.ultimo_numero+1
      RETURNING ultimo_numero
      """,Integer.class,anio);
    String sufijo=String.format("%06d",correlativo);
    String numeroTramite="TRM-"+anio+"-"+sufijo;
    String code="EXP-"+anio+"-"+sufijo;
    var result=expedientes.crear(new ExpedienteRequestDTO(code,r.numeroDocumento(),r.remitente(),r.areaDestinoId(),r.tipoId(),state,r.fechaDocumento(),r.asunto(),r.glosa(),null,null,user.getId(),Set.of()));
    db.update("UPDATE expediente SET numero_tramite=?,anio_ingreso=? WHERE expediente_id=?",numeroTramite,anio,result.id());
    return result;
  }
  @PutMapping("/expedientes/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','GESTOR_DOCUMENTAL')")
  @Transactional
  public Map<String,Object> edit(@PathVariable UUID id,@Valid @RequestBody Reception r,@AuthenticationPrincipal UsuarioPrincipal user){
    var original=detail(id);
    db.update("""
      UPDATE expediente SET numero_documento=?,remitente=?,area_destino_id=?,tipo_id=?,fecha_documento=?,asunto=?,glosa=?,fecha_actualizacion=now() WHERE expediente_id=?
      """,r.numeroDocumento(),r.remitente(),r.areaDestinoId(),r.tipoId(),r.fechaDocumento(),r.asunto(),r.glosa(),id);
    audit(user.getId(),"Expediente",id,"MODIFICAR");
    return detail(id);
  }
  private void audit(UUID user,String entity,UUID id,String action){
    db.update("INSERT INTO auditoria(usuario_id,entidad_afectada,entidad_id,accion) VALUES (?,?,?,?)",user,entity,id,action);
  }
  @GetMapping("/documentos/{id}/vista")
  @Transactional
  public ResponseEntity<Resource> preview(@PathVariable UUID id,@AuthenticationPrincipal UsuarioPrincipal user){
    var d=documentos.buscarPorId(id);var resource=storage.cargarComoRecurso(d.rutaAlmacenamiento());
    audit(user.getId(),"DocumentoDigital",id,"CONSULTAR");
    return ResponseEntity.ok().contentType(MediaType.parseMediaType(d.tipoMime()))
      .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
      .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(d.nombreArchivo()).build().toString()).body(resource);
  }
  @GetMapping("/auditoria")
  @PreAuthorize("hasRole('ADMIN')")
  public Map<String,Object> auditLog(@RequestParam Map<String,String> q,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){
    var where=new StringBuilder("FROM auditoria a JOIN usuario u ON u.usuario_id=a.usuario_id WHERE true");var args=new ArrayList<Object>();
    equal(where,args,q,"accion","a.accion");equal(where,args,q,"modulo","a.entidad_afectada");
    if(q.containsKey("usuario")&&!q.get("usuario").isBlank()){where.append(" AND unaccent(u.nombre) ILIKE unaccent(?)");args.add("%"+q.get("usuario")+"%");}
    for(String key:List.of("desde","hasta"))if(q.containsKey(key)&&!q.get(key).isBlank()){where.append(" AND a.fecha::date ").append(key.equals("desde")?">=":"<=").append(" ?::date");args.add(q.get(key));}
    return page("a.auditoria_id AS id,u.nombre AS usuario,a.entidad_afectada AS modulo,a.entidad_id AS recurso,a.accion,a.fecha",where.toString(),args,page,size,"a.fecha DESC,a.auditoria_id");
  }
  @GetMapping("/auditoria/resumen")
  @PreAuthorize("hasRole('ADMIN')")
  public Map<String,Object> auditSummary(){
    return db.queryForMap("SELECT count(*) AS eventos,count(*) FILTER(WHERE accion='DESCARGAR') AS descargas,count(*) FILTER(WHERE accion='ELIMINAR') AS eliminaciones,count(*) FILTER(WHERE accion='CONSULTAR') AS consultas FROM auditoria WHERE fecha::date=CURRENT_DATE");
  }
}
