package com.municipalidadsanjose.archivo.service.impl;

import com.municipalidadsanjose.archivo.audit.Auditable;
import com.municipalidadsanjose.archivo.dto.rol.RolRequestDTO;
import com.municipalidadsanjose.archivo.dto.rol.RolResponseDTO;
import com.municipalidadsanjose.archivo.entity.Rol;
import com.municipalidadsanjose.archivo.enums.AccionAuditoria;
import com.municipalidadsanjose.archivo.exception.RecursoDuplicadoException;
import com.municipalidadsanjose.archivo.exception.RecursoEnUsoException;
import com.municipalidadsanjose.archivo.exception.RecursoNoEncontradoException;
import com.municipalidadsanjose.archivo.mapper.RolMapper;
import com.municipalidadsanjose.archivo.repository.RolRepository;
import com.municipalidadsanjose.archivo.repository.UsuarioRepository;
import com.municipalidadsanjose.archivo.service.RolService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RolServiceImpl implements RolService {
    private final RolRepository rolRepository;
    private final RolMapper rolMapper;
    private final UsuarioRepository usuarioRepository;

    public RolServiceImpl(RolRepository rolRepository,
                          RolMapper rolMapper,
                          UsuarioRepository usuarioRepository){
        this.rolRepository=rolRepository;
        this.rolMapper=rolMapper;
        this.usuarioRepository=usuarioRepository;
    }

    @Override
    @Transactional
    @Auditable(entidad = "Rol", accion = AccionAuditoria.CREAR)
    public RolResponseDTO crear(RolRequestDTO dto){
        if (rolRepository.existsByNombre(dto.nombre())){
            throw new RecursoDuplicadoException("El rol ya existe");
        }
        Rol rol = rolMapper.toEntity(dto);

       Rol guardado = rolRepository.save(rol);

       return rolMapper.toResponseDTO(guardado);
    }

    @Override
    @Transactional
    @Auditable(entidad = "Rol", accion = AccionAuditoria.MODIFICAR)
    public RolResponseDTO actualizar(UUID id, RolRequestDTO dto) {
        Rol rol = rolRepository.findById(id).orElseThrow(()
                -> new RecursoNoEncontradoException("No se encontro el rol",id));

        if (!rol.getNombre().equals(dto.nombre()) && rolRepository.existsByNombre(dto.nombre())) {
            throw new RecursoDuplicadoException("El rol ya existe");
        }

        rol.setNombre(dto.nombre());
        rol.setDescripcion(dto.descripcion());

        return rolMapper.toResponseDTO(rol);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RolResponseDTO> listarTodos(){
        return rolRepository.findAll().stream()
                .map(rolMapper::toResponseDTO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RolResponseDTO buscarPorID(UUID id){

        Rol rol = rolRepository.findById(id).orElseThrow(()->
                new RecursoNoEncontradoException("Rol no encontrado",id));
        return rolMapper.toResponseDTO(rol);
    }

    @Override
    @Transactional
    @Auditable(entidad = "Rol", accion = AccionAuditoria.ELIMINAR)
    public void eliminar(UUID id){
    if(!rolRepository.existsById(id)){
        throw new RecursoNoEncontradoException("No se encuentra el rol",id);
    }
    if(usuarioRepository.existsByRolId(id)){
        throw new RecursoEnUsoException("No se puede eliminar el rol: hay usuarios asignados a él");
    }
        rolRepository.deleteById(id);

    }
}
