-- V3__configuracion_proveedor_ia.sql
-- Reemplaza el booleano ia_habilitada por el proveedor de IA.
-- NINGUNO equivale a la IA apagada, asi no existe el estado
-- inconsistente de "habilitada sin proveedor".
-- modelo_ia NULL = usar el modelo por defecto del proveedor,
-- resuelto en application.properties.

ALTER TABLE configuracion
    ADD COLUMN proveedor_ia VARCHAR(20) NOT NULL DEFAULT 'NINGUNO',
    ADD COLUMN modelo_ia    VARCHAR(80);

-- Conservar lo que ya estuviera prendido
UPDATE configuracion
SET proveedor_ia = 'ANTHROPIC'
WHERE COALESCE(ia_habilitada, FALSE) = TRUE;

ALTER TABLE configuracion
DROP COLUMN ia_habilitada;

ALTER TABLE configuracion
    ADD CONSTRAINT chk_configuracion_proveedor_ia
        CHECK (proveedor_ia IN ('NINGUNO', 'ANTHROPIC', 'OPENAI', 'GEMINI'));