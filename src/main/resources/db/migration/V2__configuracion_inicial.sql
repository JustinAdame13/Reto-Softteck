-- =====================================================================
-- V2: Fila unica de configuracion con valores por defecto.
-- Asi la app arranca con configuracion valida sin codigo extra;
-- despues se cambia con PUT /api/v1/configuracion.
-- =====================================================================
INSERT INTO configuracion (
    id, nombre_sucursal, zona, tamano,
    hora_apertura, hora_cierre,
    ia_habilitada, margen_minimo_promo, descuento_maximo,
    meses_historico, transacciones_por_empleado_hora
) VALUES (
    1, 'Sucursal Centro', 'Torreon', 'MEDIANA',
    '08:00', '22:00',
    FALSE, 0.10, 30.00,
    6, 20
);
