package com.municipalidadsanjose.archivo.controller;

import com.municipalidadsanjose.archivo.dto.auth.LoginRequestDTO;
import com.municipalidadsanjose.archivo.dto.auth.LoginResponseDTO;
import com.municipalidadsanjose.archivo.security.JwtService;
import com.municipalidadsanjose.archivo.security.UsuarioPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO dto) {
        // Si las credenciales son inválidas o el usuario está inactivo, esto lanza
        // BadCredentialsException/DisabledException, manejadas por GlobalExceptionHandler.
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.correo(), dto.password()));

        UsuarioPrincipal principal = (UsuarioPrincipal) authentication.getPrincipal();
        String token = jwtService.generarToken(principal);

        return ResponseEntity.ok(new LoginResponseDTO(
                token,
                principal.getId(),
                principal.getUsuario().getNombre(),
                principal.getUsername(),
                principal.getUsuario().getRol().getNombre()));
    }
}
