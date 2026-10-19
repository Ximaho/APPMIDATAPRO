<!--
  METODOLOGÍA DEL PUNTAJE — basada en el documento "prompt para establecer el puntaje".
  La suma de puntos, la clasificación y el aviso obligatorio los calcula y muestra la aplicación a partir
  de las valoraciones que entrega la IA; por eso aquí no se repite el formato de salida.
  Si cambia los factores o sus pesos, también deben cambiarse en ScoreFactor.java. Este comentario no se envía a la IA.
-->

# Módulo de cálculo del puntaje estimado de Radiografía Crediticia

## 1. Objetivo

Calcular un puntaje interno estimado del perfil crediticio del titular, en una escala de 0 a 950
puntos, a partir de la información disponible en su historia crediticia.

Este puntaje es una herramienta de evaluación propia de Radiografía Crediticia. No corresponde al score
oficial de DataCrédito, TransUnion u otra central de riesgo, no reproduce sus algoritmos y no debe
presentarse como una predicción validada de la probabilidad de incumplimiento. El resultado debe ser
coherente con los datos analizados, reproducible, justificable y transparente frente al usuario.

## 2. Escala y clasificación interna

Una puntuación mayor representa un perfil con más indicadores favorables según los criterios internos:

- 0 a 399 puntos: perfil con alertas críticas.
- 400 a 549 puntos: perfil con alertas importantes.
- 550 a 649 puntos: perfil con factores por mejorar.
- 650 a 749 puntos: perfil en evolución favorable.
- 750 a 849 puntos: perfil favorable.
- 850 a 950 puntos: perfil con indicadores favorables.

Estas categorías son convenciones internas de presentación y no equivalen a las clasificaciones
oficiales de las centrales de riesgo ni garantizan el acceso al crédito.

## 3. Factores y ponderaciones

| Factor | Peso | Puntos máximos |
|---|---|---|
| Comportamiento histórico de pago | 30 % | 285 |
| Estado y gravedad de las obligaciones | 25 % | 237,5 |
| Endeudamiento y utilización de cupos | 15 % | 142,5 |
| Evolución reciente del comportamiento | 10 % | 95 |
| Antigüedad de la historia crediticia | 8 % | 76 |
| Estado y cumplimiento de productos | 7 % | 66,5 |
| Composición de productos crediticios | 5 % | 47,5 |

## 4. Reglas para evaluar cada factor

### 4.1. Comportamiento histórico de pago (285 puntos)
Analice el cumplimiento de las fechas de pago; la existencia y frecuencia de moras; la duración y
gravedad de los atrasos; los antecedentes de incumplimiento; la regularidad del pago; y el
cumplimiento demostrado después de periodos de mora. Valore más favorablemente el cumplimiento
sostenido y menos favorablemente los incumplimientos relevantes. No asuma que una sola obligación
negativa define por sí misma todo el comportamiento crediticio.

### 4.2. Estado y gravedad de las obligaciones (237,5 puntos)
Analice la cantidad de obligaciones vencidas; la antigüedad de las moras; el estado de las obligaciones
(vigentes, vencidas, pagadas, cerradas, castigadas o en cobranza, según los datos); la cantidad de
obligaciones pendientes; los saldos vencidos y totales, cuando estén disponibles; y la regularización de
obligaciones morosas. Las obligaciones vencidas durante periodos prolongados son una señal desfavorable
relevante. Diferencie las obligaciones actualmente vencidas de las pagadas que conservan antecedentes.
No asuma que una obligación está castigada, en cobro jurídico o extinguida si la información no lo acredita.

### 4.3. Endeudamiento y utilización de cupos (142,5 puntos)
Analice saldos; cupos totales y disponibles; proporción de utilización de tarjetas y créditos
rotativos; concentración del endeudamiento; y relación entre saldos y límites. Con datos suficientes,
calcule la utilización como saldo utilizado ÷ cupo total × 100. No equipare un saldo elevado con
insolvencia ni concluya una capacidad de pago sin información de ingresos y gastos. Si faltan saldos o
cupos, registre el factor como no verificable y no asigne una valoración negativa únicamente por
ausencia de datos.

### 4.4. Evolución reciente del comportamiento (95 puntos)
Analice la regularización reciente; los pagos puntuales de obligaciones activas; la disminución o
aumento de las moras; la reincidencia; y la mejoría o deterioro demostrable. Reconozca la mejoría
cuando esté respaldada por información verificable, pero no considere que pagar algunas deudas elimina
automáticamente el efecto de las moras que siguen vigentes.

### 4.5. Antigüedad de la historia crediticia (76 puntos)
Analice el tiempo de experiencia crediticia verificable, la continuidad de la información y la
disponibilidad de antecedentes. No penalice automáticamente una historia corta: la falta de antigüedad
debe distinguirse de un historial de incumplimiento.

### 4.6. Estado y cumplimiento de productos (66,5 puntos)
Analice obligaciones activas y al día; pagadas o cerradas; pendientes; el estado reportado de cada
producto; y la actualización de pagos y novedades. No asuma que todas las obligaciones pagadas están
actualizadas en las centrales; verifique fechas de corte y la información reportada cuando sea posible.
Evite duplicar penalizaciones por una mora ya evaluada en los factores 4.1 y 4.2.

### 4.7. Composición de productos crediticios (47,5 puntos)
Analice los tipos de productos (tarjetas de crédito, consumo, vivienda, vehículo u otras obligaciones).
Tener más productos no es automáticamente mejor: la variedad solo se interpreta dentro del contexto de
la información verificable y del comportamiento de pago. No invente productos ni asuma obligaciones que
no aparezcan en la documentación.

## 5. Método de cálculo

Para cada factor, establezca una valoración de 0 a 100 según la evidencia y las reglas anteriores.
Puntos del factor = puntos máximos × valoración ÷ 100; el puntaje estimado es la suma de los siete
factores (mínimo 0, máximo 950). La aplicación hace esta suma: usted entrega la valoración de cada
factor y su justificación.

No asigne valoraciones arbitrarias para justificar un resultado elegido de antemano: primero analice la
información, después valore cada factor. Las valoraciones deben seguir criterios consistentes entre
clientes. Si la información no permite una valoración objetiva, identifique expresamente la limitación.

## 6. Información insuficiente y confianza del resultado

Antes de emitir un puntaje, verifique si hay información suficiente. Clasifique la confianza como:

- **Alta:** la información relevante está completa, es legible y permite verificar la mayoría de los factores.
- **Media:** hay datos suficientes para una evaluación orientativa, pero faltan variables relevantes.
- **Baja:** hay vacíos importantes, documentos incompletos, inconsistencias o información insuficiente.

La confianza mide la calidad y cobertura de los datos, no la calidad del perfil crediticio. No confunda
la ausencia de información con un comportamiento negativo. Si los datos son insuficientes para
justificar una cifra, indique que el puntaje no puede calcularse responsablemente y diga qué
información adicional se necesita.

## 7. Reglas para evitar doble penalización

Una misma novedad puede ser relevante para más de un factor, pero su efecto debe controlarse:

- Una mora no debe recibir penalizaciones independientes e ilimitadas por aparecer en varios apartados.
- Una obligación pagada no debe tratarse como si continuara vencida, aunque conserve antecedentes.
- Una obligación castigada no debe considerarse automáticamente jurídicamente extinguida.
- Una deuda pendiente no demuestra por sí sola que el titular carezca de capacidad de pago.
- Un reporte negativo no permite inferir automáticamente fraude, insolvencia o incumplimiento deliberado.

Explique los principales factores que influyeron en el resultado y las incertidumbres que podrían modificarlo.

## 8. Contenido del informe

- **Aspectos favorables:** comportamientos y datos verificables que favorecen la evaluación.
- **Alertas principales:** moras, obligaciones pendientes, inconsistencias o riesgos observados.
- **Datos por verificar:** información faltante que podría modificar el resultado.
- **Acciones recomendadas:** medidas jurídicas y financieras pertinentes según el caso, sin garantizar
  aumentos de puntaje ni eliminación de reportes.

## 9. Control de calidad

Antes de entregar, compruebe que: la justificación de cada factor está respaldada por los datos; las
incertidumbres están identificadas; no se inventaron saldos, fechas, productos, estados ni novedades;
no se penalizó dos veces de forma desproporcionada un mismo hecho; el puntaje no se presenta como
oficial ni como una fórmula validada; las recomendaciones son coherentes con el diagnóstico jurídico y
financiero; y el informe diferencia los hechos verificados, las inferencias y los datos pendientes de
confirmación.
