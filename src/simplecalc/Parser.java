package simplecalc;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Stack; // Necesario para el algoritmo de conversión
import static simplecalc.Token.TokenType.*;

public class Parser {

    private final List<Token> tokens;
    private int current = 0;
    private List<String> errors = new ArrayList<>();
    private Set<String> declaredVariables = new HashSet<>();
    private Map<String, String> variableTypes = new HashMap<>(); // Variable name -> Type String ("Int", "String")

    // NUEVO: Lista para almacenar las expresiones aritméticas válidas encontradas
    private List<ExpressionData> collectedExpressions = new ArrayList<>();
    
    // Clase auxiliar para almacenar los datos de cada expresión
    public static class ExpressionData {
        List<Token> infixTokens;
        String prefixExpression;
        List<String> stackSimulation; // Para la simulación de la pila
        List<String> quadruples; // Para los cuádruplos

        public ExpressionData(List<Token> infixTokens) {
            this.infixTokens = new ArrayList<>(infixTokens); // Copia profunda
            this.prefixExpression = "";
            this.stackSimulation = new ArrayList<>();
            this.quadruples = new ArrayList<>();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("  Original (Infija): ").append(tokensToString(infixTokens)).append("\n");
            sb.append("  Prefija:           ").append(prefixExpression).append("\n");
            sb.append("  Simulación Pila:\n");
            for (String step : stackSimulation) {
                sb.append("    ").append(step).append("\n");
            }
            sb.append("  Cuádruplos:\n");
            for (String quad : quadruples) {
                sb.append("    ").append(quad).append("\n");
            }
            return sb.toString();
        }

        private String tokensToString(List<Token> tokens) {
            StringBuilder sb = new StringBuilder();
            for (Token t : tokens) {
                if (t.type != EOL && t.type != EOF) {
                    sb.append(t.lexeme).append(" ");
                }
            }
            return sb.toString().trim();
        }
    }

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public List<String> getErrors() {
        return errors;
    }

    // NUEVO: Método para obtener los datos de las expresiones para la GUI
    public List<ExpressionData> getCollectedExpressions() {
        return collectedExpressions;
    }

    public boolean parse() {
        current = 0;
        errors.clear();
        declaredVariables.clear();
        variableTypes.clear(); 
        collectedExpressions.clear(); // Limpiar expresiones recolectadas en cada parseo
        
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
                System.out.println("DEBUG: sentencia: ID no seguido de ASIGNACION -> error");
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
        Token startToken = peek(); // Capturar el token de inicio de la expresión
        int startExprIndex = current; // Posición de inicio de la expresión
        
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
        int exprStart = current; // Inicio de la expresión
        String declaredType = typeToken.lexeme;
        String assignedExpressionType = expresion_aritmetica(); 
        int exprEnd = current; // Fin de la expresión

        checkTypeCompatibility(declaredType, assignedExpressionType, varNameToken);

        declaredVariables.add(varNameToken.lexeme);
        variableTypes.put(varNameToken.lexeme, declaredType);
        System.out.println("DEBUG declaracion_stmt: Declarando variable: " + varNameToken.lexeme + " de tipo: " + declaredType);

        // NUEVO: Si la expresión es válida, la recolectamos
        if (!assignedExpressionType.equals("Unknown") && !assignedExpressionType.equals("ErrorType")) {
            collectExpression(tokens.subList(exprStart, exprEnd));
        }

        consumeOptionalEOLs();
    }

    private void asignacion_stmt() {
        Token startToken = peek(); // Capturar el token de inicio de la expresión
        int startExprIndex = current; // Posición de inicio de la expresión
        
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
        int exprStart = current; // Inicio de la expresión
        String assignedExpressionType = expresion_aritmetica(); 
        int exprEnd = current; // Fin de la expresión

        checkTypeCompatibility(declaredType, assignedExpressionType, varNameToken);

        System.out.println("DEBUG asignacion_stmt: Asignando a variable: " + varNameToken.lexeme + " con tipo: " + assignedExpressionType);

        // NUEVO: Si la expresión es válida, la recolectamos
        if (!assignedExpressionType.equals("Unknown") && !assignedExpressionType.equals("ErrorType")) {
            collectExpression(tokens.subList(exprStart, exprEnd));
        }

        consumeOptionalEOLs();
    }

    private void entrada_stmt() {
        // Not used as a standalone statement; handled in assignment or declaration.
        // This method exists for completeness if we allowed standalone input in the future.
        consume(READLINE_KEYWORD, "Error interno: Se esperaba 'readLine' para entrada_stmt.");
        consume(PAREN_IZQ, "Se esperaba '(' después de 'readLine'.");
        consume(PAREN_DER, "Se esperaba ')' después de '('.");
        consumeOptionalEOLs();
    }

    private void salida_stmt() {
        consume(PRINT_KEYWORD, "Se esperaba 'print' al inicio de la sentencia de salida.");
        consume(PAREN_IZQ, "Se esperaba '(' después de 'print'.");
        
        // --- Expresión de print ---
        int exprStart = current; // Inicio de la expresión
        String exprType = expresion_aritmetica(); // El tipo de lo que se imprime
        int exprEnd = current; // Fin de la expresión
        
        // NUEVO: Recolectar la expresión de print si es válida
        if (!exprType.equals("Unknown") && !exprType.equals("ErrorType")) {
            collectExpression(tokens.subList(exprStart, exprEnd));
        }
        
        consume(PAREN_DER, "Se esperaba ')' para cerrar 'print'."); 
        consumeOptionalEOLs();
    }

    private void if_stmt() {
        consume(IF_KEYWORD, "Error interno: Se esperaba 'if' para if_stmt.");
        consume(PAREN_IZQ, "Se esperaba '(' después de 'if'.");
        
        // --- Condición del if ---
        int exprStart = current; // Inicio de la expresión de condición
        condicion_simple(); // Esto ya hace su propio análisis de tipos
        int exprEnd = current; // Fin de la expresión de condición
        
        // NUEVO: Recolectar la expresión de condición si es válida
        // No hay un tipo de retorno directo de condicion_simple, se basa en los errores internos.
        // Podríamos modificar condicion_simple para devolver un boolean indicando validez.
        // Por simplicidad, solo recolectamos si no hubo errores en ese rango.
        // Esto es un placeholder; la validez real viene de `errors` global.
        // Si queremos ser más precisos, `condicion_simple` debería devolver si la expresión fue parseada sin errores.
        // Por ahora, si no hubo errores semánticos para la condición, la recolectamos.
        // collectExpression(tokens.subList(exprStart, exprEnd)); // Esto se hará si hay una forma más robusta de validar la expresión de condición individualmente.

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
        
        // --- Condición del while ---
        int exprStart = current; // Inicio de la expresión de condición
        condicion_simple(); // Esto ya hace su propio análisis de tipos
        int exprEnd = current; // Fin de la expresión de condición
        // collectExpression(tokens.subList(exprStart, exprEnd)); // Similar al if_stmt

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
            collectExpression(tokens.subList(rangeStartExprStart, rangeStartExprEnd));
        }
        
        consume(DOT_DOT, "Se esperaba '..' para definir el rango en el bucle 'for'.");
        
        // --- Expresión de fin del rango ---
        int rangeEndExprStart = current;
        String rangeEndType = expresion_aritmetica(); 
        int rangeEndExprEnd = current;
        checkTypeCompatibility("Int", rangeEndType, previous());
        if (!rangeEndType.equals("Unknown") && !rangeEndType.equals("ErrorType")) {
            collectExpression(tokens.subList(rangeEndExprStart, rangeEndExprEnd));
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
        // --- Operando izquierdo de la condición ---
        int leftExprStart = current;
        String leftOperandType = expresion_aritmetica(); 
        int leftExprEnd = current;
        
        Token operatorToken = peek(); 
        operador_relacional(); 

        // --- Operando derecho de la condición ---
        int rightExprStart = current;
        String rightOperandType = expresion_aritmetica(); 
        int rightExprEnd = current;

        // Validaciones semánticas
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

        // NUEVO: Recolectar la expresión de condición completa si sus partes son válidas.
        // La expresión completa es desde leftExprStart hasta rightExprEnd
        // Esto requeriría re-subList del inicio del primer operando al fin del segundo.
        // Por simplicidad, si las sub-expresiones son válidas, consideramos la condición completa procesada.
        // Un enfoque más robusto sería guardar la lista de tokens para la condición completa.
        // Por ahora, solo recolectamos las sub-expresiones que componen la condición.
        if (!leftOperandType.equals("Unknown") && !leftOperandType.equals("ErrorType")) {
            collectExpression(tokens.subList(leftExprStart, leftExprEnd));
        }
        if (!rightOperandType.equals("Unknown") && !rightOperandType.equals("ErrorType")) {
            // collectedExpressions.add(new ExpressionData(tokens.subList(rightExprStart, rightExprEnd)));
            // No recolectamos el mismo fragmento dos veces si rightExprStart == leftExprStart (ej. "a < b" vs "a < (a + 1)")
            // La recolección de la expresión completa de la condición es más compleja de hacer de forma ad-hoc.
            // Para el ejercicio, nos enfocaremos en las expresiones aritméticas y los rangos for.
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

    // MODIFICADO: addSemanticError solo agrega el error, no lanza excepción
    private void addSemanticError(Token token, String generalMessage, String specificMessageToUser) {
        SemanticError e = new SemanticError(token, generalMessage, specificMessageToUser);
        errors.add(e.getMessage());
    }

    // MODIFICADO: checkVariableInitialized ahora usa addSemanticError y no lanza excepción
    private void checkVariableInitialized(Token name) {
        if (!declaredVariables.contains(name.lexeme)) {
            addSemanticError(name,
                    "Variable no inicializada: " + name.lexeme,
                    "La variable '" + name.lexeme + "' se usa antes de declararla.");
            // No se detiene el parseo aquí.
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

    // --- NUEVOS MÉTODOS PARA CÓDIGO INTERMEDIO ---

    // Prioridades de los operadores para la conversión infija a prefija
    private int getOperatorPrecedence(Token.TokenType type) {
        switch (type) {
            case OP_SUMA:
            case OP_RESTA:
                return 1;
            case OP_MULT:
            case OP_DIV:
                return 2;
            default:
                return 0; // Para operandos o paréntesis
        }
    }

    // Método para convertir de infija a prefija (notación polaca)
    // Devuelve la expresión prefija como una lista de tokens, y simula la pila.
    private ExpressionConversionResult convertToPrefix(List<Token> infixTokens) {
        Stack<Token> operators = new Stack<>();
        List<Token> prefixTokens = new ArrayList<>();
        List<String> stackSimulationSteps = new ArrayList<>();
        
        // El algoritmo de infija a prefija estándar a menudo se hace invirtiendo la expresión,
        // convirtiendo a postfija, y luego invirtiendo de nuevo.
        // O adaptando el algoritmo de infija a postfija.
        // Para simplicidad y porque los cuádruplos son más fáciles desde postfija,
        // vamos a adaptar una versión que genere prefija directamente.
        // Una forma común es procesar de derecha a izquierda.
        
        // Invertimos la expresión infija para procesarla de derecha a izquierda
        List<Token> reversedInfix = new ArrayList<>(infixTokens);
        java.util.Collections.reverse(reversedInfix);

        stackSimulationSteps.add("Inicio de conversión a prefija (derecha a izquierda)");
        stackSimulationSteps.add("Entrada: " + tokensToString(reversedInfix));
        stackSimulationSteps.add("Pila Operadores | Salida Prefija");

        for (Token token : reversedInfix) {
            String currentStackState = operators.toString();
            String currentPrefixState = tokensToString(prefixTokens);
            stackSimulationSteps.add(String.format("%-15s | %s | Procesando: %s", 
                                                    currentStackState, currentPrefixState, token.lexeme));

            if (token.type == ID || token.type == NUMERO_ENTERO || token.type == CADENA_LITERAL) {
                prefixTokens.add(token);
            } else if (token.type == PAREN_DER) { // Paréntesis derecho en expresión original, ahora izquierdo
                operators.push(token);
            } else if (token.type == PAREN_IZQ) { // Paréntesis izquierdo en expresión original, ahora derecho
                while (!operators.isEmpty() && operators.peek().type != PAREN_DER) {
                    prefixTokens.add(operators.pop());
                }
                if (!operators.isEmpty() && operators.peek().type == PAREN_DER) {
                    operators.pop(); // Sacar el paréntesis derecho
                }
            } else if (isOperator(token.type)) {
                while (!operators.isEmpty() && isOperator(operators.peek().type) && 
                       getOperatorPrecedence(operators.peek().type) > getOperatorPrecedence(token.type)) {
                    prefixTokens.add(operators.pop());
                }
                operators.push(token);
            }
        }

        while (!operators.isEmpty()) {
            String currentStackState = operators.toString();
            String currentPrefixState = tokensToString(prefixTokens);
            stackSimulationSteps.add(String.format("%-15s | %s | Vaciar pila", 
                                                    currentStackState, currentPrefixState));
            prefixTokens.add(operators.pop());
        }
        
        // Invertimos la lista de tokens prefija para obtener el orden correcto
        java.util.Collections.reverse(prefixTokens);
        
        stackSimulationSteps.add("Fin de conversión.");
        stackSimulationSteps.add("Resultado final (invertido): " + tokensToString(prefixTokens));

        return new ExpressionConversionResult(prefixTokens, stackSimulationSteps);
    }
    
    private boolean isOperator(Token.TokenType type) {
        return type == OP_SUMA || type == OP_RESTA || type == OP_MULT || type == OP_DIV;
    }
    
    private String tokensToString(List<Token> tokens) {
        StringBuilder sb = new StringBuilder();
        for (Token t : tokens) {
            sb.append(t.lexeme).append(" ");
        }
        return sb.toString().trim();
    }

    // Clase para el resultado de la conversión
    private static class ExpressionConversionResult {
        List<Token> prefixTokens;
        List<String> stackSimulation;

        public ExpressionConversionResult(List<Token> prefixTokens, List<String> stackSimulation) {
            this.prefixTokens = prefixTokens;
            this.stackSimulation = stackSimulation;
        }
    }

    // NUEVO: Método para generar cuádruplos
    private List<String> generateQuadruples(List<Token> infixTokens, List<String> stackSimulation) {
        List<String> quadruples = new ArrayList<>();
        Stack<Token> operandStack = new Stack<>();
        Stack<Token> operatorStack = new Stack<>();
        int tempVarCounter = 0;

        // Para generar cuádruplos, es más natural trabajar con postfija.
        // Podríamos convertir a postfija primero, o adaptar directamente desde infija.
        // Adaptaremos el algoritmo Shunting-Yard para generar cuádruplos.
        // Cada vez que un operador es sacado de la pila, se genera un cuádruplo.

        quadruples.add("Generación de Cuádruplos:");

        List<String> currentStackStates = new ArrayList<>();
        currentStackStates.add("Pila Operandos | Pila Operadores | Cuádruplo Generado | Siguiente Token");

        for (Token token : infixTokens) {
            String opStackState = operatorStack.isEmpty() ? "[]" : operatorStack.toString();
            String valStackState = operandStack.isEmpty() ? "[]" : operandStack.toString();
            currentStackStates.add(String.format("%-15s | %-15s | %-20s | %s", 
                                                    valStackState, opStackState, "---", token.lexeme));


            if (token.type == ID || token.type == NUMERO_ENTERO || token.type == CADENA_LITERAL) {
                operandStack.push(token);
            } else if (token.type == PAREN_IZQ) {
                operatorStack.push(token);
            } else if (token.type == PAREN_DER) {
                while (!operatorStack.isEmpty() && operatorStack.peek().type != PAREN_IZQ) {
                    quadruples.add(popAndGenerateQuadruple(operandStack, operatorStack, ++tempVarCounter));
                }
                if (!operatorStack.isEmpty() && operatorStack.peek().type == PAREN_IZQ) {
                    operatorStack.pop(); // Sacar el paréntesis izquierdo
                }
            } else if (isOperator(token.type)) {
                while (!operatorStack.isEmpty() && isOperator(operatorStack.peek().type) && 
                       getOperatorPrecedence(operatorStack.peek().type) >= getOperatorPrecedence(token.type)) { // >= para asociatividad izquierda
                    quadruples.add(popAndGenerateQuadruple(operandStack, operatorStack, ++tempVarCounter));
                }
                operatorStack.push(token);
            }
        }

        while (!operatorStack.isEmpty()) {
            quadruples.add(popAndGenerateQuadruple(operandStack, operatorStack, ++tempVarCounter));
        }

        // Simulación de la pila para cuádruplos también
        stackSimulation.addAll(currentStackStates); // Añadir los estados de la pila para cuádruplos también.
        
        return quadruples;
    }

    private String popAndGenerateQuadruple(Stack<Token> operandStack, Stack<Token> operatorStack, int tempVarNum) {
        Token op = operatorStack.pop();
        Token right = operandStack.pop();
        Token left = operandStack.pop();
        
        String tempVar = "t" + tempVarNum;
        String quad = String.format("op: %s, arg1: %s, arg2: %s, result: %s",
                                    op.lexeme, left.lexeme, right.lexeme, tempVar);
        
        operandStack.push(new Token(ID, tempVar, null, 0, 0)); // Empujar la variable temporal como operando
        return quad;
    }


    // NUEVO: Método para recolectar y procesar una expresión válida
    private void collectExpression(List<Token> exprTokens) {
        if (exprTokens.isEmpty()) return;

        ExpressionData data = new ExpressionData(exprTokens);
        
        // Conversión a prefija
        ExpressionConversionResult prefixResult = convertToPrefix(exprTokens);
        data.prefixExpression = tokensToString(prefixResult.prefixTokens);
        data.stackSimulation = prefixResult.stackSimulation; // Simulación de pila para prefija

        // Generación de cuádruplos (requiere una pila limpia para operandos/operadores)
        // La simulación de pila para cuádruplos se puede fusionar con la anterior o ser independiente.
        // Aquí pasamos la lista de pasos para que los cuádruplos puedan añadir sus propios pasos.
        data.quadruples = generateQuadruples(exprTokens, data.stackSimulation);
        
        collectedExpressions.add(data);
    }
}