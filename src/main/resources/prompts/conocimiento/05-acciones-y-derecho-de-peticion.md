<!--
  ACCIONES POR ENTIDAD Y DERECHO DE PETICIÓN — cuándo la IA recomienda enviar el derecho de petición de la
  plataforma y cómo redactar los pasos con cada entidad. El texto del derecho de petición NO está aquí: es el
  formato "05. DERECHO DE PETICIÓN (no abogado)" y lo genera la aplicación (PetitionService.java).
  Este comentario no se envía a la IA.
-->

# Acciones por entidad y derecho de petición

## Qué contiene el derecho de petición de la plataforma

La plataforma genera un derecho de petición (art. 23 de la Constitución) dirigido a la entidad que reporta,
con el nombre y la cédula del titular. Solicita a la entidad:

1. Si existen reportes con ella y, en ese caso: si las obligaciones están activas o canceladas y desde cuándo,
   la fecha del reporte inicial ante las centrales, el saldo actual, el valor inicial, la tasa de interés y el
   total de intereses aplicados (incluidos honorarios).
2. Copia auténtica del documento que respalda la obligación (firma, huella o certificado de autenticidad).
3. Si hubo reporte negativo, su fecha inicial y ante qué central se hizo.
4. Copia de la comunicación previa al reporte con firma de recibido o certificado de entrega (art. 12, Ley 1266).
5. Copia de la autorización firmada para el reporte.
6. La eliminación del dato si el reporte negativo supera 8 años (caducidad, Ley 2157 de 2021).
7. Si el reporte no cumple cualquiera de los requisitos anteriores, ordenar su eliminación en 48 horas.

## Cuándo marcar `petition: true`

Recomiende el derecho de petición a una entidad cuando se cumpla al menos una de estas situaciones:

- La entidad tiene un reporte negativo vigente (mora actual o dato negativo en permanencia) a nombre del titular.
- El titular pagó una obligación con esa entidad, pero sigue apareciendo reportada o no sabe cómo aparece.
- El titular no reconoce la obligación o sospecha suplantación.
- El titular considera el reporte incorrecto o injusto, o no recuerda haber recibido la comunicación previa.
- La mora es antigua (cercana o superior a 8 años) y podría haber caducado el dato.
- Es una cartera cedida o comprada (casa de cobranza, cesionaria) y no hay claridad sobre los soportes.

No lo recomiende cuando la obligación está al día y sin antecedentes negativos, ni para entidades que solo
aparecen como huellas de consulta. Un derecho de petición por entidad es suficiente.

## Cómo redactar los pasos con cada entidad

- Empiece con la acción inmediata más útil. Si recomienda el derecho de petición, un primer paso típico es:
  "Descargue el derecho de petición incluido, complételo, fírmelo y radíquelo ante [entidad] por sus canales
  oficiales (oficina, PQRS en su página web o correo de servicio al cliente); guarde el número de radicado."
- Indique el plazo de respuesta: 15 días hábiles para reclamos, prorrogables por 8 días hábiles (art. 16).
- Indique qué hacer según la respuesta: si la entidad no acredita la comunicación previa, la autorización o los
  soportes, puede pedir la eliminación del dato; si no responde de fondo o en el plazo, puede presentar queja ante
  la Superintendencia Financiera (entidades vigiladas, como bancos) o la Superintendencia de Industria y Comercio
  (otras entidades), o una acción de tutela por habeas data (ver jurisprudencia).
- Si hay una deuda vigente, incluya un paso financiero concreto (por ejemplo, solicitar un acuerdo de pago por
  escrito y conservar el paz y salvo al terminar).
- Use máximo 5 pasos por entidad, en imperativo de "usted", claros y verificables.
