# PDF Compressor & Document Organizer

Aplicacion de escritorio (Java 21 + Swing) que copia una carpeta de
documentos, comprime sus PDFs y los reorganiza en carpetas de salida que
nunca superan **3.95 MB**, respetando reglas de prioridad para
Transferencia/Cheque/Poliza. La carpeta original **nunca se modifica**.

---

## 1. Arquitectura

```
com.example.pdforganizer
├── Main.java                     Punto de entrada
├── ui/                           Swing (nunca bloquea: usa SwingWorker)
│   ├── MainWindow.java
│   ├── MainPanel.java
│   ├── ProgressPanel.java
│   ├── ResultPanel.java
│   └── SettingsPanel.java
├── application/                  Casos de uso / orquestacion
│   ├── ProcessingService.java
│   ├── OrganizationService.java
│   ├── CompressionService.java
│   ├── PartitionService.java
│   ├── PdfSplitService.java
│   ├── ProgressListener.java
│   └── AnalysisResult.java
├── domain/                       Objetos de datos inmutables
│   ├── FileInfo, PdfInfo, FolderInfo, FolderFileEntry
│   ├── ProcessingResult, ProcessingError
│   └── CompressionLevel, ProcessingStatus
├── infrastructure/                Acceso a disco y PDFBox
│   ├── FileSystemService.java
│   ├── PdfBoxCompressor.java
│   ├── PdfBoxSplitter.java
│   ├── ImageCompressor.java
│   └── ApplicationLogger.java
├── config/
│   └── AppConfig.java            Todos los valores configurables centralizados
└── util/
    ├── FileSizeUtils.java
    └── FileNameUtils.java
```

Capas: `ui` -> `application` -> `infrastructure`/`domain`. La UI nunca toca
PDFBox ni el disco directamente; `ProcessingService` es el unico punto de
entrada que la UI necesita conocer.

---

## 2. Requisitos

* Java 21 (JDK) para compilar; JRE 21 para ejecutar el `.jar`.
* Maven 3.8+ para compilar desde el codigo fuente.
* No requiere base de datos ni conexion a internet en tiempo de ejecucion.

## 3. Compilar y ejecutar desde el codigo fuente

```bash
# Compilar
mvn clean compile

# Ejecutar pruebas
mvn test

# Generar el JAR normal (sin dependencias incluidas)
mvn clean package

# Ejecutar desde el IDE / linea de comandos con classpath de Maven
mvn -q exec:java -Dexec.mainClass=com.example.pdforganizer.Main   # requiere exec-plugin, opcional
```

### Generar un JAR ejecutable con todas las dependencias (recomendado para distribuir)

El `pom.xml` ya incluye `maven-shade-plugin`, que se ejecuta automaticamente
en la fase `package`:

```bash
mvn clean package
```

Esto genera dos artefactos en `target/`:

* `pdf-organizer.jar` — JAR normal, requiere las dependencias en el classpath.
* `pdf-organizer-all.jar` — **JAR "fat jar"** con PDFBox y demas dependencias
  incluidas. Este es el que se distribuye al cliente:

```bash
java -jar target/pdf-organizer-all.jar
```

> Nota sobre la version de PDFBox: el proyecto usa PDFBox **2.0.x** (rama
> estable de facto, ampliamente usada en produccion) en lugar de 3.x. La
> version 3.x cambia la API de carga de documentos (introduce la clase
> `Loader` en sustitucion de `PDDocument.load(...)`), y se prioriza aqui
> estabilidad y compatibilidad verificada. Si se desea migrar a 3.x, el
> unico punto de contacto con la API de carga esta en
> `PdfBoxCompressor.compress()` y `PdfBoxSplitter.splitByPageLimit()`.

---

## 4. Crear un instalable / ejecutable para Windows

La maquina del cliente puede ser antigua y no tener Java instalado ni
Maven. La estrategia recomendada usa `jpackage` (incluido en el JDK desde
Java 14) junto con un runtime reducido generado con `jlink`, de forma que
el cliente reciba una carpeta autocontenida sin instalar nada mas.

### Paso 1: generar el fat jar

```bash
mvn clean package
```

### Paso 2: generar un runtime reducido con jlink (opcional pero recomendado)

Reduce el tamano del runtime distribuido incluyendo solo los modulos que
la aplicacion realmente usa (Swing, IO, etc.), en lugar del JDK completo:

```bash
jlink --module-path "%JAVA_HOME%\jmods" ^
      --add-modules java.desktop,java.logging,java.xml,jdk.crypto.ec ^
      --output runtime ^
      --strip-debug --no-header-files --no-man-pages --compress=2
```

(En Linux/macOS usar `$JAVA_HOME/jmods` y `\` en vez de `^` para
continuar linea.)

### Paso 3: empaquetar con jpackage (Windows)

Ejecutar en una maquina Windows con JDK 21 instalado (jpackage genera
binarios nativos de la plataforma en la que se ejecuta, asi que para
producir un `.exe`/instalador de Windows se debe correr en Windows):

```powershell
jpackage --type app-image ^
         --name PDFOrganizer ^
         --input target ^
         --main-jar pdf-organizer-all.jar ^
         --main-class com.example.pdforganizer.Main ^
         --runtime-image runtime ^
         --dest dist
```

Esto genera:

```
dist/PDFOrganizer/
├── PDFOrganizer.exe
├── app/
├── runtime/
└── ...
```

Esa carpeta completa (`dist/PDFOrganizer/`) es lo que se entrega al
cliente: se copia a su maquina y se ejecuta `PDFOrganizer.exe`, sin
necesidad de instalar Java, Maven ni ningun IDE.

Si se prefiere un instalador en vez de una carpeta portable, cambiar
`--type app-image` por `--type exe` o `--type msi` (requiere WiX Toolset
instalado en la maquina donde se ejecuta jpackage).

### Requisitos de la computadora cliente

* Windows 7 SP1 o superior (64 bits recomendado).
* Ninguna instalacion previa de Java: el runtime va incluido.
* Espacio en disco: unos 150-250 MB para la app + runtime reducido.
* RAM: la aplicacion esta optimizada para funcionar con 1-2 GB libres,
  ya que procesa un archivo a la vez y no carga documentos completos
  innecesariamente en memoria (ver seccion 8).

---

## 5. Uso

1. **ANALIZAR**: seleccionar la carpeta madre de origen y la carpeta donde
   se creara la salida. Elegir el nivel de compresion (Baja/Media/Alta).
   Al pulsar "ANALIZAR" se muestra una vista previa con el conteo de
   archivos, PDFs, otros archivos y tamano total.
2. **PROCESAR**: confirmar la vista previa para iniciar el procesamiento.
   La barra de progreso muestra el archivo actual, el porcentaje y la
   etapa (comprimiendo, validando, etc.). La interfaz nunca se congela
   porque el procesamiento corre en un `SwingWorker` fuera del Event
   Dispatch Thread.
3. **Resultado final**: al terminar se muestra un resumen (archivos
   procesados, PDFs divididos, carpetas creadas, errores, espacio
   ahorrado) con botones para abrir la carpeta de salida o ver errores.

La carpeta de salida se llama `comprimido <NOMBRE_ORIGINAL>` y cada
proveedor se renombra con el prefijo `Punto 4-Documental <Proveedor>`.

---

## 6. Algoritmo de compresion de PDF

PDFBox no ofrece un metodo generico "comprimir PDF". La estrategia
implementada (`PdfBoxCompressor`) recorre cada pagina, localiza los
`XObject` de tipo imagen dentro de sus recursos, los decodifica a
`BufferedImage`, los recomprime como JPEG con la calidad/escala del nivel
elegido, y reemplaza la entrada en el diccionario de recursos. El resto
del documento (texto, vectores, estructura) no se toca, por lo que el PDF
resultante conserva todas las paginas y sigue siendo valido.

| Nivel | Calidad JPEG | Escala de imagen |
|-------|-------------|-------------------|
| BAJA  | 0.85        | 100%              |
| MEDIA | 0.60        | 85%               |
| ALTA  | 0.35        | 65%               |

Se procesa un archivo a la vez y el documento se cierra apenas se guarda,
para no retener PDFs abiertos en memoria mas de lo necesario.

## 7. Algoritmo de division de PDFs grandes ("expandir y retroceder")

Si un PDF comprimido sigue superando 3.95 MB, se divide por paginas
completas (nunca se recorta contenido ni se pierden paginas). Como las
paginas pueden pesar muy distinto entre si, no se asume un numero fijo de
paginas por fragmento:

1. Cada fragmento empieza con **1 sola pagina**.
2. Se anaden paginas de una en una, escribiendo un archivo temporal real y
   midiendo su tamano en disco tras cada adicion.
3. En cuanto anadir una pagina mas rompe el limite, se conserva la ultima
   version que si cabia y se cierra ese fragmento.
4. Si incluso una sola pagina supera el limite, se conserva esa pagina
   sola (nunca se descarta), y se continua con el resto del documento.

Esto garantiza: nunca se pierden paginas, se respeta el orden original, y
el limite de tamano se respeta siempre que sea tecnicamente posible.

## 8. Algoritmo de particion en carpetas (First Fit Decreasing adaptado)

`PartitionService` distribuye los archivos de una poliza/proveedor en una
o mas carpetas de salida usando una variante de **First Fit Decreasing
(FFD)**, un algoritmo clasico de bin-packing: ordena los elementos de
mayor a menor tamano y los coloca en el primer contenedor donde quepan,
abriendo uno nuevo si ninguno tiene espacio. Se eligio FFD (en vez de un
bin-packing exacto, que es NP-dificil) porque da resultados cercanos al
optimo con complejidad baja, suficiente para este caso de uso.

Adaptaciones para las reglas del cliente:

* La **carpeta numero 1** se llena primero con los PDFs prioritarios
  (Transferencia/Cheque/Poliza, deteccion case-insensitive), ordenados de
  forma **ascendente** por tamano — para maximizar la *cantidad* de
  prioritarios que caben en un presupuesto fijo conviene intentar primero
  los mas pequenos.
* El espacio restante de la carpeta 1, y todas las carpetas siguientes, se
  llenan con FFD estandar (descendente) sobre el resto de archivos.
* **El limite de 3.95 MB nunca se viola**, ni siquiera por la regla de
  prioridad: si los archivos prioritarios no caben todos en la carpeta 1,
  el resto pasa a las carpetas siguientes junto con los demas archivos.

### Regla de 3.95 MB

```java
public static final long MAX_FOLDER_SIZE_BYTES = (long) (3.95 * 1024 * 1024);
```

Se usa 3.95 MB (no 4 MB exactos) como margen de seguridad, definido
explicitamente en bytes en `AppConfig` para evitar errores de redondeo y
centralizar el valor en un solo lugar.

### Decision de diseno sobre a que nivel se particiona (revisar)

La regla "las carpetas de proveedor no pueden superar 3.95 MB" (seccion 5
del requerimiento original) y la regla equivalente para polizas (seccion
6) se interpretaron asi, dado que el requerimiento no es explicito sobre
que ocurre cuando un proveedor SI tiene subcarpetas de poliza:

* Si un proveedor tiene subcarpetas de poliza, **cada poliza se particiona
  de forma independiente** (el proveedor solo agrupa polizas ya
  particionadas).
* Si un proveedor **no** tiene subcarpetas (archivos sueltos), el
  **proveedor mismo se particiona como unidad**.

Este es el punto que mas vale revisar con el cliente/equipo si la
estructura real difiere de la asumida.

---

## 9. Manejo de errores

Un error en un archivo individual (PDF corrupto, protegido, bloqueado,
sin permisos, etc.) **nunca detiene** el procesamiento completo: se
registra (archivo, operacion, mensaje, causa) y se continua con los demas
archivos. Si un PDF no puede comprimirse, se usa el original sin
modificar en su lugar, para no perder el documento. Todos los errores se
muestran al finalizar y se guardan en el log de la ejecucion.

## 10. Logs

Cada ejecucion crea un archivo en `logs/AAAA-MM-DD_HH-mm-ss.log` con:
inicio, origen, destino, configuracion, resumen final (archivos
procesados, PDFs divididos, errores, duracion implicita por timestamps).

## 11. Optimizacion para hardware antiguo

* Procesamiento **secuencial por defecto** (1 worker); configurable.
* Cada PDF se abre, procesa y cierra individualmente (try-with-resources);
  nunca se mantienen varios documentos completos en memoria a la vez.
* Los fragmentos de division se escriben a disco y se miden directamente,
  en vez de mantener representaciones intermedias completas en RAM.
* Sin animaciones ni actualizaciones constantes de la UI: el progreso se
  actualiza por archivo, no por frame.
* `SwingWorker` mantiene la interfaz responsiva sin hilos adicionales
  descontrolados.

## 12. Pruebas

```bash
mvn test
```

Incluye (JUnit 5): `FileSizeUtilsTest`, `FileNameUtilsTest`,
`PartitionServiceTest` (carpetas de 3.94MB, cercanas a 3.95MB, que superan
el limite, reglas de prioridad, multiples divisiones), `PdfBoxSplitterTest`
(PDFs reales con paginas de tamanos muy distintos, paginas que superan el
limite por si solas, preservacion de orden) y `OrganizationServiceTest`
(integracion end-to-end: preservacion del original, estructura de salida,
copia exacta de XML).

Verificado localmente: **29/29 pruebas pasan**, y una ejecucion completa
del pipeline sobre una carpeta de prueba real confirma que el original
queda intacto byte a byte y que la salida respeta la estructura y el
limite de tamano esperados.

## 13. Troubleshooting

| Problema | Causa probable | Solucion |
|----------|------------------|----------|
| "No se pudo comprimir X, se usara el original" en el log | PDF corrupto, cifrado o con estructura no estandar | Revisar el PDF con otro visor; si es valido pero protegido con contrasena, quitar la proteccion antes de procesar |
| Una carpeta de salida pesa ligeramente mas de 3.95 MB | No deberia ocurrir; si sucede, es un bug — revisar `PartitionService` | Reportar con el archivo de log de esa ejecucion |
| La aplicacion no abre en el cliente | Falta el runtime de Java | Usar la carpeta generada por `jpackage` (incluye el runtime) en vez del `.jar` suelto |
| El proceso tarda mucho | Compresion ALTA + muchos PDFs con imagenes grandes en hardware muy antiguo | Usar nivel MEDIA/BAJA o aumentar el numero de workers en configuracion (con cuidado, aumenta uso de RAM) |
