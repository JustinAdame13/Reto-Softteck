# Tienda Inteligente para Retail — Backend

API REST con Spring Boot 4 + PostgreSQL + Flyway. Base: `http://localhost:8080/api/v1`.

## Cómo correrlo

1. Crea la base de datos `tienda_inteligente` en PostgreSQL.
2. Copia `.env.example` a `.env` y pon tu contraseña de PostgreSQL (el `.env` no se sube a git).
3. Corre `PlsApplication`. Flyway crea las tablas (V1) y la configuración inicial (V2).
4. (Opcional) Pon `IA_ANTHROPIC_API_KEY`, `IA_OPENAI_API_KEY` y/o `IA_GEMINI_API_KEY` en `.env` y prende el proveedor con
   `PATCH /configuracion/ia` → `{"proveedorIa": "ANTHROPIC"}` (opcional `"modeloIa"`). Apagar: `{"proveedorIa": "NINGUNO"}`.
   Sin llaves, todo funciona con Ayax sin IA (modelo `BASE`).

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
| `configuracion` | `GET/PUT /configuracion`, `PATCH /configuracion/ia`, `GET /configuracion/ia/proveedores`, `GET /configuracion/ia/estado` |
| `producto` | CRUD `/productos`, `/productos/lote`, `/productos/sku/{sku}`, `/productos/categorias` |
| `empleado` | CRUD `/empleados`, `/empleados/lote`, `PATCH /empleados/{id}/activo` |
| `contexto` | `/contexto`, `PUT /contexto/{fecha}`, `/contexto/lote`, `/contexto/generar` |
| `venta` | `POST /ventas` (ticket), `/ventas/lote`, `/ventas/resumen`, `/ventas/records`, `/ventas/ticket/{t}` |
| `inventario` | `/inventario/movimientos`, `/estado`, `/alertas`, `/reabasto`, `/perdidas` |
| `promocion` | CRUD `/promociones`, `PATCH /{id}/estado`, `POST /generar`, `GET /{id}/resultado` |
| `prediccion` | `POST /predicciones/generar`, `/predicciones`, `/precision`, `/comparar` |
| `plantilla` | CRUD `/plantillas`, `/semana`, `/cobertura`, `POST /recomendar` |
| `dashboard` | `GET /dashboard/resumen` |
| `ia` | `GET /ia/estado` (alias de `/configuracion/ia/estado`) |

## Algoritmos (para explicar a los jueces)

- **Ayax** (`MotorAyax`, se guarda como modelo `BASE`): el algoritmo propio de pronóstico de demanda. Funciona solo, sin IA.
  - **Dos métodos según el producto.** *Ayax-Estacional* para productos de venta regular: descomposición multiplicativa (día de la semana, quincena, eventos/festivos, temporada) + suavizamiento exponencial del nivel + tendencia acotada. *Ayax-Intermitente* para productos de baja rotación (muchos días en cero): método de Croston con corrección SBA, que no sobreestima como el estacional.
  - **Autocalibración por producto.** Prueba hasta 10 configuraciones (velocidad de adaptación, fuerza del shrinkage y Croston si aplica) con un examen sobre datos conocidos: 3 veces entrena hasta cierto día y pronostica las 2 semanas siguientes (*rolling origin*). Gana la de menor WAPE + |sesgo|, y solo reemplaza a la configuración de fábrica si mejora al menos 10 % (para no sobreajustar al ruido). `POST /predicciones/generar` regresa en `calibracionAyax` el método, los parámetros y el error de cada producto contra la configuración fija.
  - **Solo aprende de días limpios.** Excluye días con promoción (inflan la demanda y el motor de promociones agrega su efecto después) y rachas de 3+ días en cero en productos que venden 3+ diarios (probable quiebre de stock).
  - Intervalo de 80 % con el error de pronóstico a un paso. Productos nuevos usan el patrón de su categoría.
- **Ayax + IA** (`MotorAyaxIa`, se guarda como modelo `IA`): recibe el pronóstico de Ayax, el historial y el calendario, y ajusta. No es chatbot. El ajuste (y su límite superior) no puede salir de 0.5×–2× de Ayax; si un lote falla, ese lote se queda con Ayax sin IA. Proveedores: Anthropic, OpenAI o Gemini, intercambiables en caliente.
- **Inventario** (`AnalisisInventario`): stock de seguridad `Z·σ·√leadTime` (95 %), punto de reorden, días de cobertura, semáforo y caducidad estimada (última ENTRADA + vida útil).
- **Promociones** (`MotorPromociones`): detecta POR_CADUCAR, SOBRESTOCK, BAJA_ROTACION y TEMPORADA; prueba descuentos y elige el que maximiza la ganancia incremental respetando margen mínimo y descuento máximo. La elasticidad se aprende de promociones pasadas.
- **Personal** (`PlantillaService.recomendar`): tickets por día/hora ÷ transacciones por empleado por hora.

## Motor externo (si el compañero de algoritmos usa Python)

Con `MOTOR_TIPO=externo`, el backend hace `POST {MOTOR_URL}/predecir` con `EntradaPrediccion`
(`fechaInicio`, `dias`, `productos[]` con `ventasDiarias[]` y `diasConPromocion[]`, `contexto[]`) y espera una lista de
`ResultadoPrediccion` (`productoId`, `fechaObjetivo`, `cantidadPredicha`, `limiteInferior`, `limiteSuperior`).
Si el externo falla, se usa Ayax.
