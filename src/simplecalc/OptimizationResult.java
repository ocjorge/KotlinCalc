package simplecalc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OptimizationResult {
    private String optimizedSource;
    private int originalLines;
    private int optimizedLines;
    private List<String> removedVariables;
    private Map<String, String> propagatedValues;
    private int removedBlankLines;
    private boolean hasErrors;
    private String errorMessage;
    
    public OptimizationResult() {
        this.optimizedSource = "";
        this.originalLines = 0;
        this.optimizedLines = 0;
        this.removedVariables = new ArrayList<>();
        this.propagatedValues = new HashMap<>();
        this.removedBlankLines = 0;
        this.hasErrors = false;
        this.errorMessage = "";
    }
    
    // Getters
    public String getOptimizedSource() { return optimizedSource; }
    public int getOriginalLines() { return originalLines; }
    public int getOptimizedLines() { return optimizedLines; }
    public List<String> getRemovedVariables() { return removedVariables; }
    public Map<String, String> getPropagatedValues() { return propagatedValues; }
    public int getRemovedBlankLines() { return removedBlankLines; }
    public boolean hasErrors() { return hasErrors; }
    public String getErrorMessage() { return errorMessage; }
    
    // Setters
    public void setOptimizedSource(String source) { this.optimizedSource = source; }
    public void setOriginalLines(int lines) { this.originalLines = lines; }
    public void setOptimizedLines(int lines) { this.optimizedLines = lines; }
    public void addRemovedVariable(String varName) { this.removedVariables.add(varName); }
    public void addPropagatedValue(String varName, String value) { this.propagatedValues.put(varName, value); }
    public void setRemovedBlankLines(int count) { this.removedBlankLines = count; }
    public void setError(String message) { 
        this.hasErrors = true;
        this.errorMessage = message;
    }
    
    public int getLinesReduced() {
        return originalLines - optimizedLines;
    }
    
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== RESUMEN DE OPTIMIZACIÓN ===\n\n");
        sb.append(String.format("Líneas originales: %d\n", originalLines));
        sb.append(String.format("Líneas optimizadas: %d\n", optimizedLines));
        sb.append(String.format("Líneas reducidas: %d (%.1f%%)\n", 
                getLinesReduced(), 
                (getLinesReduced() * 100.0 / originalLines)));
        sb.append(String.format("Variables eliminadas: %d\n", removedVariables.size()));
        sb.append(String.format("Valores propagados: %d\n", propagatedValues.size()));
        sb.append(String.format("Líneas en blanco eliminadas: %d\n\n", removedBlankLines));
        
        if (!removedVariables.isEmpty()) {
            sb.append("Variables eliminadas (código muerto):\n");
            for (String var : removedVariables) {
                sb.append("  - ").append(var).append("\n");
            }
            sb.append("\n");
        }
        
        if (!propagatedValues.isEmpty()) {
            sb.append("Valores propagados (constantes):\n");
            int count = 0;
            for (Map.Entry<String, String> entry : propagatedValues.entrySet()) {
                sb.append(String.format("  - %s → %s\n", entry.getKey(), entry.getValue()));
                count++;
                if (count >= 10) { // Limitar a 10 para no saturar
                    sb.append(String.format("  ... y %d más\n", propagatedValues.size() - 10));
                    break;
                }
            }
        }
        
        return sb.toString();
    }
}