package simplecalc;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourceOptimizer {

    private String sourceCode;
    private List<String> sourceLines;
    private Map<String, VariableInfo> variableTable;
    private Set<Integer> linesToRemove;
    private Map<String, String> constantPropagation;
    private Set<String> variablesAvailableAtLine; // NUEVO: tracking de variables disponibles
    private OptimizationResult result;

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
        this.variablesAvailableAtLine = new LinkedHashSet<>();
        this.result = new OptimizationResult();
        this.result.setOriginalLines(sourceLines.size());
    }

    public OptimizationResult optimize() {
        try {
            buildSymbolTable();
            performConstantPropagation();
            eliminateDeadCode();
            String optimizedCode = generateOptimizedSource();
            result.setOptimizedSource(optimizedCode);
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

            Matcher declMatcher = VAL_VAR_PATTERN.matcher(line);
            if (declMatcher.find()) {
                String type = declMatcher.group(1);
                String varName = declMatcher.group(2);
                String dataType = declMatcher.group(3);
                String value = declMatcher.group(4).trim();
                value = cleanValue(value);

                VariableInfo info = new VariableInfo(varName, type, dataType, value, lineNumber);
                variableTable.put(varName, info);
                continue;
            }

            Matcher assignMatcher = ASSIGNMENT_PATTERN.matcher(line);
            if (assignMatcher.find()) {
                String varName = assignMatcher.group(1);
                String value = assignMatcher.group(2).trim();

                if (variableTable.containsKey(varName)) {
                    value = cleanValue(value);
                    variableTable.get(varName).setAssignedValue(value);
                }
            }

            Matcher usageMatcher = VARIABLE_USAGE_PATTERN.matcher(line);
            while (usageMatcher.find()) {
                String possibleVar = usageMatcher.group(1);

                if (!keywords.contains(possibleVar) && variableTable.containsKey(possibleVar)) {
                    VariableInfo info = variableTable.get(possibleVar);
                    if (info.getDeclarationLine() != lineNumber) {
                        info.addUsage(lineNumber);
                    }
                }
            }
        }
    }

    private String cleanValue(String value) {
        int commentIndex = value.indexOf("//");
        if (commentIndex != -1) {
            value = value.substring(0, commentIndex);
        }
        return value.trim();
    }

    private void performConstantPropagation() {
        boolean changed;
        int iterations = 0;
        final int MAX_ITERATIONS = 100;

        do {
            changed = false;
            iterations++;

            if (iterations > MAX_ITERATIONS) {
                System.err.println("ADVERTENCIA: Límite de iteraciones alcanzado en propagación de constantes");
                break;
            }

            for (VariableInfo var : variableTable.values()) {
                String currentValue = var.getAssignedValue();

                if (isNumericLiteral(currentValue)) {
                    if (!constantPropagation.containsKey(var.getName())) {
                        constantPropagation.put(var.getName(), currentValue);
                        result.addPropagatedValue(var.getName(), currentValue);
                        changed = true;
                    }
                } else if (variableTable.containsKey(currentValue)) {
                    if (constantPropagation.containsKey(currentValue)) {
                        String propagatedValue = constantPropagation.get(currentValue);
                        var.setAssignedValue(propagatedValue);
                        constantPropagation.put(var.getName(), propagatedValue);
                        result.addPropagatedValue(var.getName(), propagatedValue);
                        changed = true;
                    }
                } else if (currentValue.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
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
        variablesAvailableAtLine.clear(); // Reset para tracking

        for (int i = 0; i < sourceLines.size(); i++) {
            int lineNumber = i + 1;
            String line = sourceLines.get(i);

            if (linesToRemove.contains(lineNumber)) {
                continue;
            }

            if (line.trim().isEmpty()) {
                consecutiveBlankLines++;
                if (consecutiveBlankLines > 1) {
                    blankLinesRemoved++;
                    continue;
                }
            } else {
                consecutiveBlankLines = 0;
            }

            // CRÍTICO: Actualizar variables disponibles ANTES de optimizar la línea
            Matcher declMatcher = VAL_VAR_PATTERN.matcher(line);
            if (declMatcher.find()) {
                String varName = declMatcher.group(2);
                variablesAvailableAtLine.add(varName);
            }

            String optimizedLine = applyConstantPropagation(line);
            optimized.append(optimizedLine).append("\n");
        }

        result.setRemovedBlankLines(blankLinesRemoved);
        return optimized.toString();
    }

    private String applyConstantPropagation(String line) {
        String optimizedLine = line;

        // Detectar declaraciones
        Matcher declMatcher = VAL_VAR_PATTERN.matcher(line);
        if (declMatcher.find()) {
            String type = declMatcher.group(1);
            String varName = declMatcher.group(2);
            String dataType = declMatcher.group(3);
            String value = declMatcher.group(4).trim();

            // OPTIMIZACIÓN MEJORADA: Solo propagar constantes disponibles
            String optimizedValue = propagateInExpression(value, varName);

            String indentation = line.substring(0, line.indexOf(type));
            optimizedLine = String.format("%s%s %s: %s = %s", indentation, type, varName, dataType, optimizedValue);
            return optimizedLine;
        }

        // Detectar asignaciones
        Matcher assignMatcher = ASSIGNMENT_PATTERN.matcher(line);
        if (assignMatcher.find()) {
            String varName = assignMatcher.group(1);
            String value = assignMatcher.group(2).trim();

            String optimizedValue = propagateInExpression(value, varName);

            String indentation = line.substring(0, line.indexOf(varName));
            optimizedLine = String.format("%s%s = %s", indentation, varName, optimizedValue);
            return optimizedLine;
        }

        // Para otras líneas (print, etc.)
        optimizedLine = propagateInExpression(line, null);
        return optimizedLine;
    }

    /**
     * NUEVO MÉTODO: Propaga constantes solo si la variable está disponible
     * y no es la variable que se está declarando.
     */
    private String propagateInExpression(String expression, String declaringVar) {
        String result = expression;

        // Ordenar por longitud descendente para evitar reemplazos parciales
        List<Map.Entry<String, String>> sortedEntries = new ArrayList<>(constantPropagation.entrySet());
        sortedEntries.sort((a, b) -> b.getKey().length() - a.getKey().length());

        for (Map.Entry<String, String> entry : sortedEntries) {
            String varName = entry.getKey();
            String constValue = entry.getValue();

            // NO reemplazar la variable que se está declarando
            if (varName.equals(declaringVar)) {
                continue;
            }

            // CRÍTICO: Solo propagar si la variable ya fue declarada
            // (está en variablesAvailableAtLine) o si no es una declaración
            if (declaringVar != null && !variablesAvailableAtLine.contains(varName)) {
                // Esta variable aún no está disponible, no propagar
                continue;
            }

            // Solo propagar si NO está dentro de string literal
            if (!isInsideStringLiteral(result, varName)) {
                Pattern varPattern = Pattern.compile("\\b" + Pattern.quote(varName) + "\\b");
                result = varPattern.matcher(result).replaceAll(constValue);
            }
        }

        return result;
    }

    private boolean isInsideStringLiteral(String line, String target) {
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