package simplecalc;

import java.util.ArrayList;
import java.util.List;

public class VariableInfo {
    private String name;
    private String type; // "val" o "var"
    private String dataType; // "Int" o "String"
    private String assignedValue; // Puede ser un literal, una variable, o una expresión
    private int declarationLine;
    private List<Integer> usageLines; // Líneas donde se usa (lee) la variable
    private boolean isConstant; // true si el valor es un literal numérico
    private boolean isDead; // true si nunca se usa después de declararse
    
    public VariableInfo(String name, String type, String dataType, String assignedValue, int declarationLine) {
        this.name = name;
        this.type = type;
        this.dataType = dataType;
        this.assignedValue = assignedValue;
        this.declarationLine = declarationLine;
        this.usageLines = new ArrayList<>();
        this.isConstant = isNumericLiteral(assignedValue);
        this.isDead = false;
    }
    
    public void addUsage(int line) {
        usageLines.add(line);
    }
    
    public boolean isUsed() {
        return !usageLines.isEmpty();
    }
    
    private boolean isNumericLiteral(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    // Getters
    public String getName() { return name; }
    public String getType() { return type; }
    public String getDataType() { return dataType; }
    public String getAssignedValue() { return assignedValue; }
    public int getDeclarationLine() { return declarationLine; }
    public List<Integer> getUsageLines() { return usageLines; }
    public boolean isConstant() { return isConstant; }
    public boolean isDead() { return isDead; }
    
    // Setters
    public void setAssignedValue(String value) { 
        this.assignedValue = value;
        this.isConstant = isNumericLiteral(value);
    }
    public void setDead(boolean dead) { this.isDead = dead; }
    
    @Override
    public String toString() {
        return String.format("%s %s: %s = %s (Línea %d, Usos: %d, Constante: %s, Muerta: %s)",
                type, name, dataType, assignedValue, declarationLine, 
                usageLines.size(), isConstant, isDead);
    }
}
