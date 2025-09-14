package simplecalc;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import static simplecalc.Token.TokenType.*;

public class Parser {

    private final List<Token> tokens;
    private int current = 0;
    private List<String> errors = new ArrayList<>();
    private Set<String> declaredVariables = new HashSet<>();
    private Map<String, String> variableTypes = new HashMap<>(); // Variable name -> Type String ("Int", "String")

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public List<String> getErrors() {
        return errors;
    }

    public boolean parse() {
        current = 0;
        errors.clear();
        declaredVariables.clear();
        variableTypes.clear(); 
        try {
            programa();
        } catch (SyntaxError | SemanticError e) {
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
            System.out.println("DEBUG: sentencia: READLINE_KEYWORD -> entrada_stmt()");
            entrada_stmt();
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
            System.out.println("DEBUG: sentencia: Token inesperado " + peek().type + " -> error");
            error(peek(), "Sentencia inválida o no reconocida.",
                    "Se esperaba 'val', 'var', 'readLine', 'print', 'if', 'while', 'for', una asignación, o fin de bloque '}'.");
            synchronizeToStatementBoundary();
        }
        System.out.println("DEBUG: Saliendo de sentencia(), current ahora apunta a: " + (isAtEnd() ? "EOF" : peek().type));
    }

    private void declaracion_stmt() {
        Token declarationType = advance(); // VAL_KEYWORD o VAR_KEYWORD
        Token varNameToken = consume(ID, "Se esperaba un nombre de variable después de '" + declarationType.lexeme + "'.");
        
        // Verificar si la variable ya fue declarada
        if (declaredVariables.contains(varNameToken.lexeme)) {
             semanticError(varNameToken, "Redeclaración de variable.",
                           "La variable '" + varNameToken.lexeme + "' ya ha sido declarada.");
        }

        consume(DOS_PUNTOS, "Se esperaba ':' después del nombre de variable '" + varNameToken.lexeme + "'.");
        
        Token typeToken = consume(ID, "Se esperaba un tipo (ej. 'Int', 'String') después de ':'.");
        if (!isValidType(typeToken.lexeme)) {
            semanticError(typeToken, "Tipo de dato no reconocido: '" + typeToken.lexeme + "'.",
                          "Se esperaba un tipo de dato válido como 'Int' o 'String'.");
        }

        consume(ASIGNACION, "Se esperaba '=' después del tipo '" + typeToken.lexeme + "'.");
        
        String declaredType = typeToken.lexeme;
        String assignedExpressionType = expresion_aritmetica(); // Parsear y obtener el tipo de la expresión

        checkTypeCompatibility(declaredType, assignedExpressionType, varNameToken);

        declaredVariables.add(varNameToken.lexeme);
        variableTypes.put(varNameToken.lexeme, declaredType);
        System.out.println("DEBUG declaracion_stmt: Declarando variable: " + varNameToken.lexeme + " de tipo: " + declaredType);

        consumeOptionalEOLs();
    }

    private void asignacion_stmt() {
        Token varNameToken = consume(ID, "Se esperaba un nombre de variable para la asignación.");
        checkVariableInitialized(varNameToken);

        String declaredType = variableTypes.get(varNameToken.lexeme);
        if (declaredType == null) { 
             semanticError(varNameToken, "Error interno: Tipo de variable no encontrado.",
                           "La variable '" + varNameToken.lexeme + "' no tiene un tipo asignado.");
             declaredType = "Unknown";
        }

        consume(ASIGNACION, "Se esperaba '=' después del nombre de variable '" + varNameToken.lexeme + "'.");
        
        String assignedExpressionType = expresion_aritmetica(); // Parsear y obtener el tipo de la expresión

        checkTypeCompatibility(declaredType, assignedExpressionType, varNameToken);

        System.out.println("DEBUG asignacion_stmt: Asignando a variable: " + varNameToken.lexeme + " con tipo: " + assignedExpressionType);

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
        expresion_aritmetica(); // print toma una expresión, no solo un valor_salida simple
        consume(PAREN_DER, "Se esperaba ')' para cerrar 'print'."); 
        consumeOptionalEOLs();
    }

    private void if_stmt() {
        consume(IF_KEYWORD, "Error interno: Se esperaba 'if' para if_stmt.");
        consume(PAREN_IZQ, "Se esperaba '(' después de 'if'.");
        condicion_simple();
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
        condicion_simple();
        consume(PAREN_DER, "Se esperaba ')' después de la condición en 'while'.");
        bloque_loop();
    }

    private void for_stmt() {
        consume(FOR_KEYWORD, "Se esperaba 'for'.");
        consume(PAREN_IZQ, "Se esperaba '(' después de 'for'.");
        
        Token loopVarName = consume(ID, "Se esperaba un nombre de variable para el bucle 'for'.");
        if (declaredVariables.contains(loopVarName.lexeme)) {
             semanticError(loopVarName, "Redeclaración de variable.",
                           "La variable '" + loopVarName.lexeme + "' ya ha sido declarada.");
        }
        declaredVariables.add(loopVarName.lexeme); 
        variableTypes.put(loopVarName.lexeme, "Int"); 
        System.out.println("DEBUG for_stmt: Declarando variable de bucle: " + loopVarName.lexeme + " de tipo: Int");


        consume(IN_KEYWORD, "Se esperaba 'in' después del nombre de la variable en 'for'.");
        
        String rangeStartType = expresion_aritmetica(); 
        checkTypeCompatibility("Int", rangeStartType, previous());
        
        consume(DOT_DOT, "Se esperaba '..' para definir el rango en el bucle 'for'.");
        
        String rangeEndType = expresion_aritmetica(); 
        checkTypeCompatibility("Int", rangeEndType, previous());

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
        // Parsear el primer operando
        String leftOperandType = expresion_aritmetica(); // Esto debería parsear "contador"

        // Consumir el operador relacional y guardarlo
        // operador_relacional() llamará a advance()
        Token operatorToken = peek(); // Obtener el token del operador antes de consumirlo
        operador_relacional(); // Esto consume '<'

        // Parsear el segundo operando
        String rightOperandType = expresion_aritmetica(); // Esto debería parsear "3"

        // Semántico: Verificar compatibilidad de tipos en la condición
        if (!leftOperandType.equals("Unknown") && !rightOperandType.equals("Unknown") &&
            !leftOperandType.equals("ErrorType") && !rightOperandType.equals("ErrorType")) {
            
            if (!leftOperandType.equals(rightOperandType)) {
                semanticError(operatorToken, "Incompatibilidad de tipos en la condición.", // Usar operatorToken
                              "No se puede comparar un tipo '" + leftOperandType + "' con un tipo '" + rightOperandType + "'.");
            }
            if (!leftOperandType.equals("Int") && !leftOperandType.equals("String")) { 
                 semanticError(operatorToken, "Tipo de dato no comparable.", // Usar operatorToken
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
    
    // MODIFICADO: expresion_aritmetica ahora devuelve el tipo de la expresión
    private String expresion_aritmetica() {
        String type = termino();
        while (match(OP_SUMA, OP_RESTA)) {
            Token operator = previous(); // Operador actual
            // Consumir el operador relacional si existe antes de evaluar el termino.
            if(operator.type == OP_MENOR || operator.type == OP_MAYOR || operator.type == OP_IGUAL_IGUAL) {
                // Este caso solo debería ocurrir en condicion_simple y ya lo maneja
                // Si llegamos aquí, es un error sintáctico porque un operador relacional no va en una expresión aritmética.
                 semanticError(operator, "Operador relacional inesperado en expresión aritmética.",
                               "Se esperaba un operador aritmético (+, -) pero se encontró un operador relacional.");
                 type = "ErrorType";
                 // No avanzamos, dejamos que el parseo continúe con el término
            }
            
            String rightType = termino();

            if (type.equals("Unknown") || rightType.equals("Unknown")) {
                type = "Unknown";
            } else if (type.equals("ErrorType") || rightType.equals("ErrorType")) {
                type = "ErrorType"; // Propagar el error de tipo
            } else if (type.equals("Int") && rightType.equals("Int")) {
                type = "Int";
            } else if (type.equals("String") && rightType.equals("String") && operator.type == OP_SUMA) {
                type = "String"; // Concatenación de cadenas
            } else {
                semanticError(operator, "Incompatibilidad de tipos en operación.",
                              "Operación '" + operator.lexeme + "' entre '" + type + "' y '" + rightType + "' no permitida.");
                type = "ErrorType";
            }
        }
        return type;
    }

    // MODIFICADO: termino ahora devuelve el tipo del término
    private String termino() {
        String type = factor();
        while (match(OP_MULT, OP_DIV)) {
            Token operator = previous(); // Operador actual
            String rightType = factor();

            if (type.equals("Unknown") || rightType.equals("Unknown")) {
                type = "Unknown";
            } else if (type.equals("ErrorType") || rightType.equals("ErrorType")) {
                type = "ErrorType";
            } else if (type.equals("Int") && rightType.equals("Int")) {
                type = "Int";
            } else {
                semanticError(operator, "Incompatibilidad de tipos en operación.",
                              "Operación '" + operator.lexeme + "' entre '" + type + "' y '" + rightType + "' no permitida.");
                type = "ErrorType";
            }
        }
        return type;
    }

    // MODIFICADO: factor ahora devuelve el tipo del factor
    private String factor() {
        if (check(ERROR)) {
            String lexerErrorMessage = peek().errorMessage != null ? peek().errorMessage : peek().lexeme + " (Caracter inesperado)";
            error(peek(), "Expresión aritmética malformada.",
                    "Cadena literal o secuencia inválida: " + lexerErrorMessage);
            advance();
            return "Unknown"; // Si hay error léxico, el tipo es desconocido
        }

        String type = "Unknown";
        if (check(ID)) {
            Token idToken = peek();
            checkVariableInitialized(idToken); // Semántico: Verificar que la variable está declarada
            type = variableTypes.get(idToken.lexeme); // Obtener el tipo de la variable
            if (type == null) type = "Unknown"; 
            consume(ID, "");
        } else if (check(NUMERO_ENTERO)) {
            type = "Int";
            consume(NUMERO_ENTERO, "");
        } else if (check(CADENA_LITERAL)) {
            type = "String";
            consume(CADENA_LITERAL, "");
        } else if (check(READLINE_KEYWORD)) {
            type = "String"; // readLine() siempre devuelve String
            consume(READLINE_KEYWORD, "");
            consume(PAREN_IZQ, "Se esperaba '(' después de 'readLine'.");
            consume(PAREN_DER, "Se esperaba ')' después de '('.");
        } else if (check(PAREN_IZQ)) {
            consume(PAREN_IZQ, "");
            type = expresion_aritmetica(); // Recursivamente obtener el tipo de la subexpresión
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

    private SemanticError semanticError(Token token, String generalMessage, String specificMessageToUser) {
        SemanticError e = new SemanticError(token, generalMessage, specificMessageToUser);
        errors.add(e.getMessage());
        return e;
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

    private void checkVariableInitialized(Token name) {
        if (!declaredVariables.contains(name.lexeme)) {
            throw semanticError(name,
                    "Variable no inicializada: " + name.lexeme,
                    "La variable '" + name.lexeme + "' se usa antes de declararla.");
        }
    }
    
    private boolean isValidType(String typeName) {
        return typeName.equals("Int") || typeName.equals("String");
    }

    private void checkTypeCompatibility(String expectedType, String actualType, Token problemToken) {
        if (expectedType.equals("Unknown") || actualType.equals("Unknown") || actualType.equals("ErrorType")) {
            return; 
        }
        
        if (!expectedType.equals(actualType)) {
            throw semanticError(problemToken, "Incompatibilidad de tipos en asignación.",
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
}