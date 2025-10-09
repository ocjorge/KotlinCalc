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
    private Map<String, Integer> variableValues = new HashMap<>(); // NUEVO: Para almacenar los valores numéricos actuales de las variables

    private List<ExpressionData> collectedExpressions = new ArrayList<>();

    // Clase auxiliar para almacenar los datos de cada expresión
    public static class ExpressionData {

        List<Token> infixTokens;
        String prefixExpression;
        List<String> prefixStackSimulation;
        List<String> quadruples;
        List<String> quadrupleStackSimulation;
        Map<String, Integer> numericResultsSimulation; // Para resultados numéricos de ESTA expresión y temporales
        int lineNumber;

        public ExpressionData(List<Token> infixTokens, int lineNumber) {
            this.infixTokens = new ArrayList<>(infixTokens);
            this.prefixExpression = "";
            this.prefixStackSimulation = new ArrayList<>();
            this.quadruples = new ArrayList<>();
            this.quadrupleStackSimulation = new ArrayList<>();
            this.numericResultsSimulation = new HashMap<>();
            this.lineNumber = lineNumber;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("  Original (Infija): ").append(Parser.tokensToString(infixTokens)).append("\n");
            sb.append("  Pila (Infija a Prefija):\n");
            if (prefixStackSimulation.isEmpty() || prefixStackSimulation.size() <= 1) {
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

            sb.append("  Pila (Generación Cuádruplos desde Infija):\n");
            if (quadrupleStackSimulation.isEmpty() || quadrupleStackSimulation.size() <= 1) {
                sb.append("    (No aplica / Trivial para esta expresión)\n");
            } else {
                for (String step : quadrupleStackSimulation) {
                    sb.append("    ").append(step).append("\n");
                }
            }

            if (!numericResultsSimulation.isEmpty()) {
                sb.append("  Resultados Numéricos:\n");
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
        variableValues.clear(); // ¡Reiniciar valores de variables al inicio de cada parseo!
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
                int lineNumber = subExprTokens.isEmpty() ? varNameToken.line : subExprTokens.get(0).line;
                collectExpression(subExprTokens, varNameToken.lexeme,lineNumber);
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
                int lineNumber = subExprTokens.isEmpty() ? varNameToken.line : subExprTokens.get(0).line;
                collectExpression(subExprTokens, varNameToken.lexeme,lineNumber);
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
        // Solo si el tipo es Int o una cadena literal simple (para PRINT).
        if (!exprType.equals("Unknown") && !exprType.equals("ErrorType") && (exprType.equals("Int") || (exprType.equals("String") && tokens.subList(exprStart, exprEnd).size() == 1 && tokens.subList(exprStart, exprEnd).get(0).type == CADENA_LITERAL))) {
            List<Token> subExprTokens = tokens.subList(exprStart, exprEnd);
            if (hasArithmeticOperators(subExprTokens) || isSingleArithmeticOperand(subExprTokens) || (subExprTokens.size() == 1 && subExprTokens.get(0).type == CADENA_LITERAL)) {
                int lineNumber = subExprTokens.get(0).line;
                collectExpression(subExprTokens, "print_target",lineNumber); // Target especial para "print"
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
        if (!rangeStartType.equals("Unknown") && !rangeStartType.equals("ErrorType") && rangeStartType.equals("Int")) {
            List<Token> subExprTokens = tokens.subList(rangeStartExprStart, rangeStartExprEnd);
            if (hasArithmeticOperators(subExprTokens) || isSingleArithmeticOperand(subExprTokens)) {
                int lineNumber = subExprTokens.get(0).line;
                collectExpression(subExprTokens, "range_start",lineNumber); // Target especial
            }
        }

        consume(DOT_DOT, "Se esperaba '..' para definir el rango en el bucle 'for'.");

        // --- Expresión de fin del rango ---
        int rangeEndExprStart = current;
        String rangeEndType = expresion_aritmetica();
        int rangeEndExprEnd = current;
        checkTypeCompatibility("Int", rangeEndType, previous());
        if (!rangeEndType.equals("Unknown") && !rangeEndType.equals("ErrorType") && rangeEndType.equals("Int")) {
            List<Token> subExprTokens = tokens.subList(rangeEndExprStart, rangeEndExprEnd);
            if (hasArithmeticOperators(subExprTokens) || isSingleArithmeticOperand(subExprTokens)) {
                int lineNumber = subExprTokens.get(0).line;
                collectExpression(subExprTokens, "range_end",lineNumber); // Target especial
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

        if (!leftOperandType.equals("Unknown") && !rightOperandType.equals("Unknown")
                && !leftOperandType.equals("ErrorType") && !rightOperandType.equals("ErrorType")) {

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
                if (type == null) {
                    type = "Unknown";
                }
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

            if (token.type == ID || token.type == NUMERO_ENTERO) { // Solo operandos aritméticos relevantes
                prefixTokensReversed.add(token);
                stackSimulationSteps.add(String.format("%-20s | %-20s | Operando a salida: %s", currentStack, tokensToString(prefixTokensReversed), token.lexeme));
            } else if (isArithmeticOperator(token.type)) {
                // Para infija a prefija de derecha a izquierda, los operadores de igual precedencia se sacan
                // si el de la pila tiene MAYOR precedencia. Si tienen igual precedencia, el de la pila se queda.
                // Esto es para que los operadores se agrupen de derecha a izquierda para la prefija.
                while (!operators.isEmpty() && operators.peek().type != PAREN_IZQ
                        && getOperatorPrecedence(operators.peek().type) > getOperatorPrecedence(token.type)) { // '>' para asociatividad derecha efectiva
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
        Map<String, Integer> currentNumericValues = new HashMap<>(variableValues); // Copiar valores PERSISTENTES del parser

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
                    int val1 = getNumericValueForSimulation(arg1, currentNumericValues); // Usar auxiliar
                    int val2 = getNumericValueForSimulation(arg2, currentNumericValues); // Usar auxiliar
                    int result = evaluate(val1, val2, op.type);
                    currentNumericValues.put(tempVar, result); // Almacenar resultado temporal

                    String quad = String.format("%s = %s %s %s", tempVar, op.lexeme, arg1, arg2);
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
                while (!operatorStack.isEmpty() && operatorStack.peek().type != PAREN_IZQ
                        && getOperatorPrecedence(operatorStack.peek().type) >= getOperatorPrecedence(token.type)) {
                    if (operandStack.size() < 2) {
                        quadrupleStackSimulationSteps.add(String.format("%-20s | %-20s | %-15s | ERROR: Pila insuficiente para operador.", valStackState, opStackState, token.lexeme));
                        return new QuadrupleGenerationResult(new ArrayList<>(), quadrupleStackSimulationSteps, new HashMap<>());
                    }
                    String arg2 = operandStack.pop();
                    String arg1 = operandStack.pop();
                    Token op = operatorStack.pop();
                    String tempVar = "t" + (++tempVarCounter);

                    // Simular cálculo numérico
                    int val1 = getNumericValueForSimulation(arg1, currentNumericValues); // Usar auxiliar
                    int val2 = getNumericValueForSimulation(arg2, currentNumericValues); // Usar auxiliar
                    int result = evaluate(val1, val2, op.type);
                    currentNumericValues.put(tempVar, result); // Almacenar resultado temporal

                    String quad = String.format("%s = %s %s %s", tempVar, op.lexeme, arg1, arg2);
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
            int val1 = getNumericValueForSimulation(arg1, currentNumericValues); // Usar auxiliar
            int val2 = getNumericValueForSimulation(arg2, currentNumericValues); // Usar auxiliar
            int result = evaluate(val1, val2, op.type);
            currentNumericValues.put(tempVar, result); // Almacenar resultado temporal

            String quad = String.format("%s = %s %s %s", tempVar, op.lexeme, arg1, arg2);
            quadruples.add(quad);
            operandStack.push(tempVar);

            quadrupleStackSimulationSteps.add(String.format("%-20s | %-20s | %-15s | %s",
                    operandStack.isEmpty() ? "[]" : operandStack.toString(),
                    operatorStack.isEmpty() ? "[]" : operatorStack.toString(),
                    op.lexeme, quad + " (Resultado: " + result + ")"));
        }

        // Asignación final al target si es aplicable
        if (!operandStack.isEmpty()) {
            String finalExpressionResult = operandStack.pop();
            if (finalTarget != null) {
                // Actualizar el valor de la variable en la tabla de valores PERSISTENTE del parser
                int finalCalculatedValue = getNumericValueForSimulation(finalExpressionResult, currentNumericValues);

                if (finalTarget.equals("print_target")) {
                    quadruples.add(String.format("PRINT %s", finalExpressionResult));
                    // No se actualiza variableValues para print_target
                } else if (finalTarget.equals("range_start")) {
                    quadruples.add(String.format("RANGE_START %s", finalExpressionResult));
                } else if (finalTarget.equals("range_end")) {
                    quadruples.add(String.format("RANGE_END %s", finalExpressionResult));
                } else { // Es una variable a la que se asigna
                    quadruples.add(String.format("%s = %s", finalTarget, finalExpressionResult));
                    variableValues.put(finalTarget, finalCalculatedValue); // ¡Actualizar el valor persistente!
                }
                quadrupleStackSimulationSteps.add(String.format("%-20s | %-20s | %-15s | Asignación final: %s = %s",
                        "[]", "[]", "FINAL", finalTarget, finalExpressionResult));
            }
        } else if (!infixTokens.isEmpty() && quadruples.isEmpty() && finalTarget != null) {
            // Este caso es para expresiones de un solo operando (ID o NUMERO) que no se procesaron en el loop principal
            // Ej: `val x: Int = 10` o `print(y)`
            String operand = tokensToString(infixTokens);
            int finalCalculatedValue = getNumericValueForSimulation(operand, currentNumericValues);

            if (finalTarget.equals("print_target")) {
                quadruples.add(String.format("PRINT %s", operand));
                quadrupleStackSimulationSteps.add(String.format("[] | [] | %-15s | PRINT %s", operand, operand));
            } else if (finalTarget.equals("range_start")) {
                quadruples.add(String.format("RANGE_START %s", operand));
                quadrupleStackSimulationSteps.add(String.format("[] | [] | %-15s | RANGE_START %s", operand, operand));
            } else if (finalTarget.equals("range_end")) {
                quadruples.add(String.format("RANGE_END %s", operand));
                quadrupleStackSimulationSteps.add(String.format("[] | [] | %-15s | RANGE_END %s", operand, operand));
            } else { // Asignación a una variable
                quadruples.add(String.format("%s = %s", finalTarget, operand));
                variableValues.put(finalTarget, finalCalculatedValue); // ¡Actualizar el valor persistente!
                quadrupleStackSimulationSteps.add(String.format("[] | [] | %-15s | %s = %s", operand, finalTarget, operand));
            }
        }

        return new QuadrupleGenerationResult(quadruples, quadrupleStackSimulationSteps, currentNumericValues);
    }

    // NUEVO: Método auxiliar para obtener el valor numérico de un operando para la simulación
    // Busca en la tabla de valores de la expresión (temporales) o en los valores persistentes de variables
    private int getNumericValueForSimulation(String operand, Map<String, Integer> currentNumericValues) {
        try {
            return Integer.parseInt(operand); // Si es un literal numérico
        } catch (NumberFormatException e) {
            if (currentNumericValues.containsKey(operand)) { // Si es una variable temporal o declarada en esta expresión
                Integer val = currentNumericValues.get(operand);
                return (val != null) ? val : 0; // Devolver 0 si el valor es null (no calculado o error)
            }
            if (variableValues.containsKey(operand)) { // Si es una variable declarada con valor persistente
                Integer val = variableValues.get(operand);
                return (val != null) ? val : 0; // Devolver 0 si el valor es null
            }
            // Si es un ID sin valor numérico aún, devolver 0 (o lanzar error si no se espera)
            return 0; // Podrías lanzar un SemanticError si un ID no tiene valor
        }
    }

    // NUEVO: Método auxiliar para evaluar una operación
    private int evaluate(int val1, int val2, Token.TokenType opType) {
        switch (opType) {
            case OP_SUMA:
                return val1 + val2;
            case OP_RESTA:
                return val1 - val2;
            case OP_MULT:
                return val1 * val2;
            case OP_DIV:
                if (val2 == 0) {
                    System.err.println("ADVERTENCIA: División por cero detectada en simulación.");
                    return 0;
                }
                return val1 / val2;
            default:
                return 0; // Operador desconocido
        }
    }

    // Método para recolectar y procesar una expresión válida
    private void collectExpression(List<Token> exprTokens, String finalTarget,int lineNumber) {
        if (exprTokens.isEmpty()) {
            return;
        }

        boolean isArithmetic = hasArithmeticOperators(exprTokens) || isSingleArithmeticOperand(exprTokens);

        if (!isArithmetic) {
            // Caso especial: print de una cadena literal simple
            if (exprTokens.size() == 1 && exprTokens.get(0).type == CADENA_LITERAL && finalTarget.equals("print_target")) {
                ExpressionData data = new ExpressionData(exprTokens,lineNumber);
                String operand = exprTokens.get(0).lexeme;
                data.prefixExpression = operand;
                data.quadruples.add(String.format("PRINT %s", operand));
                data.quadrupleStackSimulation.add(String.format("[] | [] | %-15s | PRINT %s", operand, operand));
                collectedExpressions.add(data);
                return;
            }
            return; // No procesar si no es aritmética ni el caso especial de print de cadena.
        }

        ExpressionData data = new ExpressionData(exprTokens,lineNumber);

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
