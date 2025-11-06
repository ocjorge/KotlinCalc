package simplecalc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KotlinCodeGenerator {

    private final List<Parser.ExpressionData> collectedExpressions;
    private final Map<String, String> variableTypes; // Necesitamos los tipos para las declaraciones
    private final Map<String, Boolean> isVar; // Para saber si es val o var
    private final Map<String, String> lastKnownValue = new HashMap<>(); // Para la propagación de valores
    private final StringBuilder generatedCode = new StringBuilder();
    private int indentLevel = 0;

    // Patrones para parsear los cuádruplos optimizados
    private static final Pattern ASSIGNMENT_QUAD_PATTERN = Pattern.compile("([a-zA-Z_][a-zA-Z0-9_]*) = (.+)");
    private static final Pattern PRINT_QUAD_PATTERN = Pattern.compile("PRINT (.+)");
    private static final Pattern RANGE_START_QUAD_PATTERN = Pattern.compile("RANGE_START (.+)");
    private static final Pattern RANGE_END_QUAD_PATTERN = Pattern.compile("RANGE_END (.+)");
    private static final Pattern ARITHMETIC_QUAD_PATTERN = Pattern.compile("(t\\d+) = (.+) ([+\\-*/]) (.+)"); // Aritméticos que aún podrían quedar

    public KotlinCodeGenerator(List<Parser.ExpressionData> collectedExpressions, Map<String, String> variableTypes, Map<String, Boolean> isVar) {
        this.collectedExpressions = collectedExpressions;
        this.variableTypes = new HashMap<>(variableTypes); // Copia para no modificar el original del parser
        this.isVar = new HashMap<>(isVar); // Copia
    }

    public String generateOptimizedKotlinCode() {
        generatedCode.append("fun main() {\n");
        increaseIndent();

        // Regenerar declaraciones y asignaciones principales
        // Las expresiones están recolectadas en el orden en que aparecen en el código.
        for (Parser.ExpressionData exprData : collectedExpressions) {
            String target = extractTargetFromExpressionData(exprData.infixTokens);
            List<String> quadruples = exprData.quadruples; // Usamos los cuádruplos YA OPTIMIZADOS

            // Si no hay cuádruplos, significa que la expresión fue completamente trivial o eliminada
            // En nuestro caso, las declaraciones y prints siempre resultarán en al menos un cuádruplo final.
            if (quadruples.isEmpty()) {
                continue;
            }

            String finalQuad = quadruples.get(quadruples.size() - 1); // El último cuádruplo es el más relevante para el resultado final

            Matcher assignmentMatcher = ASSIGNMENT_QUAD_PATTERN.matcher(finalQuad);
            Matcher printMatcher = PRINT_QUAD_PATTERN.matcher(finalQuad);
            Matcher rangeStartMatcher = RANGE_START_QUAD_PATTERN.matcher(finalQuad);
            Matcher rangeEndMatcher = RANGE_END_QUAD_PATTERN.matcher(finalQuad);

            // Intentamos recrear la línea de código Kotlin
            if (assignmentMatcher.matches()) {
                String varName = assignmentMatcher.group(1);
                String value = assignmentMatcher.group(2);

                // Reconstruir el valor, ya aplicando Copy Propagation/Constant Folding
                String finalValue = reconstructExpression(value, exprData.numericResultsSimulation, lastKnownValue);

                // Si la variable se declaró por primera vez en esta línea
                if (!lastKnownValue.containsKey(varName)) {
                    // Para simplificar, asumimos que siempre es Int si es una expresión numérica.
                    // En un compilador real, el tipo se sabría desde el AST o la tabla de símbolos.
                    String type = variableTypes.getOrDefault(varName, "Int");
                    String keyword = isVar.getOrDefault(varName, false) ? "var" : "val";
                    appendIndent().append(String.format("%s %s: %s = %s\n", keyword, varName, type, finalValue));
                } else {
                    // Es una reasignación
                    appendIndent().append(String.format("%s = %s\n", varName, finalValue));
                }
                lastKnownValue.put(varName, finalValue); // Actualizar el último valor conocido para propagación
            } else if (printMatcher.matches()) {
                String valueToPrint = printMatcher.group(1);
                String finalValue = reconstructExpression(valueToPrint, exprData.numericResultsSimulation, lastKnownValue);
                // Si es una cadena literal, la imprimimos directamente.
                if (finalValue.startsWith("\"") && finalValue.endsWith("\"")) {
                    appendIndent().append(String.format("print(%s)\n", finalValue));
                } else {
                    appendIndent().append(String.format("print(%s)\n", finalValue));
                }
            }
            // Por ahora, ignoramos la reconstrucción de FOR/IF/WHILE en este generador simplificado,
            // ya que son más complejos y no son el foco de esta demo de optimización de expresiones.
            // Para RANGE_START/END, simplemente los omitimos ya que son constructos internos para el bucle for.
            // Si la GUI quisiera mostrar un 'for', necesitaría una lógica mucho más compleja.
        }

        decreaseIndent();
        generatedCode.append("}\n");

        return generatedCode.toString();
    }

    private String extractTargetFromExpressionData(List<Token> infixTokens) {
        if (infixTokens.isEmpty()) return "UNKNOWN";
        // Si la primera palabra es 'val' o 'var', el segundo token es el ID
        if (infixTokens.get(0).type == Token.TokenType.VAL_KEYWORD || infixTokens.get(0).type == Token.TokenType.VAR_KEYWORD) {
            if (infixTokens.size() > 1 && infixTokens.get(1).type == Token.TokenType.ID) {
                return infixTokens.get(1).lexeme;
            }
        }
        // Si el primer token es un ID y el segundo es ASIGNACION, el ID es el target
        if (infixTokens.get(0).type == Token.TokenType.ID) {
            return infixTokens.get(0).lexeme;
        }
        // Caso print o range_start/end, el target real es un placeholder
        // Para print, ya está manejado por el patrón PRINT_QUAD_PATTERN.
        return "UNKNOWN"; // Default
    }


    // Intenta reconstruir una expresión a partir de su forma final optimizada o un valor directo.
    // Utiliza lastKnownValue para la propagación si es una variable.
    private String reconstructExpression(String expressionPart, Map<String, Integer> numericResultsSimulation, Map<String, String> lastKnownValues) {
        // 1. Si es un literal (número o cadena)
        if (isNumericLiteral(expressionPart) || (expressionPart.startsWith("\"") && expressionPart.endsWith("\""))) {
            return expressionPart;
        }

        // 2. Si es una variable temporal (tX) y su valor numérico está disponible
        if (expressionPart.startsWith("t") && numericResultsSimulation.containsKey(expressionPart)) {
            return String.valueOf(numericResultsSimulation.get(expressionPart));
        }
        
        // 3. Si es una variable (ID) y tenemos su último valor conocido propagado
        if (lastKnownValues.containsKey(expressionPart)) {
            return lastKnownValues.get(expressionPart);
        }

        // 4. Si es una operación aritmética simple que aún no se plegó (raro con el optimizador actual)
        // Esto es muy simplificado y no maneja expresiones anidadas o complejas que podrían quedar.
        Matcher arithmeticMatcher = Pattern.compile("(.+) ([+\\-*/]) (.+)").matcher(expressionPart);
        if (arithmeticMatcher.matches()) {
            String op1 = reconstructExpression(arithmeticMatcher.group(1), numericResultsSimulation, lastKnownValues);
            String operator = arithmeticMatcher.group(2);
            String op2 = reconstructExpression(arithmeticMatcher.group(3), numericResultsSimulation, lastKnownValues);
            // Podríamos intentar Constant Foldear aquí de nuevo si ambos son literales
            if (isNumericLiteral(op1) && isNumericLiteral(op2)) {
                try {
                    int val1 = Integer.parseInt(op1);
                    int val2 = Integer.parseInt(op2);
                    int result = evaluate(val1, val2, operator); // Usa tu método evaluate
                    return String.valueOf(result);
                } catch (NumberFormatException e) {
                    // Fallback
                }
            }
            return String.format("%s %s %s", op1, operator, op2);
        }


        // Por defecto, devolver la parte de la expresión tal cual
        return expressionPart;
    }

    // Método auxiliar para evaluar operaciones (copiado del Parser)
    private int evaluate(int val1, int val2, String opType) {
        switch (opType) {
            case "+": return val1 + val2;
            case "-": return val1 - val2;
            case "*": return val1 * val2;
            case "/":
                if (val2 == 0) {
                    // Esto no debería ocurrir si el optimizador ya manejó divisiones por cero con CF.
                    // Pero como fallback, podemos devolver 0 o lanzar una excepción.
                    System.err.println("ADVERTENCIA: División por cero detectada durante la reconstrucción.");
                    return 0;
                }
                return val1 / val2;
            default: return 0;
        }
    }

    private boolean isNumericLiteral(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private StringBuilder appendIndent() {
        for (int i = 0; i < indentLevel; i++) {
            generatedCode.append("    "); // 4 espacios por nivel de indentación
        }
        return generatedCode;
    }

    private void increaseIndent() {
        indentLevel++;
    }

    private void decreaseIndent() {
        if (indentLevel > 0) {
            indentLevel--;
        }
    }
}