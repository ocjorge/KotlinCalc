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
    private Map<String, Integer> variableValues = new HashMap<>(); // NUEVO: Para simular valores numéricos

    private List<ExpressionData> collectedExpressions = new ArrayList<>();
    
    // Clase auxiliar para almacenar los datos de cada expresión
    public static class ExpressionData { 
        List<Token> infixTokens;
        String prefixExpression;
        List<String> prefixStackSimulation; // Simulación de pila para infija a prefija
        List<String> quadruples; 
        List<String> quadrupleStackSimulation; // Simulación de pila para cuádruplos
        Map<String, Integer> numericResultsSimulation; // NUEVO: Para resultados numéricos

        public ExpressionData(List<Token> infixTokens) {
            this.infixTokens = new ArrayList<>(infixTokens); 
            this.prefixExpression = "";
            this.prefixStackSimulation = new ArrayList<>(); 
            this.quadruples = new ArrayList<>();
            this.quadrupleStackSimulation = new ArrayList<>();
            this.numericResultsSimulation = new HashMap<>(); // Inicializar el mapa
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("  Original (Infija): ").append(Parser.tokensToString(infixTokens)).append("\n"); 
            sb.append("  Simulación Pila (Infija a Prefija):\n");
            if (prefixStackSimulation.isEmpty() || prefixStackSimulation.size() <= 1) { // Mejorar para expresiones simples
                sb.append("    (No aplica / Trivial para esta expresión)\n");
            } else {
                for (String step : prefixStackSimulation) {
                    sb.append("    ").append(step).append("\n");
                }
            }
            
            sb.append("  Prefija: ").append(prefixExpression).append("\n"); 
            
            sb.append("  Cuádruplos (Tres Direcciones):\n");
            if (quadruples.isEmpty()) {
                 sb.append("    (No aplica para esta expresión)\n");
            } else {
                for (String quad : quadruples) {
                    sb.append("    ").append(quad).append("\n");
                }
            }
            
            sb.append("  Simulación Pila (Generación Cuádruplos desde Infija):\n"); 
            if (quadrupleStackSimulation.isEmpty() || quadrupleStackSimulation.size() <= 1) { // Mejorar para expresiones simples
                sb.append("    (No aplica / Trivial para esta expresión)\n");
            } else {
                for (String step : quadrupleStackSimulation) {
                    sb.append("    ").append(step).append("\n");
                }
            }
            
            // NUEVO: Mostrar resultados numéricos
            if (!numericResultsSimulation.isEmpty()) {
                sb.append("  Resultados Numéricos (Simulación):\n");
                numericResultsSimulation.forEach((var, val) -> sb.append(String.format("    %s = %d\n", var, val)));
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
        variableValues.clear(); // Limpiar valores numéricos
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

        // Solo recolectar si es una expresión aritmética válida (tiene operadores o es un ID/NUMERO)
        // Y SOLO si el tipo final es Int (excluir String+String para cuádruplos aritméticos)
        if (!assignedExpressionType.equals("Unknown") && !assignedExpressionType.equals("ErrorType") && assignedExpressionType.equals("Int")) {
            List<Token> subExprTokens = tokens.subList(exprStart, exprEnd);
            if (hasArithmeticOperators(subExprTokens) || isSingleArithmeticOperand(subExprTokens)) { 
                collectExpression(subExprTokens, varNameToken.lexeme); 
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

        // Solo recolectar si es una expresión aritmética válida (tiene operadores o es un ID/NUMERO)
        // Y SOLO si el tipo final es Int
        if (!assignedExpressionType.equals("Unknown") && !assignedExpressionType.equals("ErrorType") && assignedExpressionType.equals("Int")) {
            List<Token> subExprTokens = tokens.subList(exprStart, exprEnd);
            if (hasArithmeticOperators(subExprTokens) || isSingleArithmeticOperand(subExprTokens)) {
                collectExpression(subExprTokens, varNameToken.lexeme); 
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
        
        // Recolectamos la expresión si es aritmética y válida.
        // Solo si el tipo es Int. Si es String, no genera cuádruplos aritméticos.
        if (!exprType.equals("Unknown") && !exprType.equals("ErrorType") && exprType.equals("Int")) {
            List<Token> subExprTokens = tokens.subList(exprStart, exprEnd);
            if (hasArithmeticOperators(subExprTokens) || isSingleArithmeticOperand(subExprTokens)) { 
                 collectExpression(subExprTokens, "print_target"); // Target especial para "print"
            }
        }
        
        consume(PAREN_DER, "Se esperaba ')' para cerrar 'print'."); 
        consumeOptionalEOLs();
    }

    private void if_stmt() {
        consume(IF_KEYWORD, "Error interno: Se esperaba 'if' para if_stmt.");
        consume(PAREN_IZQ, "Se esperaba '(' después de 'if'.");
        
        condicion_simple(); // Las condiciones no generan cuádruplos aritméticos en esta fase
        
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
            if (hasArithmeticOperators(subExprTokens) || isSingleArithmeticOperand(subExprTokens)) { 
                collectExpression(subExprTokens, "range_start"); // Target especial
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
            if (hasArithmeticOperators(subExprTokens) || isSingleArithmeticOperand(subExprTokens)) { 
                collectExpression(subExprTokens, "range_end"); // Target especial
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

    // Prioridades de los operadores para la conversión infija a prefija (procesando de derecha a izquierda)
    // NOTA: Para infija a prefija procesando DE DERECHA A IZQUIERDA, las reglas de precedencia se invierten
    // si el operador es de asociatividad izquierda (como + o -). Pero para la simulación
    // de Shunting-Yard inverso, es más fácil usar la precedencia normal.
    // Usaremos la precedencia normal aquí, pero el algoritmo convertToPrefix lo manejará
    // con su lógica de derecha a izquierda o Shunting-Yard inverso.
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
    
    // Solo operadores aritméticos
    private boolean isArithmeticOperator(Token.TokenType type) { 
        return type == OP_SUMA || type == OP_RESTA || type == OP_MULT || type == OP_DIV;
    }

    // Determina si una lista de tokens de expresión contiene operadores ARITMÉTICOS
    private boolean hasArithmeticOperators(List<Token> tokens) { 
        if (tokens.size() <= 1) { 
            return false;
        }
        for (Token t : tokens) {
            if (isArithmeticOperator(t.type)) { 
                return true;
            }
        }
        return false;
    }

    // Método para verificar si es un solo operando de tipo ARITMÉTICO
    private boolean isSingleArithmeticOperand(List<Token> tokens) { 
        if (tokens.size() == 1) {
            Token t = tokens.get(0);
            return (t.type == ID || t.type == NUMERO_ENTERO); 
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


    // Clase para el resultado de la conversión a PREFIJA (directamente)
    private static class ExpressionConversionResult { 
        List<Token> prefixTokens; // PREFIJA
        List<String> stackSimulation; // Simulación de pila para Infija -> Prefija

        public ExpressionConversionResult(List<Token> prefixTokens, List<String> stackSimulation) {
            this.prefixTokens = prefixTokens;
            this.stackSimulation = stackSimulation;
        }
    }

    // NUEVO ALGORITMO: Convertir Infija a Prefija (procesando de derecha a izquierda)
    private ExpressionConversionResult convertToPrefix(List<Token> infixTokens) {
        Stack<Token> operators = new Stack<>();
        List<Token> prefixTokensReversed = new ArrayList<>(); // Construiremos la prefija invertida
        List<String> stackSimulationSteps = new ArrayList<>();

        stackSimulationSteps.add(String.format("%-20s | %-20s | %s", "Pila Operadores", "Salida (Reversa)", "Procesando Token"));

        // Procesar la expresión infija de DERECHA A IZQUIERDA
        List<Token> reversedInfix = new ArrayList<>(infixTokens);
        java.util.Collections.reverse(reversedInfix);

        for (Token token : reversedInfix) {
            String currentStack = operators.isEmpty() ? "[]" : operators.toString();
            String currentOutput = tokensToString(prefixTokensReversed);
            stackSimulationSteps.add(String.format("%-20s | %-20s | Token: %s", currentStack, currentOutput, token.lexeme));

            if (token.type == ID || token.type == NUMERO_ENTERO || token.type == CADENA_LITERAL) {
                prefixTokensReversed.add(token);
                stackSimulationSteps.add(String.format("%-20s | %-20s | Operando a salida: %s", currentStack, tokensToString(prefixTokensReversed), token.lexeme));
            } else if (isArithmeticOperator(token.type)) {
                // Para infija a prefija de derecha a izquierda, los operadores de igual precedencia se sacan
                // si el de la pila tiene MAYOR precedencia. Si tienen igual precedencia, el de la pila se queda.
                // Esto es para que los operadores se agrupen de derecha a izquierda para la prefija.
                while (!operators.isEmpty() && operators.peek().type != PAREN_IZQ && 
                       getOperatorPrecedence(operators.peek().type) > getOperatorPrecedence(token.type)) { // '>' para asociatividad derecha efectiva
                    Token poppedOperator = operators.pop();
                    prefixTokensReversed.add(poppedOperator);
                    stackSimulationSteps.add(String.format("%-20s | %-20s | Pop operador (mayor prec.): %s", operators.toString(), tokensToString(prefixTokensReversed), poppedOperator.lexeme));
                }
                operators.push(token);
                stackSimulationSteps.add(String.format("%-20s | %-20s | Push operador: %s", operators.toString(), currentOutput, token.lexeme));
            } else if (token.type == PAREN_DER) { // En la expresión invertida, '(' original se vuelve ')'
                operators.push(token);
                stackSimulationSteps.add(String.format("%-20s | %-20s | Push PAREN_DER: %s", operators.toString(), currentOutput, token.lexeme));
            } else if (token.type == PAREN_IZQ) { // En la expresión invertida, ')' original se vuelve '('
                while (!operators.isEmpty() && operators.peek().type != PAREN_DER) {
                    Token poppedOperator = operators.pop();
                    prefixTokensReversed.add(poppedOperator);
                    stackSimulationSteps.add(String.format("%-20s | %-20s | Pop operador: %s", operators.toString(), tokensToString(prefixTokensReversed), poppedOperator.lexeme));
                }
                if (!operators.isEmpty() && operators.peek().type == PAREN_DER) {
                    operators.pop(); // Sacar el PAREN_DER de la pila (que es el PAREN_IZQ original)
                    stackSimulationSteps.add(String.format("%-20s | %-20s | Pop PAREN_DER (para match con PAREN_IZQ)", operators.toString(), currentOutput));
                } else {
                    stackSimulationSteps.add(String.format("%-20s | %-20s | ERROR: Paréntesis no balanceados", operators.toString(), currentOutput));
                }
            }
        }

        // Vaciar la pila de operadores restantes
        while (!operators.isEmpty()) {
            if (operators.peek().type == PAREN_IZQ || operators.peek().type == PAREN_DER) {
                 stackSimulationSteps.add(String.format("%-20s | %-20s | ERROR: Paréntesis no balanceados al final", operators.toString(), tokensToString(prefixTokensReversed)));
                 operators.pop(); 
            } else {
                Token poppedOperator = operators.pop(); 
                prefixTokensReversed.add(poppedOperator);
                stackSimulationSteps.add(String.format("%-20s | %-20s | Pop final: %s", operators.toString(), tokensToString(prefixTokensReversed), poppedOperator.lexeme)); 
            }
        }
        
        // Invertir la lista final para obtener la notación prefija correcta
        List<Token> prefixTokens = new ArrayList<>(prefixTokensReversed);
        java.util.Collections.reverse(prefixTokens);
        
        stackSimulationSteps.add("\nFin de conversión Infija a Prefija.");
        stackSimulationSteps.add("Resultado Prefija: " + tokensToString(prefixTokens));

        return new ExpressionConversionResult(prefixTokens, stackSimulationSteps);
    }
    
    // Clase para el resultado de la generación de cuádruplos
    private static class QuadrupleGenerationResult {
        List<String> quadruples;
        List<String> stackSimulation;
        Map<String, Integer> numericResults; // Para almacenar los valores calculados

        public QuadrupleGenerationResult(List<String> quadruples, List<String> stackSimulation, Map<String, Integer> numericResults) {
            this.quadruples = quadruples;
            this.stackSimulation = stackSimulation;
            this.numericResults = numericResults;
        }
    }

    // NUEVO ALGORITMO: Generar cuádruplos directamente desde Infija (algoritmo de doble pila)
    // También realiza una simulación de cálculo numérico
    private QuadrupleGenerationResult generateQuadruples(List<Token> infixTokens, String finalTarget) { 
        List<String> quadruples = new ArrayList<>();
        Stack<String> operandStack = new Stack<>(); // Guarda operandos (literales, IDs, temporales)
        Stack<Token> operatorStack = new Stack<>(); // Guarda operadores
        List<String> quadrupleStackSimulationSteps = new ArrayList<>();
        Map<String, Integer> currentNumericValues = new HashMap<>(variableValues); // Copiar valores de variables globales
        int tempVarCounter = 0;

        quadrupleStackSimulationSteps.add(String.format("%-20s | %-20s | %-15s | %s", "Pila Operandos", "Pila Operadores", "Token", "Cuádruplo Generado"));

        for (Token token : infixTokens) {
            String opStackState = operatorStack.isEmpty() ? "[]" : operatorStack.toString();
            String valStackState = operandStack.isEmpty() ? "[]" : operandStack.toString();
            String generatedQuad = "---";

            if (token.type == ID || token.type == NUMERO_ENTERO) { // Solo operandos aritméticos (ID, NUMERO)
                operandStack.push(token.lexeme);
                generatedQuad = "Operando a pila: " + token.lexeme;
            } else if (token.type == PAREN_IZQ) {
                operatorStack.push(token);
                generatedQuad = "Push PAREN_IZQ";
            } else if (token.type == PAREN_DER) {
                while (!operatorStack.isEmpty() && operatorStack.peek().type != PAREN_IZQ) {
                    if (operandStack.size() < 2) { 
                        quadrupleStackSimulationSteps.add(String.format("%-20s | %-20s | %-15s | ERROR: Pila insuficiente para operador.", valStackState, opStackState, token.lexeme));
                        return new QuadrupleGenerationResult(new ArrayList<>(), quadrupleStackSimulationSteps, new HashMap<>());
                    }
                    String arg2 = operandStack.pop();
                    String arg1 = operandStack.pop();
                    Token op = operatorStack.pop();
                    String tempVar = "t" + (++tempVarCounter);
                    
                    // Simular cálculo numérico
                    int val1 = getValue(arg1, currentNumericValues);
                    int val2 = getValue(arg2, currentNumericValues);
                    int result = evaluate(val1, val2, op.type);
                    currentNumericValues.put(tempVar, result); // Almacenar resultado temporal
                    
                    String quad = String.format("%s = %s %s %s", tempVar, arg1, op.lexeme, arg2);
                    quadruples.add(quad);
                    operandStack.push(tempVar);
                    generatedQuad = quad + " (Resultado: " + result + ")";
                }
                if (!operatorStack.isEmpty() && operatorStack.peek().type == PAREN_IZQ) {
                    operatorStack.pop(); // Sacar el '(' de la pila
                } else {
                    quadrupleStackSimulationSteps.add(String.format("%-20s | %-20s | %-15s | ERROR: Paréntesis no balanceados.", valStackState, opStackState, token.lexeme));
                    return new QuadrupleGenerationResult(new ArrayList<>(), quadrupleStackSimulationSteps, new HashMap<>());
                }
            } else if (isArithmeticOperator(token.type)) {
                while (!operatorStack.isEmpty() && operatorStack.peek().type != PAREN_IZQ && 
                       getOperatorPrecedence(operatorStack.peek().type) >= getOperatorPrecedence(token.type)) {
                    if (operandStack.size() < 2) { 
                        quadrupleStackSimulationSteps.add(String.format("%-20s | %-20s | %-15s | ERROR: Pila insuficiente para operador.", valStackState, opStackState, token.lexeme));
                        return new QuadrupleGenerationResult(new ArrayList<>(), quadrupleStackSimulationSteps, new HashMap<>());
                    }
                    String arg2 = operandStack.pop();
                    String arg1 = operandStack.pop();
                    Token op = operatorStack.pop();
                    String tempVar = "t" + (++tempVarCounter);

                    // Simular cálculo numérico
                    int val1 = getValue(arg1, currentNumericValues);
                    int val2 = getValue(arg2, currentNumericValues);
                    int result = evaluate(val1, val2, op.type);
                    currentNumericValues.put(tempVar, result); // Almacenar resultado temporal

                    String quad = String.format("%s = %s %s %s", tempVar, arg1, op.lexeme, arg2);
                    quadruples.add(quad);
                    operandStack.push(tempVar);
                    generatedQuad = quad + " (Resultado: " + result + ")";
                }
                operatorStack.push(token); 
            }
            quadrupleStackSimulationSteps.add(String.format("%-20s | %-20s | %-15s | %s", 
                                                            operandStack.isEmpty() ? "[]" : operandStack.toString(), 
                                                            operatorStack.isEmpty() ? "[]" : operatorStack.toString(), 
                                                            token.lexeme, generatedQuad));
        }

        // Vaciar operadores restantes de la pila
        while (!operatorStack.isEmpty()) {
            if (operatorStack.peek().type == PAREN_IZQ || operatorStack.peek().type == PAREN_DER) {
                quadrupleStackSimulationSteps.add(String.format("%-20s | %-20s | %-15s | ERROR: Paréntesis no balanceados al final.", 
                                                                operandStack.isEmpty() ? "[]" : operandStack.toString(), 
                                                                operatorStack.toString(), "FINAL", "---"));
                operatorStack.pop(); 
                continue;
            }
            if (operandStack.size() < 2) {
                quadrupleStackSimulationSteps.add(String.format("%-20s | %-20s | %-15s | ERROR: Pila insuficiente para operador final.", 
                                                                operandStack.isEmpty() ? "[]" : operandStack.toString(), 
                                                                operatorStack.toString(), "FINAL", "---"));
                return new QuadrupleGenerationResult(new ArrayList<>(), quadrupleStackSimulationSteps, new HashMap<>());
            }
            String arg2 = operandStack.pop();
            String arg1 = operandStack.pop();
            Token op = operatorStack.pop();
            String tempVar = "t" + (++tempVarCounter);

            // Simular cálculo numérico
            int val1 = getValue(arg1, currentNumericValues);
            int val2 = getValue(arg2, currentNumericValues);
            int result = evaluate(val1, val2, op.type);
            currentNumericValues.put(tempVar, result); // Almacenar resultado temporal

            String quad = String.format("%s = %s %s %s", tempVar, arg1, op.lexeme, arg2);
            quadruples.add(quad);
            operandStack.push(tempVar);
            
            quadrupleStackSimulationSteps.add(String.format("%-20s | %-20s | %-15s | %s", 
                                                            operandStack.isEmpty() ? "[]" : operandStack.toString(), 
                                                            operatorStack.isEmpty() ? "[]" : operatorStack.toString(), 
                                                            op.lexeme, quad + " (Resultado: " + result + ")"));
        }

        // Asignación final al target si es aplicable y el resultado es numérico
        if (!operandStack.isEmpty()) {
            String finalExpressionResult = operandStack.pop();
            // Asegurarse de que el resultado final es un valor numérico o una temporal que lo contiene
            if (!currentNumericValues.containsKey(finalExpressionResult) && !isNumericLiteral(finalExpressionResult) && !variableValues.containsKey(finalExpressionResult)) {
                // No se puede simular el resultado numérico si no es numérico
                // Esto podría ser un ID sin valor numérico o una cadena.
                 currentNumericValues.put(finalExpressionResult, null); // Indicar que no se pudo calcular
            } else {
                 int finalVal = getValue(finalExpressionResult, currentNumericValues);
                 currentNumericValues.put(finalTarget, finalVal); // El valor final de la expresión se asigna al target.
            }

            if (finalTarget != null) {
                // Si el target es una variable o un destino especial (print, range)
                String finalResultValue = finalExpressionResult;
                if (finalTarget.equals("print_target")) { 
                    quadruples.add(String.format("PRINT %s", finalResultValue)); 
                } else if (finalTarget.equals("range_start")) {
                    quadruples.add(String.format("RANGE_START %s", finalResultValue)); 
                } else if (finalTarget.equals("range_end")) {
                    quadruples.add(String.format("RANGE_END %s", finalResultValue)); 
                } else { // Asignación a una variable
                    quadruples.add(String.format("%s = %s", finalTarget, finalResultValue));
                    // Actualizar el valor de la variable en la tabla de valores
                    try {
                        currentNumericValues.put(finalTarget, getValue(finalResultValue, currentNumericValues));
                    } catch (NumberFormatException e) {
                        // Si no es un número, no se actualiza el valor numérico
                    }
                }
                quadrupleStackSimulationSteps.add(String.format("%-20s | %-20s | %-15s | Asignación final: %s = %s", 
                                                                "[]", "[]", "FINAL", finalTarget, finalExpressionResult));
            }
        }
        
        return new QuadrupleGenerationResult(quadruples, quadrupleStackSimulationSteps, currentNumericValues);
    }
    
    // NUEVO: Método auxiliar para obtener el valor numérico de un operando
    private int getValue(String operand, Map<String, Integer> currentNumericValues) {
        try {
            return Integer.parseInt(operand); // Si es un literal numérico
        } catch (NumberFormatException e) {
            if (currentNumericValues.containsKey(operand)) { // Si es una variable temporal o declarada
                Integer val = currentNumericValues.get(operand);
                return (val != null) ? val : 0; // Devolver 0 si el valor es null (no calculado)
            }
            if (variableValues.containsKey(operand)) { // Si es una variable global declarada
                return variableValues.get(operand);
            }
            // Si es un ID sin valor numérico aún, devolver 0 (o lanzar error si no se espera)
            return 0;
        }
    }

    // NUEVO: Método auxiliar para evaluar una operación
    private int evaluate(int val1, int val2, Token.TokenType opType) {
        switch (opType) {
            case OP_SUMA: return val1 + val2;
            case OP_RESTA: return val1 - val2;
            case OP_MULT: return val1 * val2;
            case OP_DIV: 
                if (val2 == 0) {
                    // Manejo de división por cero - para simulación, podemos devolver 0 o un valor específico
                    System.err.println("ADVERTENCIA: División por cero detectada en simulación.");
                    return 0; 
                }
                return val1 / val2;
            default: return 0; // Operador desconocido
        }
    }

    // NUEVO: Método auxiliar para saber si una cadena es un literal numérico
    private boolean isNumericLiteral(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }


    // Método para recolectar y procesar una expresión válida
    private void collectExpression(List<Token> exprTokens, String finalTarget) { 
        if (exprTokens.isEmpty()) return;

        // Filtramos para asegurarnos de que la expresión sea realmente aritmética y no una simple cadena literal si no es parte de una suma
        boolean isArithmetic = hasArithmeticOperators(exprTokens) || isSingleArithmeticOperand(exprTokens);
        
        // Excluimos las concatenaciones de cadenas del código intermedio aritmético
        boolean isStringConcatenation = false; 
        if (hasArithmeticOperators(exprTokens)) { // Solo para expresiones con operadores
             // Para esta fase, asumimos que hasArithmeticOperators ya filtró los strings correctamente
             // Y que exprType ya fue validado a "Int".
             // Si exprType fuera "String" y el operador fuera "+", sería una concatenación.
             // Pero el filtro de `declaredType.equals("Int")` ya debería manejar esto antes.
             // Aquí confirmamos que si es una cadena, no se procesa aritméticamente.
             String exprTypeCheck = "";
             if (exprTokens.size() == 1 && exprTokens.get(0).type == CADENA_LITERAL) {
                 exprTypeCheck = "String";
             } else if (isSingleArithmeticOperand(exprTokens) || hasArithmeticOperators(exprTokens)) {
                 exprTypeCheck = "Int"; // Asumimos que la expresión ya fue validada como Int
             }

             if (exprTypeCheck.equals("String") && hasArithmeticOperators(exprTokens)) {
                 // Si la expresión es String y tiene operadores, es una concatenación.
                 // EXCLUIR de cuádruplos aritméticos según la petición.
                 return;
             }
        }
        
        // Si no es una expresión aritmética (simple o compleja) no la procesamos.
        // Las cadenas literales sueltas no generan cuádruplos.
        if (!isArithmetic) { 
            // Caso especial: print de una cadena literal simple
            if (exprTokens.size() == 1 && exprTokens.get(0).type == CADENA_LITERAL && finalTarget.equals("print_target")) {
                ExpressionData data = new ExpressionData(exprTokens);
                String operand = exprTokens.get(0).lexeme;
                data.prefixExpression = operand;
                data.quadruples.add(String.format("PRINT %s", operand));
                data.quadrupleStackSimulation.add(String.format("[] | [] | %-15s | PRINT %s", operand, operand));
                collectedExpressions.add(data);
                return;
            }
            return;
        }

        ExpressionData data = new ExpressionData(exprTokens);
        
        // 1. Convertir Infija a Prefija (procesando de derecha a izquierda)
        ExpressionConversionResult prefixConversionResult = convertToPrefix(exprTokens);
        data.prefixExpression = tokensToString(prefixConversionResult.prefixTokens);
        data.prefixStackSimulation = prefixConversionResult.stackSimulation; 
        
        // 2. Generación de cuádruplos directamente desde la Infija
        QuadrupleGenerationResult quadResult = generateQuadruples(exprTokens, finalTarget); 
        data.quadruples = quadResult.quadruples;
        data.quadrupleStackSimulation = quadResult.stackSimulation;
        data.numericResultsSimulation = quadResult.numericResults; // Asignar resultados numéricos

        collectedExpressions.add(data);
    }
}