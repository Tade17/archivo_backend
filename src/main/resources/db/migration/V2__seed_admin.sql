-- =========================================================
-- Semilla mínima para poder autenticarse por primera vez.
-- Sin esto no hay forma de crear el primer usuario/rol vía API,
-- porque /api/roles y /api/usuarios ya requieren un JWT válido.
--
-- Password del usuario admin semilla: "CambiarInmediatamente123!"
-- (hash BCrypt, generado con BCryptPasswordEncoder). CAMBIAR esta
-- contraseña (o desactivar este usuario y crear uno nuevo) apenas
-- se despliegue en un ambiente real.
-- =========================================================

INSERT INTO rol (nombre, descripcion)
VALUES ('ADMIN', 'Administrador del sistema, acceso total')
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO usuario (nombre, correo, password_hash, rol_id, activo)
SELECT 'Administrador', 'admin@sanjose.gob.pe',
       '$2a$10$eIKdBe4f83T36iENPuDZI.P.M/oqegXeTU4D8DZlRp5Z6clLkR3ky',
       rol_id, true
FROM rol
WHERE nombre = 'ADMIN'
ON CONFLICT (correo) DO NOTHING;
