package simplecalc;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Stack; 
import static simplecalc.Token.TokenType.*;
import java.util.stream.Collectors; 

public class Parser {

    private final List<Token> tokens;
    private int current = 0;
    private List<String> errors = new ArrayList<>();
    private Set<String> declaredVariables = new HashSet<>();
    private Map<String, String> variableTypes = new HashMap<>(); 

    private List<ExpressionData> collectedExpressions = new ArrayList<>();
    
    // Clase auxiliar para almacenar los datos de cada expresión
    public static class ExpressionData { 
        List<Token> infixTokens;
        String prefixExpression;
        List<String> postFixTokensString; 
        List<String> prefixStackSimulation; 
        List<String> quadruples; 
        List<String> quadrupleStackSimulation; 

        public ExpressionData(List<Token> infixTokens) {
            this.infixTokens = new ArrayList<>(infixTokens); 
            this.prefixExpression = "";
            this.postFixTokensString = new ArrayList<>();
            this.prefixStackSimulation = new ArrayList<>(); 
            this.quadruples = new ArrayList<>();
            this.quadrupleStackSimulation = new ArrayList<>();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("  Original (Infija): ").append(Parser.tokensToString(infixTokens)).append("\n"); 
            sb.append("  Notación prefija: ").append(String.join(" ", postFixTokensString)).append("\n"); 
            sb.append("  Simulación Pila (Infija a Prefija):\n");
            if (prefixStackSimulation.isEmpty()) { 
                sb.append("    (No aplica para expresiones de un solo operando o sin operadores complejos)\n");
            } else {
                for (String step : prefixStackSimulation) {
                    sb.append("    ").append(step).append("\n");
                }
            }
            
            sb.append("  Prefija (desde Postfija): ").append(prefixExpression).append("\n"); 
            
            sb.append("  Cuádruplos (Tres Direcciones):\n");
            if (quadruples.isEmpty()) {
                 sb.append("    (No aplica para expresiones de un solo operando o errores)\n");
            } else {
                for (String quad : quadruples) {
                    sb.append("    ").append(quad).append("\n");
                }
            }
            
            sb.append("  Simulación Pila (Generación Cuádruplos):\n");
            if (quadrupleStackSimulation.isEmpty()) {
                sb.append("    (No aplica para expresiones de un solo operando o errores)\n");
            } else {
                for (String step : quadrupleStackSimulation) {
                    sb.append("    ").append(step).append("\n");
                }
            }
            return sb.toString();
        }
    }

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public List<String> getErrors() {
        return errors;
    }

    public List<ExpressionData> getCollectedExpressions() {
        return collectedExpressions;
    }

    public boolean parse() {
        current = 0;
        errors.clear();
        declaredVariables.clear();
        variableTypes.clear(); 
        collectedExpressions.clear(); 
        
        try {
            programa();
        } catch (SyntaxError e) {
            return false;
        }
        return errors.isEmpty();
    }
    
    public List<Token> getTokens() {
        return tokens;
    }

    private void programa() {
        consume(FUN_KEYWORD, "Se esperaba 'fun' al inicio del programa.");
        consumeOptionalEOLs();
        consume(ID, "Se esperaba 'main' después de 'fun'.");
        consumeOptionalEOLs();
        consume(PAREN_IZQ, "Se esperaba '(' después de 'main'.");
        consumeOptionalEOLs();
        consume(PAREN_DER, "Se esperaba ')' después de '('.");
        consumeOptionalEOLs();
        consume(LLAVE_IZQ, "Se esperaba '{' después de 'main()'.");
        cuerpo_programa();
        consume(LLAVE_DER, "Se esperaba '}' para cerrar el cuerpo del programa.");
        consumeOptionalEOLs();
        if (!check(EOF)) {
            error(peek(), "Error al final de la entrada.",
                    "Falta el token de fin de archivo (EOF) o hay tokens extra.");
        } else {
            consume(EOF, "Se esperaba el fin de la entrada después de '}'.");
        }
    }

    private void cuerpo_programa() {
        consumeOptionalEOLs();
        while (!check(LLAVE_DER) && !isAtEnd()) {
            if (peek().type == ERROR) {
                String lexerErrorMessage = peek().errorMessage != null ? peek().errorMessage : peek().lexeme + " (Caracter inesperado)";
                errors.add(String.format("[Línea %d, Col %d] Error léxico: %s (token ignorado)",
                        peek().line, peek().column, lexerErrorMessage));
                advance();
                continue;
            }
            if (peek().type == EOL) {
                advance();
                continue;
            }
            sentencia();
        }
        consumeOptionalEOLs();
    }

    private void sentencia() {
        System.out.println("DEBUG: Entrando a sentencia(), peek()=" + peek().type + ", lexema='" + peek().lexeme + "'");

        if (check(VAL_KEYWORD) || check(VAR_KEYWORD)) {
            System.out.println("DEBUG: sentencia: VAL/VAR -> declaracion_stmt()");
            declaracion_stmt();
        } else if (check(ID)) {
            if (current + 1 < tokens.size() && tokens.get(current + 1).type == ASIGNACION) {
                System.out.println("DEBUG: sentencia: ID seguido de ASIGNACION -> asignacion_stmt()");
                asignacion_stmt();
            } else {
                error(peek(), "Sentencia inválida comenzando con ID '" + peek().lexeme + "'.",
                        "Un identificador debe ser parte de una asignación (ej: variable = valor).");
                synchronizeToStatementBoundary();
            }
        } else if (check(READLINE_KEYWORD)) {
            error(peek(), "Sentencia inválida: 'readLine()' debe ser asignado a una variable.",
                        "La función 'readLine()' debe utilizarse como parte de una asignación (ej. 'val x = readLine()').");
            advance(); 
            if (check(PAREN_IZQ)) { 
                advance();
                if (check(PAREN_DER)) {
                    advance();
                }
            }
            consumeOptionalEOLs();
            synchronizeToStatementBoundary();
        } else if (check(PRINT_KEYWORD)) {
            System.out.println("DEBUG: sentencia: PRINT_KEYWORD -> salida_stmt()");
            salida_stmt();
        } else if (check(IF_KEYWORD)) {
            System.out.println("DEBUG: sentencia: IF_KEYWORD -> if_stmt()");
            if_stmt();
        } else if (check(WHILE_KEYWORD)) {
            System.out.println("DEBUG: sentencia: WHILE_KEYWORD -> while_stmt()");
            while_stmt();
        } else if (check(FOR_KEYWORD)) {
            System.out.println("DEBUG: sentencia: FOR_KEYWORD -> for_stmt()");
            for_stmt();
        } else if (peek().type != LLAVE_DER && peek().type != EOF && peek().type != EOL && peek().type != ERROR) {
            error(peek(), "Sentencia inválida o no reconocida.",
                    "Se esperaba 'val', 'var', 'readLine', 'print', 'if', 'while', 'for', una asignación, o fin de bloque '}'.");
            synchronizeToStatementBoundary();
        }
        System.out.println("DEBUG: Saliendo de sentencia(), current ahora apunta a: " + (isAtEnd() ? "EOF" : peek().type));
    }

    private void declaracion_stmt() {
        Token declarationType = advance(); 
        Token varNameToken = consume(ID, "Se esperaba un nombre de variable después de '" + declarationType.lexeme + "'.");
        
        if (declaredVariables.contains(varNameToken.lexeme)) {
             addSemanticError(varNameToken, "Redeclaración de variable.",
                           "La variable '" + varNameToken.lexeme + "' ya ha sido declarada.");
        }

        consume(DOS_PUNTOS, "Se esperaba ':' después del nombre de variable '" + varNameToken.lexeme + "'.");
        
        Token typeToken = consume(ID, "Se esperaba un tipo (ej. 'Int', 'String') después de ':'.");
        if (!isValidType(typeToken.lexeme)) {
            addSemanticError(typeToken, "Tipo de dato no reconocido: '" + typeToken.lexeme + "'.",
                          "Se esperaba un tipo de dato válido como 'Int' o 'String'.");
        }

        consume(ASIGNACION, "Se esperaba '=' después del tipo '" + typeToken.lexeme + "'.");
        
        // --- Expresión de asignación ---
        int exprStart = current; 
        String declaredType = typeToken.lexeme;
        String assignedExpressionType = expresion_aritmetica(); 
        int exprEnd = current; 

        checkTypeCompatibility(declaredType, assignedExpressionType, varNameToken);

        declaredVariables.add(varNameToken.lexeme);
        variableTypes.put(varNameToken.lexeme, declaredType);
        System.out.println("DEBUG declaracion_stmt: Declarando variable: " + varNameToken.lexeme + " de tipo: " + declaredType);

        // SOLO recolectar si es una expresión aritmética válida
        if (!assignedExpressionType.equals("Unknown") && !assignedExpressionType.equals("ErrorType")) {
            List<Token> subExprTokens = tokens.subList(exprStart, exprEnd);
            if (hasArithmeticOperators(subExprTokens) || isSingleOperandArithmeticExpression(subExprTokens)) { 
                collectExpression(subExprTokens, assignedExpressionType, varNameToken.lexeme); // Pasar el nombre de la variable para la asignación final
            }
        }

        consumeOptionalEOLs();
    }

    private void asignacion_stmt() {
        Token varNameToken = consume(ID, "Se esperaba un nombre de variable para la asignación.");
        checkVariableInitialized(varNameToken);

        String declaredType = variableTypes.get(varNameToken.lexeme);
        if (declaredType == null) { 
             addSemanticError(varNameToken, "Error interno: Tipo de variable no encontrado.",
                           "La variable '" + varNameToken.lexeme + "' no tiene un tipo asignado.");
             declaredType = "Unknown";
        }

        consume(ASIGNACION, "Se esperaba '=' después del nombre de variable '" + varNameToken.lexeme + "'.");
        
        // --- Expresión de asignación ---
        int exprStart = current;
        String assignedExpressionType = expresion_aritmetica(); 
        int exprEnd = current;

        checkTypeCompatibility(declaredType, assignedExpressionType, varNameToken);

        System.out.println("DEBUG asignacion_stmt: Asignando a variable: " + varNameToken.lexeme + " con tipo: " + assignedExpressionType);

        // SOLO recolectar si es una expresión aritmética válida
        if (!assignedExpressionType.equals("Unknown") && !assignedExpressionType.equals("ErrorType")) {
            List<Token> subExprTokens = tokens.subList(exprStart, exprEnd);
            if (hasArithmeticOperators(subExprTokens) || isSingleOperandArithmeticExpression(subExprTokens)) {
                collectExpression(subExprTokens, assignedExpressionType, varNameToken.lexeme); // Pasar el nombre de la variable para la asignación final
            }
        }

        consumeOptionalEOLs();
    }

    private void entrada_stmt() {
        consume(READLINE_KEYWORD, "Error interno: Se esperaba 'readLine' para entrada_stmt.");
        consume(PAREN_IZQ, "Se esperaba '(' después de 'readLine'.");
        consume(PAREN_DER, "Se esperaba ')' después de '('.");
        consumeOptionalEOLs();
    }

    private void salida_stmt() {
        consume(PRINT_KEYWORD, "Se esperaba 'print' al inicio de la sentencia de salida.");
        consume(PAREN_IZQ, "Se esperaba '(' después de 'print'.");
        
        // --- Expresión de print ---
        int exprStart = current;
        String exprType = expresion_aritmetica(); 
        int exprEnd = current;
        
        // No generamos cuádruplos de asignación final para print directamente,
        // pero la expresión dentro del print sí puede ser una expresión aritmética.
        // Recolectamos la expresión si es aritmética y válida.
        if (!exprType.equals("Unknown") && !exprType.equals("ErrorType")) {
            List<Token> subExprTokens = tokens.subList(exprStart, exprEnd);
            if (hasArithmeticOperators(subExprTokens) || isSingleOperandArithmeticExpression(subExprTokens)) {
                 collectExpression(subExprTokens, exprType, "print_target"); // Target especial para "print"
            }
        }
        
        consume(PAREN_DER, "Se esperaba ')' para cerrar 'print'."); 
        consumeOptionalEOLs();
    }

    private void if_stmt() {
        consume(IF_KEYWORD, "Error interno: Se esperaba 'if' para if_stmt.");
        consume(PAREN_IZQ, "Se esperaba '(' después de 'if'.");
        
        // --- Condición del if ---
        condicion_simple(); // Las condiciones no generan cuádruplos aritméticos
        
        consume(PAREN_DER, "Se esperaba ')' después de la condición en 'if'.");
        bloque_if();
    }

    private void bloque_if() {
        consume(LLAVE_IZQ, "Se esperaba '{' después de 'if (...)'.");
        consumeOptionalEOLs();

        while (!check(LLAVE_DER) && !isAtEnd()) {
            if (peek().type == ERROR) {
                String lexerErrorMessage = peek().errorMessage != null ? peek().errorMessage : peek().lexeme + " (Caracter inesperado)";
                errors.add(String.format("[Línea %d, Col %d] Error léxico: %s (token ignorado)",
                        peek().line, peek().column, lexerErrorMessage));
                advance();
                continue;
            }
            if (peek().type == EOL) {
                advance();
                continue;
            }
            sentencia();
        }

        consumeOptionalEOLs();
        consume(LLAVE_DER, "Se esperaba '}' para cerrar el bloque del 'if'.");
        consumeOptionalEOLs();
    }

    private void while_stmt() {
        consume(WHILE_KEYWORD, "Se esperaba 'while'.");
        consume(PAREN_IZQ, "Se esperaba '(' después de 'while'.");
        
        condicion_simple(); // Las condiciones no generan cuádruplos aritméticos

        consume(PAREN_DER, "Se esperaba ')' después de la condición en 'while'.");
        bloque_loop();
    }

    private void for_stmt() {
        consume(FOR_KEYWORD, "Se esperaba 'for'.");
        consume(PAREN_IZQ, "Se esperaba '(' después de 'for'.");
        
        Token loopVarName = consume(ID, "Se esperaba un nombre de variable para el bucle 'for'.");
        if (declaredVariables.contains(loopVarName.lexeme)) {
             addSemanticError(loopVarName, "Redeclaración de variable.",
                           "La variable '" + loopVarName.lexeme + "' ya ha sido declarada.");
        }
        declaredVariables.add(loopVarName.lexeme); 
        variableTypes.put(loopVarName.lexeme, "Int"); 
        System.out.println("DEBUG for_stmt: Declarando variable de bucle: " + loopVarName.lexeme + " de tipo: Int");


        consume(IN_KEYWORD, "Se esperaba 'in' después del nombre de la variable en 'for'.");
        
        // --- Expresión de inicio del rango ---
        int rangeStartExprStart = current;
        String rangeStartType = expresion_aritmetica(); 
        int rangeStartExprEnd = current;
        checkTypeCompatibility("Int", rangeStartType, previous());
        if (!rangeStartType.equals("Unknown") && !rangeStartType.equals("ErrorType")) {
            List<Token> subExprTokens = tokens.subList(rangeStartExprStart, rangeStartExprEnd);
            if (hasArithmeticOperators(subExprTokens) || isSingleOperandArithmeticExpression(subExprTokens)) {
                collectExpression(subExprTokens, rangeStartType, "range_start"); // Target especial
            }
        }
        
        consume(DOT_DOT, "Se esperaba '..' para definir el rango en el bucle 'for'.");
        
        // --- Expresión de fin del rango ---
        int rangeEndExprStart = current;
        String rangeEndType = expresion_aritmetica(); 
        int rangeEndExprEnd = current;
        checkTypeCompatibility("Int", rangeEndType, previous());
        if (!rangeEndType.equals("Unknown") && !rangeEndType.equals("ErrorType")) {
            List<Token> subExprTokens = tokens.subList(rangeEndExprStart, rangeEndExprEnd);
            if (hasArithmeticOperators(subExprTokens) || isSingleOperandArithmeticExpression(subExprTokens)) {
                collectExpression(subExprTokens, rangeEndType, "range_end"); // Target especial
            }
        }

        consume(PAREN_DER, "Se esperaba ')' después del rango en 'for'.");
        bloque_loop();
    }

    private void bloque_loop() {
        consume(LLAVE_IZQ, "Se esperaba '{' después de la cabecera del ciclo.");
        consumeOptionalEOLs();

        while (!check(LLAVE_DER) && !isAtEnd()) {
            if (peek().type == ERROR) {
                String lexerErrorMessage = peek().errorMessage != null ? peek().errorMessage : peek().lexeme + " (Caracter inesperado)";
                errors.add(String.format("[Línea %d, Col %d] Error léxico: %s (token ignorado)",
                        peek().line, peek().column, lexerErrorMessage));
                advance();
                continue;
            }
            if (peek().type == EOL) {
                advance();
                continue;
            }
            sentencia();
        }

        consumeOptionalEOLs();
        consume(LLAVE_DER, "Se esperaba '}' para cerrar el bloque del ciclo.");
        consumeOptionalEOLs();
    }


    private void condicion_simple() {
        String leftOperandType = expresion_aritmetica(); 
        
        Token operatorToken = peek(); 
        operador_relacional(); 

        String rightOperandType = expresion_aritmetica(); 

        if (!leftOperandType.equals("Unknown") && !rightOperandType.equals("Unknown") &&
            !leftOperandType.equals("ErrorType") && !rightOperandType.equals("ErrorType")) {
            
            if (!leftOperandType.equals(rightOperandType)) {
                addSemanticError(operatorToken, "Incompatibilidad de tipos en la condición.",
                              "No se puede comparar un tipo '" + leftOperandType + "' con un tipo '" + rightOperandType + "'.");
            }
            if (!leftOperandType.equals("Int") && !leftOperandType.equals("String")) { 
                 addSemanticError(operatorToken, "Tipo de dato no comparable.",
                               "Las comparaciones solo están permitidas para tipos 'Int' o 'String'. Tipo encontrado: '" + leftOperandType + "'.");
            }
        }
    }
    
    private void operador_relacional() {
        if (check(OP_MENOR) || check(OP_MAYOR) || check(OP_IGUAL_IGUAL)) {
            advance();
        } else {
            error(peek(), "Operador relacional inválido.",
                    "Se esperaba '<', '>' o '=='.");
        }
    }

    private void consumeOptionalEOLs() {
        while (match(EOL)) {
        }
    }
    
    private String expresion_aritmetica() {
        String type = termino();
        while (match(OP_SUMA, OP_RESTA)) {
            Token operator = previous(); 
            
            String rightType = termino();

            if (type.equals("Unknown") || rightType.equals("Unknown")) {
                type = "Unknown";
            } else if (type.equals("ErrorType") || rightType.equals("ErrorType")) {
                type = "ErrorType"; 
            } else if (type.equals("Int") && rightType.equals("Int")) {
                type = "Int";
            } else if (type.equals("String") && rightType.equals("String") && operator.type == OP_SUMA) {
                type = "String"; 
            } else {
                addSemanticError(operator, "Incompatibilidad de tipos en operación.",
                              "Operación '" + operator.lexeme + "' entre '" + type + "' y '" + rightType + "' no permitida.");
                type = "ErrorType";
            }
        }
        return type;
    }

    private String termino() {
        String type = factor();
        while (match(OP_MULT, OP_DIV)) {
            Token operator = previous(); 
            String rightType = factor();

            if (type.equals("Unknown") || rightType.equals("Unknown")) {
                type = "Unknown";
            } else if (type.equals("ErrorType") || rightType.equals("ErrorType")) {
                type = "ErrorType";
            } else if (type.equals("Int") && rightType.equals("Int")) {
                type = "Int";
            } else {
                addSemanticError(operator, "Incompatibilidad de tipos en operación.",
                              "Operación '" + operator.lexeme + "' entre '" + type + "' y '" + rightType + "' no permitida.");
                type = "ErrorType";
            }
        }
        return type;
    }

    private String factor() {
        if (check(ERROR)) {
            String lexerErrorMessage = peek().errorMessage != null ? peek().errorMessage : peek().lexeme + " (Caracter inesperado)";
            error(peek(), "Expresión aritmética malformada.",
                    "Cadena literal o secuencia inválida: " + lexerErrorMessage);
            advance();
            return "Unknown"; 
        }

        String type = "Unknown";
        if (check(ID)) {
            Token idToken = peek();
            if (!declaredVariables.contains(idToken.lexeme)) {
                addSemanticError(idToken, "Variable no inicializada: " + idToken.lexeme,
                                "La variable '" + idToken.lexeme + "' se usa antes de declararla.");
                type = "Unknown"; 
            } else {
                type = variableTypes.get(idToken.lexeme); 
                if (type == null) type = "Unknown"; 
            }
            consume(ID, "");
        } else if (check(NUMERO_ENTERO)) {
            type = "Int";
            consume(NUMERO_ENTERO, "");
        } else if (check(CADENA_LITERAL)) {
            type = "String";
            consume(CADENA_LITERAL, "");
        } else if (check(READLINE_KEYWORD)) {
            type = "String"; 
            consume(READLINE_KEYWORD, "");
            consume(PAREN_IZQ, "Se esperaba '(' después de 'readLine'.");
            consume(PAREN_DER, "Se esperaba ')' después de '('.");
        } else if (check(PAREN_IZQ)) {
            consume(PAREN_IZQ, "");
            type = expresion_aritmetica(); 
            consume(PAREN_DER, "Se esperaba ')' para cerrar la expresión entre paréntesis.");
        } else {
            error(peek(), "Expresión aritmética malformada.",
                    "Se esperaba un ID, un número, una cadena literal, readLine(), o una expresión entre paréntesis '(...)'.");
            type = "Unknown";
        }
        return type;
    }

    private Token consume(Token.TokenType type, String message) {
        if (peek().type == ERROR && type != ERROR) {
            String lexerErrorMessage = peek().errorMessage != null ? peek().errorMessage : peek().lexeme + " (Caracter inesperado)";
            errors.add(String.format("[Línea %d, Col %d] Error léxico: %s (token ignorado para análisis sintáctico)",
                    peek().line, peek().column, lexerErrorMessage));
            advance();
            if (check(type)) {
                return advance();
            }
            throw error(peek(), message, message);
        }
        
        if (peek().type == EOL && type != EOL && type != EOF) {
            throw error(peek(), message, message);
        }
        
        if (check(type)) {
            return advance();
        }
        
        throw error(peek(), message, message);
    }

    private SyntaxError error(Token token, String generalMessage, String specificMessageToUser) {
        SyntaxError e = new SyntaxError(token, generalMessage, specificMessageToUser);
        errors.add(e.getMessage());
        return e;
    }

    private void addSemanticError(Token token, String generalMessage, String specificMessageToUser) {
        SemanticError e = new SemanticError(token, generalMessage, specificMessageToUser);
        errors.add(e.getMessage());
    }

    private void checkVariableInitialized(Token name) {
        if (!declaredVariables.contains(name.lexeme)) {
            addSemanticError(name,
                    "Variable no inicializada: " + name.lexeme,
                    "La variable '" + name.lexeme + "' se usa antes de declararla.");
        }
    }
    
    private boolean match(Token.TokenType... types) {
        for (Token.TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(Token.TokenType type) {
        if (isAtEnd()) {
            return type == EOF;
        }
        return peek().type == type;
    }

    private Token advance() {
        if (!isAtEnd()) {
            current++;
        }
        return previous();
    }

    private boolean isAtEnd() {
        if (current >= tokens.size()) {
            System.err.println("ADVERTENCIA PARSER: isAtEnd() llamado con current fuera de límites.");
            return true;
        }
        return peek().type == EOF;
    }

    private Token peek() {
        if (current >= tokens.size()) {
            System.err.println("ADVERTENCIA PARSER: peek() llamado con current (" + current + ") >= tokens.size() (" + tokens.size() + "). Devolviendo el último token.");
            return tokens.get(tokens.size() - 1);
        }
        return tokens.get(current);
    }

    private Token previous() {
        if (current == 0) {
            return tokens.get(0);
        }
        return tokens.get(current - 1);
    }
    
    private boolean isValidType(String typeName) {
        return typeName.equals("Int") || typeName.equals("String");
    }

    private void checkTypeCompatibility(String expectedType, String actualType, Token problemToken) {
        if (expectedType.equals("Unknown") || actualType.equals("Unknown") || actualType.equals("ErrorType")) {
            return; 
        }
        
        if (!expectedType.equals(actualType)) {
            addSemanticError(problemToken, "Incompatibilidad de tipos en asignación.",
                                "Se esperaba un valor de tipo '" + expectedType + "' pero se encontró un tipo '" + actualType + "'.");
        }
    }

    private void synchronizeToStatementBoundary() {
        System.out.println("DEBUG: Entrando a synchronizeToStatementBoundary(), peek() al entrar=" + peek().type);
        advance();
        System.out.println("DEBUG: synchronize: después de consumir token erróneo, peek()=" + peek().type);

        int recoveryLoopGuard = 0;
        final int MAX_RECOVERY_ATTEMPTS = tokens.size() + 5;

        while (!isAtEnd()) {
            System.out.println("DEBUG: synchronize: en bucle, peek()=" + peek().type + ", previous()=" + previous().type);
            recoveryLoopGuard++;
            if (recoveryLoopGuard > MAX_RECOVERY_ATTEMPTS) {
                System.err.println("ERROR PARSER: Posible bucle infinito en synchronizeToStatementBoundary(). Abortando sincronización.");
                errors.add("[ERROR INTERNO] Falla en la recuperación de errores. Demasiados tokens consumidos.");
                while (!isAtEnd()) {
                    advance();
                }
                return;
            }

            switch (peek().type) {
                case VAL_KEYWORD:
                case VAR_KEYWORD:
                case READLINE_KEYWORD:
                case PRINT_KEYWORD:
                case IF_KEYWORD:
                case WHILE_KEYWORD:
                case FOR_KEYWORD:
                case LLAVE_DER:
                case EOF:
                    System.out.println("DEBUG: synchronize: encontrado " + peek().type + ". Retornando.");
                    return;
                default:
            }
            System.out.println("DEBUG: synchronize: avanzando desde " + peek().type);
            advance();
        }
        System.out.println("DEBUG: Salida de synchronizeToStatementBoundary() porque isAtEnd() es true.");
    }

    // --- MÉTODOS PARA CÓDIGO INTERMEDIO ---

    // Prioridades de los operadores para la conversión infija a postfija (Shunting-Yard)
    private int getOperatorPrecedence(Token.TokenType type) {
        switch (type) {
            case OP_SUMA:
            case OP_RESTA:
                return 1;
            case OP_MULT:
            case OP_DIV:
                return 2;
            case PAREN_IZQ: 
                return 0; // Baja precedencia para PAREN_IZQ en la pila (para que no sea sacado prematuramente)
            default:
                return 0; // Para operandos o PAREN_DER (que se maneja diferente)
        }
    }
    
    // CORRECCIÓN: isArithmeticOperator solo para + - * /
    private boolean isArithmeticOperator(Token.TokenType type) { // MODIFICADO
        return type == OP_SUMA || type == OP_RESTA || type == OP_MULT || type == OP_DIV;
    }

    // CORRECCIÓN: hasArithmeticOperators (renombrado para claridad)
    // Determina si una lista de tokens de expresión contiene operadores ARITMÉTICOS
    private boolean hasArithmeticOperators(List<Token> tokens) { // MODIFICADO
        if (tokens.size() <= 1) { 
            return false;
        }
        for (Token t : tokens) {
            if (isArithmeticOperator(t.type)) { // Usar el nuevo isArithmeticOperator
                return true;
            }
        }
        return false;
    }

    // NUEVO: Método para verificar si es un solo operando de tipo aritmético
    private boolean isSingleOperandArithmeticExpression(List<Token> tokens) {
        if (tokens.size() == 1) {
            Token t = tokens.get(0);
            return (t.type == ID || t.type == NUMERO_ENTERO); // Solo ID o NUMERO_ENTERO para aritméticas
        }
        return false;
    }


    private static String tokensToString(List<Token> tokens) { 
        StringBuilder sb = new StringBuilder();
        for (Token t : tokens) {
            sb.append(t.lexeme).append(" ");
        }
        return sb.toString().trim();
    }


    // Clase para el resultado de la conversión (postfija)
    private static class ExpressionConversionResult { 
        List<Token> resultTokens; // Postfija
        List<String> stackSimulation;

        public ExpressionConversionResult(List<Token> resultTokens, List<String> stackSimulation) {
            this.resultTokens = resultTokens;
            this.stackSimulation = stackSimulation;
        }
    }

    // Algoritmo Shunting-Yard para convertir Infija a Postfija
    private ExpressionConversionResult convertToPostfix(List<Token> infixTokens) {
        Stack<Token> operators = new Stack<>();
        List<Token> postfixTokens = new ArrayList<>();
        List<String> stackSimulationSteps = new ArrayList<>();
        
        stackSimulationSteps.add(String.format("%-20s | %-20s | %s", "Pila Operadores", "Salida Postfija", "Procesando"));

        for (Token token : infixTokens) {
            String currentStack = operators.isEmpty() ? "[]" : operators.toString();
            String currentOutput = tokensToString(postfixTokens);
            stackSimulationSteps.add(String.format("%-20s | %-20s | Procesando: %s", currentStack, currentOutput, token.lexeme));

            if (token.type == ID || token.type == NUMERO_ENTERO || token.type == CADENA_LITERAL) {
                postfixTokens.add(token);
                stackSimulationSteps.add(String.format("%-20s | %-20s | Operando a salida: %s", currentStack, tokensToString(postfixTokens), token.lexeme));
            } else if (token.type == PAREN_IZQ) {
                operators.push(token);
                stackSimulationSteps.add(String.format("%-20s | %-20s | Push PAREN_IZQ: %s", operators.toString(), currentOutput, token.lexeme));
            } else if (token.type == PAREN_DER) {
                while (!operators.isEmpty() && operators.peek().type != PAREN_IZQ) {
                    postfixTokens.add(operators.pop());
                    stackSimulationSteps.add(String.format("%-20s | %-20s | Pop operador: %s", operators.toString(), tokensToString(postfixTokens), previous().lexeme));
                }
                if (!operators.isEmpty() && operators.peek().type == PAREN_IZQ) {
                    operators.pop(); // Sacar el PAREN_IZQ de la pila
                    stackSimulationSteps.add(String.format("%-20s | %-20s | Pop PAREN_IZQ", operators.toString(), currentOutput));
                } else {
                    stackSimulationSteps.add(String.format("%-20s | %-20s | ERROR: Paréntesis no balanceados", operators.toString(), currentOutput));
                }
            } else if (isArithmeticOperator(token.type)) { // CORRECCIÓN: usar isArithmeticOperator
                while (!operators.isEmpty() && operators.peek().type != PAREN_IZQ && 
                       getOperatorPrecedence(operators.peek().type) >= getOperatorPrecedence(token.type)) {
                    postfixTokens.add(operators.pop());
                    stackSimulationSteps.add(String.format("%-20s | %-20s | Pop operador (mayor/igual prec.): %s", operators.toString(), tokensToString(postfixTokens), previous().lexeme));
                }
                operators.push(token);
                stackSimulationSteps.add(String.format("%-20s | %-20s | Push operador: %s", operators.toString(), currentOutput, token.lexeme));
            }
            // NOTA: Los operadores relacionales NO se manejan aquí para postfija aritmética
        }

        while (!operators.isEmpty()) {
            if (operators.peek().type == PAREN_IZQ || operators.peek().type == PAREN_DER) {
                 stackSimulationSteps.add(String.format("%-20s | %-20s | ERROR: Paréntesis no balanceados al final", operators.toString(), tokensToString(postfixTokens)));
                 operators.pop(); 
            } else {
                postfixTokens.add(operators.pop());
                stackSimulationSteps.add(String.format("%-20s | %-20s | Pop final: %s", operators.toString(), tokensToString(postfixTokens), previous().lexeme));
            }
        }
        
        stackSimulationSteps.add("Fin de conversión a Postfija.");
        stackSimulationSteps.add("Resultado Postfija: " + tokensToString(postfixTokens));

        return new ExpressionConversionResult(postfixTokens, stackSimulationSteps);
    }
    
    // Método para convertir Postfija a Prefija
    private List<Token> convertPostfixToPrefix(List<Token> postfixTokens) {
        Stack<List<Token>> stack = new Stack<>();

        for (Token token : postfixTokens) {
            if (token.type == ID || token.type == NUMERO_ENTERO || token.type == CADENA_LITERAL) {
                List<Token> operand = new ArrayList<>();
                operand.add(token);
                stack.push(operand);
            } else if (isArithmeticOperator(token.type)) { // CORRECCIÓN: usar isArithmeticOperator
                // Asegurar que la pila tenga suficientes operandos antes de pop
                if (stack.size() < 2) {
                    // Esto indica un problema con la postfija, quizás mal formada.
                    // Podríamos añadir un error, pero el parseo ya lo validó.
                    // Para evitar RuntimeException, simplemente saltamos o manejamos con un mensaje de error.
                    // Por ahora, simplemente retornamos lo que tengamos si la pila está mal.
                    // Una postfija válida siempre tendrá suficientes operandos.
                    return new ArrayList<>(); 
                }
                List<Token> operand2 = stack.pop();
                List<Token> operand1 = stack.pop();
                
                List<Token> expression = new ArrayList<>();
                expression.add(token); // Operador primero
                expression.addAll(operand1);
                expression.addAll(operand2);
                stack.push(expression);
            }
        }
        if (stack.isEmpty()) return new ArrayList<>(); // Si la pila está vacía (expresión vacía o error)
        return stack.pop();
    }

    // Clase para el resultado de la generación de cuádruplos
    private static class QuadrupleGenerationResult {
        List<String> quadruples;
        List<String> stackSimulation;

        public QuadrupleGenerationResult(List<String> quadruples, List<String> stackSimulation) {
            this.quadruples = quadruples;
            this.stackSimulation = stackSimulation;
        }
    }

    // Método para generar cuádruplos desde Postfija
    private QuadrupleGenerationResult generateQuadruples(List<Token> postfixTokens, String finalTarget) { // NUEVO: finalTarget
        List<String> quadruples = new ArrayList<>();
        Stack<String> operandStack = new Stack<>(); 
        List<String> quadrupleStackSimulationSteps = new ArrayList<>();
        int tempVarCounter = 0;

        quadrupleStackSimulationSteps.add(String.format("%-20s | %-15s | %s", "Pila Operandos", "Siguiente Token", "Cuádruplo Generado"));

        for (Token token : postfixTokens) {
            String currentOperandStack = operandStack.isEmpty() ? "[]" : operandStack.toString();
            String generatedQuad = "---";

            if (token.type == ID || token.type == NUMERO_ENTERO || token.type == CADENA_LITERAL) {
                operandStack.push(token.lexeme);
                generatedQuad = "Operando empujado";
            } else if (isArithmeticOperator(token.type)) { // CORRECCIÓN: usar isArithmeticOperator
                if (operandStack.size() < 2) {
                    quadrupleStackSimulationSteps.add(String.format("%-20s | %-15s | ERROR: Pila insuficiente para operador %s. Cuádruplos incompletos.", currentOperandStack, token.lexeme, token.lexeme));
                    return new QuadrupleGenerationResult(new ArrayList<>(), quadrupleStackSimulationSteps); // Detener y reportar error
                }
                String arg2 = operandStack.pop();
                String arg1 = operandStack.pop();
                
                String tempVar = "t" + (++tempVarCounter);
                String quad = String.format("%s = %s %s %s", tempVar, arg1, token.lexeme, arg2); // Formato tX = arg1 op arg2
                quadruples.add(quad);
                operandStack.push(tempVar); 
                generatedQuad = quad;
            }
            quadrupleStackSimulationSteps.add(String.format("%-20s | %-15s | %s", currentOperandStack, token.lexeme, generatedQuad));
        }

        // Al final, si queda un resultado en la pila, es el valor final de la expresión
        if (!operandStack.isEmpty() && finalTarget != null) {
            String finalResult = operandStack.pop();
            // Si el resultado final no es una temporal (ej. es un literal o ID simple)
            // y el target es una variable real, generamos la asignación
            if (!finalResult.startsWith("t") || finalTarget.equals("print_target") || finalTarget.equals("range_start") || finalTarget.equals("range_end")) {
                 quadruples.add(String.format("%s = %s", finalTarget, finalResult));
            } else {
                // Si finalResult ya es una temporal (tX) y finalTarget es un nombre de variable,
                // la asignación es var = tX
                if (!finalTarget.startsWith("t")) { // Evitar tX = tY
                    quadruples.add(String.format("%s = %s", finalTarget, finalResult));
                }
            }
            // Asegurarse de que el target para print, range_start, range_end no sea realmente el nombre de una variable.
            if (finalTarget.equals("print_target")) {
                quadruples.add(0, String.format("PRINT %s", finalResult)); // Cuádruplo especial para print
            } else if (finalTarget.equals("range_start")) {
                quadruples.add(String.format("RANGE_START %s", finalResult)); // Cuádruplo especial para inicio de rango
            } else if (finalTarget.equals("range_end")) {
                quadruples.add(String.format("RANGE_END %s", finalResult)); // Cuádruplo especial para fin de rango
            }
        }
        
        return new QuadrupleGenerationResult(quadruples, quadrupleStackSimulationSteps);
    }
    
    // Método para recolectar y procesar una expresión válida
    // Ahora recibe el target final de la expresión (ej. nombre de variable o "print_target")
    private void collectExpression(List<Token> exprTokens, String expressionType, String finalTarget) { // MODIFICADO
        if (exprTokens.isEmpty() || expressionType.equals("Unknown") || expressionType.equals("ErrorType")) {
            return;
        }

        ExpressionData data = new ExpressionData(exprTokens);
        
        // Expresiones de un solo operando (ej. "a", "10", "\"hola\"")
        // Generar un cuádruplo de asignación a una temporal o directamente al target.
        if (!hasArithmeticOperators(exprTokens) && isSingleOperandArithmeticExpression(exprTokens)) { // Modificado: verificar hasArithmeticOperators
            String operand = exprTokens.get(0).lexeme;
            data.prefixExpression = operand; 
            data.postFixTokensString.add(operand); 
            // Para un solo operando, la "operación" es simplemente asignarlo al target
            if (finalTarget != null) {
                if (finalTarget.equals("print_target")) { // Caso especial para print
                    data.quadruples.add(String.format("PRINT %s", operand));
                    data.quadrupleStackSimulation.add(String.format("[] | %-15s | PRINT %s", operand, operand));
                } else if (finalTarget.equals("range_start")) {
                    data.quadruples.add(String.format("RANGE_START %s", operand));
                    data.quadrupleStackSimulation.add(String.format("[] | %-15s | RANGE_START %s", operand, operand));
                } else if (finalTarget.equals("range_end")) {
                    data.quadruples.add(String.format("RANGE_END %s", operand));
                    data.quadrupleStackSimulation.add(String.format("[] | %-15s | RANGE_END %s", operand, operand));
                } else { // Asignación a una variable
                    data.quadruples.add(String.format("%s = %s", finalTarget, operand));
                    data.quadrupleStackSimulation.add(String.format("[] | %-15s | %s = %s", operand, operand, finalTarget, operand));
                }
            }
        } else { // Expresiones con operadores
            // 1. Convertir a Postfija (Shunting-Yard)
            ExpressionConversionResult postfixResult = convertToPostfix(exprTokens);
            List<Token> postfixTokens = postfixResult.resultTokens;
            data.postFixTokensString = postfixTokens.stream().map(t -> t.lexeme).collect(Collectors.toList());
            data.prefixStackSimulation = postfixResult.stackSimulation; 

            // 2. Convertir Postfija a Prefija
            List<Token> prefixResultTokens = convertPostfixToPrefix(postfixTokens);
            data.prefixExpression = tokensToString(prefixResultTokens);
            
            // 3. Generación de cuádruplos desde la Postfija
            QuadrupleGenerationResult quadResult = generateQuadruples(postfixTokens, finalTarget); // Pasar finalTarget
            data.quadruples = quadResult.quadruples;
            data.quadrupleStackSimulation = quadResult.stackSimulation;
        }
        
        collectedExpressions.add(data);
    }
}