# Tienda Inteligente para Retail — Backend

API REST con Spring Boot 4 + PostgreSQL + Flyway. Base: `http://localhost:8080/api/v1`.

## Cómo correrlo

1. Crea la base de datos `tienda_inteligente` en PostgreSQL.
2. Copia `.env.example` a `.env` y pon tu contraseña de PostgreSQL (el `.env` no se sube a git).
3. Corre `PlsApplication`. Flyway crea las tablas (V1) y la configuración inicial (V2).
4. (Opcional) Pon `IA_API_KEY` en `.env` para habilitar la IA. Sin key, todo funciona con el algoritmo BASE.

## Orden para cargar datos de demo

1. `POST /contexto/generar?anio=2026` (también el año anterior si hay históricos de ese año)
2. `POST /productos/lote`
3. `POST /empleados/lote`
4. `POST /ventas/lote` (históricos; **no toca el stock**)
5. `POST /inventario/movimientos/lote` (entradas/mermas históricas; no toca el stock por defecto)
6. `POST /predicciones/generar` con `{"dias": 14, "modelo": "AMBOS", "fechaInicio": "<hace 14 días>"}` → backtesting para que `/predicciones/precision` tenga datos.
7. `POST /predicciones/generar` con `{"dias": 7}` → pronóstico real.
8. `POST /promociones/generar` y `POST /plantillas/recomendar?guardar=true`.

## Módulos

| Paquete | Endpoints principales |
|---|---|
| `configuracion` | `GET/PUT /configuracion`, `PATCH /configuracion/ia` |
| `producto` | CRUD `/productos`, `/productos/lote`, `/productos/sku/{sku}`, `/productos/categorias` |
| `empleado` | CRUD `/empleados`, `/empleados/lote`, `PATCH /empleados/{id}/activo` |
| `contexto` | `/contexto`, `PUT /contexto/{fecha}`, `/contexto/lote`, `/contexto/generar` |
| `venta` | `POST /ventas` (ticket), `/ventas/lote`, `/ventas/resumen`, `/ventas/records`, `/ventas/ticket/{t}` |
| `inventario` | `/inventario/movimientos`, `/estado`, `/alertas`, `/reabasto`, `/perdidas` |
| `promocion` | CRUD `/promociones`, `PATCH /{id}/estado`, `POST /generar`, `GET /{id}/resultado` |
| `prediccion` | `POST /predicciones/generar`, `/predicciones`, `/precision`, `/comparar` |
| `plantilla` | CRUD `/plantillas`, `/semana`, `/cobertura`, `POST /recomendar` |
| `dashboard` | `GET /dashboard/resumen` |
| `ia` | `GET /ia/estado` |

## Algoritmos (para explicar a los jueces)

- **Predicción BASE** (`MotorPrediccionBase`): descomposición multiplicativa (día de la semana, quincena, eventos/festivos, temporada) + suavizamiento exponencial del nivel + tendencia acotada. Intervalo de 80 % con el error de pronóstico a un paso. Productos nuevos usan el patrón de su categoría.
- **IA opcional** (`MotorPrediccionIa`): recibe el pronóstico BASE, el historial y el calendario, y ajusta. No es chatbot. Si falla, se queda el BASE.
- **Inventario** (`AnalisisInventario`): stock de seguridad `Z·σ·√leadTime` (95 %), punto de reorden, días de cobertura, semáforo y caducidad estimada (última ENTRADA + vida útil).
- **Promociones** (`MotorPromociones`): detecta POR_CADUCAR, SOBRESTOCK, BAJA_ROTACION y TEMPORADA; prueba descuentos y elige el que maximiza la ganancia incremental respetando margen mínimo y descuento máximo. La elasticidad se aprende de promociones pasadas.
- **Personal** (`PlantillaService.recomendar`): tickets por día/hora ÷ transacciones por empleado por hora.

## Motor externo (si el compañero de algoritmos usa Python)

Con `MOTOR_TIPO=externo`, el backend hace `POST {MOTOR_URL}/predecir` con `EntradaPrediccion`
(`fechaInicio`, `dias`, `productos[]` con `ventasDiarias[]`, `contexto[]`) y espera una lista de
`ResultadoPrediccion` (`productoId`, `fechaObjetivo`, `cantidadPredicha`, `limiteInferior`, `limiteSuperior`).
Si el externo falla, se usa el motor interno.
