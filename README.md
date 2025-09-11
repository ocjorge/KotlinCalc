# Kotlin Compiler Simulator

Un compilador educativo que simula un subconjunto de la sintaxis de Kotlin, desarrollado en Java con interfaz gráfica.

![Java](https://img.shields.io/badge/Java-17%2B-orange?style=flat-square&logo=java)
![Swing](https://img.shields.io/badge/GUI-Swing-blue?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-green?style=flat-square)

## Características

- **Análisis Léxico**: Tokenización de código fuente tipo Kotlin
- **Análisis Sintáctico**: Validación de estructura gramatical
- **Análisis Semántico**: Verificación de variables declaradas
- **Interfaz Gráfica**: IDE con numeración de líneas y resaltado de errores
- **Manejo de Errores**: Reporte detallado con ubicación de errores

## Tokens Soportados

| Categoría | Tokens |
|-----------|--------|
| Palabras reservadas | `fun`, `val`, `var`, `if`, `print`, `readLine` |
| Operadores | `+`, `-`, `*`, `/`, `=`, `==`, `<`, `>` |
| Delimitadores | `(`, `)`, `{`, `}`, `:` |
| Literales | Números enteros, Cadenas de texto |
| Identificadores | Nombres de variables |

## Ejemplo de Código

```kotlin
fun main() {
    val numero: Int = 42
    var resultado: Int = numero + 10
    print("El resultado es: ")
    print(resultado)
    
    val input: String = readLine()
    
    if (resultado > 50) {
        print("Mayor que 50")
    }
}
```

## Estructura del Proyecto

```
src/
├── simplecalc/
│   ├── Lexer.java          # Analizador léxico
│   ├── Parser.java         # Analizador sintáctico y semántico
│   ├── Token.java          # Definición de tokens
│   ├── SyntaxError.java    # Manejo de errores sintácticos
│   ├── SemanticError.java  # Manejo de errores semánticos
│   ├── SimpleCalcGUI.java  # Interfaz gráfica principal
│   ├── LineNumberingTextArea.java # Componente de numeración
│   └── Main.java           # Punto de entrada
```

## Requisitos

- Java JDK 17 o superior
- Entorno con soporte para Java Swing

## Uso

1. Compila el proyecto:
   ```bash
   javac -d bin src/simplecalc/*.java
   ```

2. Ejecuta la aplicación:
   ```bash
   java -cp bin simplecalc.Main
   ```

3. Escribe código en el área de texto
4. Haz clic en "Compilar Código Kotlin" para validar
5. Revisa los resultados en el panel de salida

## Funcionalidades de la GUI

- **Numeración de líneas**: Visualización de números de línea
- **Resaltado de errores**: Marcado visual de errores en el código
- **Reporte detallado**: Lista completa de tokens y errores encontrados
- **Sintaxis tipo Kotlin**: Soporte para declaraciones, condicionales y E/S

## Desarrollado por

- Orozco Reyes Hiram
- Ortiz Ceballos Jorge  
- Salgado Rojas Marelin Iral

## Licencia

Este proyecto está bajo la Licencia MIT.

---

**Nota**: Este es un compilador educativo que simula un subconjunto de Kotlin para fines académicos. No es un compilador completo de Kotlin.
