# Paquetes Locales Modificados

Este directorio contiene versiones locales de paquetes de Flutter que han sido modificados para cumplir con los requisitos específicos del proyecto.

## flutter_vision

**Versión original:** 2.0.0  
**Fuente:** https://pub.dev/packages/flutter_vision

### Modificaciones realizadas

Se modificó el paquete `flutter_vision` para excluir las librerías nativas de OpenCV que no soportan el tamaño de página de 16KB requerido por Google Play para dispositivos Android modernos.

#### Cambios en `android/build.gradle`:

1. **Exclusión de librerías nativas de OpenCV:**
   ```gradle
   packagingOptions {
       // Excluir librerías nativas de OpenCV que no soportan 16KB page size
       exclude 'lib/arm64-v8a/libopencv_java3.so'
       exclude 'lib/arm64-v8a/libopencv_java4.so'
       exclude 'lib/armeabi-v7a/libopencv_java3.so'
       exclude 'lib/armeabi-v7a/libopencv_java4.so'
       exclude 'lib/x86/libopencv_java3.so'
       exclude 'lib/x86/libopencv_java4.so'
       exclude 'lib/x86_64/libopencv_java3.so'
       exclude 'lib/x86_64/libopencv_java4.so'
   }
   ```

2. **Exclusión de dependencias problemáticas:**
   ```gradle
   implementation('com.github.vladiH:opencv-android:v1.0.0') {
       exclude group: 'org.opencv', module: 'opencv-android'
   }
   ```

### Motivo de las modificaciones

Las versiones antiguas de OpenCV (3.x y 4.x) incluyen librerías nativas compiladas con un tamaño de página de 4KB, que no son compatibles con los dispositivos Android que usan kernels con páginas de 16KB (como algunos dispositivos con Android 15+).

Google Play ahora requiere que todas las aplicaciones soporten páginas de 16KB para garantizar la compatibilidad con dispositivos modernos.

### Impacto en la funcionalidad

La exclusión de estas librerías **NO afecta** la funcionalidad de detección YOLO del proyecto, ya que:
- El modelo YOLO utiliza TensorFlow Lite, no OpenCV
- Las librerías de TensorFlow Lite están actualizadas y soportan 16KB page size
- Las operaciones de procesamiento de imágenes se realizan mediante las APIs nativas de Flutter y TFLite

### Verificación

Para verificar que las librerías de OpenCV se excluyeron correctamente:

```bash
unzip -l build/app/outputs/flutter-apk/app-production-release.apk | grep -i "opencv"
```

El comando no debe devolver ningún resultado si la exclusión fue exitosa.

### Mantenimiento

Al actualizar `flutter_vision` a una versión más reciente, verificar si:
1. La nueva versión ya incluye soporte para 16KB page size
2. Si no, aplicar las mismas modificaciones a la nueva versión
3. Actualizar este README con la nueva versión y cambios

### Referencias

- [Soporte de Android para páginas de 16KB](https://developer.android.com/guide/practices/page-sizes)
- [Requisitos de Google Play para 16KB](https://support.google.com/googleplay/android-developer/answer/14574540)
