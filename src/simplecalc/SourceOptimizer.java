package simplecalc;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourceOptimizer {

    private String sourceCode;
    private List<String> sourceLines;
    private Map<String, VariableInfo> variableTable;
    private Set<Integer> linesToRemove;
    private Map<String, String> constantPropagation; // variable -> valor constante propagado
    private OptimizationResult result;

    // Patrones regex para análisis
    private static final Pattern VAL_VAR_PATTERN
            = Pattern.compile("^\\s*(val|var)\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*:\\s*(Int|String)\\s*=\\s*(.+)$");
    private static final Pattern ASSIGNMENT_PATTERN
            = Pattern.compile("^\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*(.+)$");
    private static final Pattern VARIABLE_USAGE_PATTERN
            = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");

    public SourceOptimizer(String sourceCode) {
        this.sourceCode = sourceCode;
        this.sourceLines = new ArrayList<>(Arrays.asList(sourceCode.split("\n")));
        this.variableTable = new LinkedHashMap<>();
        this.linesToRemove = new HashSet<>();
        this.constantPropagation = new HashMap<>();
        this.result = new OptimizationResult();
        this.result.setOriginalLines(sourceLines.size());
    }

    public OptimizationResult optimize() {
        try {
            // Fase 1: Construir tabla de símbolos y analizar usos
            buildSymbolTable();

            // Fase 2: Propagar constantes
            performConstantPropagation();

            // Fase 3: Eliminar código muerto
            eliminateDeadCode();

            // Fase 4: Generar código optimizado
            String optimizedCode = generateOptimizedSource();
            result.setOptimizedSource(optimizedCode);

            // Fase 5: Contar líneas finales
            result.setOptimizedLines(optimizedCode.split("\n").length);

        } catch (Exception e) {
            result.setError("Error durante la optimización: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
    }

    private void buildSymbolTable() {
        Set<String> keywords = new HashSet<>(Arrays.asList(
                "fun", "main", "val", "var", "Int", "String", "print", "readLine",
                "if", "while", "for", "in"
        ));

        for (int i = 0; i < sourceLines.size(); i++) {
            String line = sourceLines.get(i).trim();
            int lineNumber = i + 1;

            // Detectar declaraciones val/var
            Matcher declMatcher = VAL_VAR_PATTERN.matcher(line);
            if (declMatcher.find()) {
                String type = declMatcher.group(1); // val o var
                String varName = declMatcher.group(2);
                String dataType = declMatcher.group(3); // Int o String
                String value = declMatcher.group(4).trim();

                // Limpiar el valor (quitar comentarios, espacios, etc.)
                value = cleanValue(value);

                VariableInfo info = new VariableInfo(varName, type, dataType, value, lineNumber);
                variableTable.put(varName, info);
                continue;
            }

            // Detectar asignaciones a variables ya declaradas
            Matcher assignMatcher = ASSIGNMENT_PATTERN.matcher(line);
            if (assignMatcher.find()) {
                String varName = assignMatcher.group(1);
                String value = assignMatcher.group(2).trim();

                if (variableTable.containsKey(varName)) {
                    value = cleanValue(value);
                    variableTable.get(varName).setAssignedValue(value);
                }
            }

            // Detectar usos de variables (en cualquier contexto)
            Matcher usageMatcher = VARIABLE_USAGE_PATTERN.matcher(line);
            while (usageMatcher.find()) {
                String possibleVar = usageMatcher.group(1);

                // Excluir keywords y la propia declaración
                if (!keywords.contains(possibleVar) && variableTable.containsKey(possibleVar)) {
                    // Verificar que no sea la misma línea de declaración
                    VariableInfo info = variableTable.get(possibleVar);
                    if (info.getDeclarationLine() != lineNumber) {
                        info.addUsage(lineNumber);
                    }
                }
            }
        }
    }

    private String cleanValue(String value) {
        // Remover comentarios inline si existen
        int commentIndex = value.indexOf("//");
        if (commentIndex != -1) {
            value = value.substring(0, commentIndex);
        }
        return value.trim();
    }

    private void performConstantPropagation() {
        // Iterar hasta que no haya más cambios (punto fijo)
        boolean changed;
        int iterations = 0;
        final int MAX_ITERATIONS = 100; // Prevenir loops infinitos

        do {
            changed = false;
            iterations++;

            if (iterations > MAX_ITERATIONS) {
                System.err.println("ADVERTENCIA: Límite de iteraciones alcanzado en propagación de constantes");
                break;
            }

            for (VariableInfo var : variableTable.values()) {
                String currentValue = var.getAssignedValue();

                // Si el valor es un literal numérico, propagarlo
                if (isNumericLiteral(currentValue)) {
                    if (!constantPropagation.containsKey(var.getName())) {
                        constantPropagation.put(var.getName(), currentValue);
                        result.addPropagatedValue(var.getName(), currentValue);
                        changed = true;
                    }
                } // Si el valor es otra variable que ya tiene constante propagada
                else if (variableTable.containsKey(currentValue)) {
                    if (constantPropagation.containsKey(currentValue)) {
                        String propagatedValue = constantPropagation.get(currentValue);
                        var.setAssignedValue(propagatedValue);
                        constantPropagation.put(var.getName(), propagatedValue);
                        result.addPropagatedValue(var.getName(), propagatedValue);
                        changed = true;
                    }
                } // Si el valor es una expresión simple de una variable conocida
                else if (currentValue.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                    // Es una simple copia de otra variable
                    if (constantPropagation.containsKey(currentValue)) {
                        String propagatedValue = constantPropagation.get(currentValue);
                        var.setAssignedValue(propagatedValue);
                        constantPropagation.put(var.getName(), propagatedValue);
                        result.addPropagatedValue(var.getName(), propagatedValue);
                        changed = true;
                    }
                }
            }
        } while (changed);
    }

    private void eliminateDeadCode() {
        for (Map.Entry<String, VariableInfo> entry : variableTable.entrySet()) {
            VariableInfo var = entry.getValue();

            // Una variable está "muerta" si:
            // 1. Nunca se usa (no tiene líneas de uso)
            // 2. Solo se usa para asignar a otras variables que también están muertas
            if (!var.isUsed()) {
                var.setDead(true);
                linesToRemove.add(var.getDeclarationLine());
                result.addRemovedVariable(var.getName());
            }
        }
    }

    private String generateOptimizedSource() {
        StringBuilder optimized = new StringBuilder();
        int consecutiveBlankLines = 0;
        int blankLinesRemoved = 0;

        for (int i = 0; i < sourceLines.size(); i++) {
            int lineNumber = i + 1;
            String line = sourceLines.get(i);

            // Si la línea debe eliminarse (código muerto), saltarla
            if (linesToRemove.contains(lineNumber)) {
                continue;
            }

            // Controlar líneas en blanco consecutivas (máximo 1)
            if (line.trim().isEmpty()) {
                consecutiveBlankLines++;
                if (consecutiveBlankLines > 1) {
                    blankLinesRemoved++;
                    continue; // No agregar más de una línea en blanco consecutiva
                }
            } else {
                consecutiveBlankLines = 0;
            }

            // Aplicar propagación de constantes a la línea
            String optimizedLine = applyConstantPropagation(line);

            optimized.append(optimizedLine).append("\n");
        }

        result.setRemovedBlankLines(blankLinesRemoved);
        return optimized.toString();
    }

    private String applyConstantPropagation(String line) {
        String optimizedLine = line;

        // NO aplicar propagación en líneas de declaración/asignación de variables
        // Solo en líneas donde se USAN las variables (como print, expresiones, etc.)
        // Detectar si es una línea de declaración (val/var X: Type = ...)
        Matcher declMatcher = VAL_VAR_PATTERN.matcher(line);
        if (declMatcher.find()) {
            String varName = declMatcher.group(2); // Nombre de la variable que se está declarando
            String value = declMatcher.group(4).trim(); // El valor asignado

            // Solo propagar constantes en el LADO DERECHO de la asignación (el valor)
            // Pero NO reemplazar la variable que se está declarando (lado izquierdo)
            // Ordenar por longitud descendente para evitar reemplazos parciales
            List<Map.Entry<String, String>> sortedEntries = new ArrayList<>(constantPropagation.entrySet());
            sortedEntries.sort((a, b) -> b.getKey().length() - a.getKey().length());

            String optimizedValue = value;
            for (Map.Entry<String, String> entry : sortedEntries) {
                String propagateVarName = entry.getKey();
                String constValue = entry.getValue();

                // NO reemplazar la variable que se está declarando en esta línea
                if (propagateVarName.equals(varName)) {
                    continue;
                }

                // Reemplazar otras variables en el valor asignado
                Pattern varPattern = Pattern.compile("\\b" + Pattern.quote(propagateVarName) + "\\b");
                optimizedValue = varPattern.matcher(optimizedValue).replaceAll(constValue);
            }

            // Reconstruir la línea con el valor optimizado
            String type = declMatcher.group(1);
            String dataType = declMatcher.group(3);

            // Preservar indentación original
            String indentation = line.substring(0, line.indexOf(type));
            optimizedLine = String.format("%s%s %s: %s = %s", indentation, type, varName, dataType, optimizedValue);

            return optimizedLine;
        }

        // Detectar si es una línea de asignación simple (X = ...)
        Matcher assignMatcher = ASSIGNMENT_PATTERN.matcher(line);
        if (assignMatcher.find()) {
            String varName = assignMatcher.group(1); // Variable que recibe la asignación
            String value = assignMatcher.group(2).trim(); // Valor asignado

            List<Map.Entry<String, String>> sortedEntries = new ArrayList<>(constantPropagation.entrySet());
            sortedEntries.sort((a, b) -> b.getKey().length() - a.getKey().length());

            String optimizedValue = value;
            for (Map.Entry<String, String> entry : sortedEntries) {
                String propagateVarName = entry.getKey();
                String constValue = entry.getValue();

                // NO reemplazar la variable que se está asignando
                if (propagateVarName.equals(varName)) {
                    continue;
                }

                Pattern varPattern = Pattern.compile("\\b" + Pattern.quote(propagateVarName) + "\\b");
                optimizedValue = varPattern.matcher(optimizedValue).replaceAll(constValue);
            }

            // Preservar indentación
            String indentation = line.substring(0, line.indexOf(varName));
            optimizedLine = String.format("%s%s = %s", indentation, varName, optimizedValue);

            return optimizedLine;
        }

        // Para otras líneas (print, expresiones, etc.), aplicar propagación normal
        List<Map.Entry<String, String>> sortedEntries = new ArrayList<>(constantPropagation.entrySet());
        sortedEntries.sort((a, b) -> b.getKey().length() - a.getKey().length());

        for (Map.Entry<String, String> entry : sortedEntries) {
            String varName = entry.getKey();
            String constValue = entry.getValue();

            // Usar regex con word boundaries para reemplazos precisos
            Pattern varPattern = Pattern.compile("\\b" + Pattern.quote(varName) + "\\b");

            // Solo reemplazar si NO está dentro de una string literal
            if (!isInsideStringLiteral(optimizedLine, varName)) {
                optimizedLine = varPattern.matcher(optimizedLine).replaceAll(constValue);
            }
        }

        return optimizedLine;
    }

    private boolean isInsideStringLiteral(String line, String target) {
        // Detectar si el target está dentro de comillas
        int index = line.indexOf(target);
        if (index == -1) {
            return false;
        }

        int quotesBefore = 0;
        for (int i = 0; i < index; i++) {
            if (line.charAt(i) == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                quotesBefore++;
            }
        }

        // Si hay un número impar de comillas antes, está dentro de un string
        return quotesBefore % 2 != 0;
    }

    private boolean isNumericLiteral(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public Map<String, VariableInfo> getVariableTable() {
        return variableTable;
    }
}
