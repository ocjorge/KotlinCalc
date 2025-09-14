// simplecalc/Parser.java

package simplecalc;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import static simplecalc.Token.TokenType.*;

public class Parser {

    private final List<Token> tokens;
    private int current = 0;
    private List<String> errors = new ArrayList<>();
    private Set<String> declaredVariables = new HashSet<>();

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
                errors.add(String.format("[Línea %d, Col %d] Error léxico: %s (token ignorado)", // Añadido (token ignorado)
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
        Token declarationType = advance();
        Token varNameToken = consume(ID, "Se esperaba un nombre de variable después de '" + declarationType.lexeme + "'.");
        consume(DOS_PUNTOS, "Se esperaba ':' después del nombre de variable '" + varNameToken.lexeme + "'.");
        consume(ID, "Se esperaba un tipo después de ':'.");
        consume(ASIGNACION, "Se esperaba '=' después del tipo.");
        expresion_aritmetica();

        System.out.println("DEBUG declaracion_stmt: Declarando variable: " + varNameToken.lexeme);
        declaredVariables.add(varNameToken.lexeme);
        System.out.println("DEBUG declaracion_stmt: declaredVariables: " + declaredVariables);

        consumeOptionalEOLs();
    }

    private void asignacion_stmt() {
        Token varNameToken = consume(ID, "Se esperaba un nombre de variable para la asignación.");
        checkVariableInitialized(varNameToken);
        consume(ASIGNACION, "Se esperaba '=' después del nombre de variable '" + varNameToken.lexeme + "'.");
        expresion_aritmetica();

        System.out.println("DEBUG asignacion_stmt: Asignando a variable: " + varNameToken.lexeme);
        System.out.println("DEBUG asignacion_stmt: declaredVariables: " + declaredVariables);

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
        valor_salida();
        // Aquí es donde ajustamos el mensaje si el PAREN_DER falta y hay un EOL.
        consume(PAREN_DER, "Se esperaba ')' para cerrar 'print'."); 
        consumeOptionalEOLs();
    }

    private void valor_salida() {
        if (check(ERROR)) {
            String lexerErrorMessage = peek().errorMessage != null ? peek().errorMessage : peek().lexeme + " (Caracter inesperado)";
            error(peek(), "Valor inválido para 'print'.",
                    "Cadena literal mal formada o incompleta: " + lexerErrorMessage);
            advance(); // Consumir el token de ERROR para seguir el parseo
            return; 
        }
        
        if (check(ID)) {
            Token idToken = peek();
            checkVariableInitialized(idToken);
            consume(ID, "");
        } else if (check(NUMERO_ENTERO)) {
            consume(NUMERO_ENTERO, "");
        } else if (check(CADENA_LITERAL)) {
            consume(CADENA_LITERAL, "");
        } else {
            error(peek(), "Valor inválido para 'print'.",
                    "Se esperaba un ID, un número entero o una cadena literal dentro de 'print()'.");
        }
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
        declaredVariables.add(loopVarName.lexeme); 

        consume(IN_KEYWORD, "Se esperaba 'in' después del nombre de la variable en 'for'.");
        expresion_aritmetica();
        consume(DOT_DOT, "Se esperaba '..' para definir el rango en el bucle 'for'.");
        expresion_aritmetica();

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
        operando_condicion();
        operador_relacional();
        operando_condicion();
    }

    private void operando_condicion() {
        // Manejo de token ERROR léxico dentro de una condición
        if (check(ERROR)) {
            String lexerErrorMessage = peek().errorMessage != null ? peek().errorMessage : peek().lexeme + " (Caracter inesperado)";
            error(peek(), "Operando inválido en condición.",
                    "Secuencia inválida encontrada: " + lexerErrorMessage);
            advance(); // Consumir el token de ERROR
            return;
        }

        if (check(ID)) {
            Token idToken = peek();
            checkVariableInitialized(idToken);
            consume(ID, "");
        } else if (check(NUMERO_ENTERO)) {
            consume(NUMERO_ENTERO, "");
        } else {
            error(peek(), "Operando inválido en condición.",
                    "Se esperaba un ID o un número entero en la condición.");
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

    private void expresion_aritmetica() {
        termino();
        while (match(OP_SUMA, OP_RESTA)) {
            if (peek().type == EOL) {
                throw error(peek(), "Expresión incompleta antes de salto de línea.",
                        "Se esperaba un operando después de '" + previous().lexeme + "' pero se encontró un salto de línea.");
            }
            termino();
        }
    }

    private void termino() {
        factor();
        while (match(OP_MULT, OP_DIV)) {
            if (peek().type == EOL) {
                throw error(peek(), "Expresión incompleta antes de salto de línea.",
                        "Se esperaba un operando después de '" + previous().lexeme + "' pero se encontró un salto de línea.");
            }
            factor();
        }
    }

    private void factor() {
        // Manejo de token ERROR léxico dentro de una expresión aritmética
        if (check(ERROR)) {
            String lexerErrorMessage = peek().errorMessage != null ? peek().errorMessage : peek().lexeme + " (Caracter inesperado)";
            error(peek(), "Expresión aritmética malformada.",
                    "Cadena literal o secuencia inválida: " + lexerErrorMessage);
            advance(); // Consumir el token de ERROR
            return;
        }

        if (check(ID)) {
            Token idToken = peek();
            checkVariableInitialized(idToken);
            consume(ID, "");
        } else if (check(NUMERO_ENTERO)) {
            consume(NUMERO_ENTERO, "");
        } else if (check(CADENA_LITERAL)) {
            consume(CADENA_LITERAL, "");
        } else if (check(READLINE_KEYWORD)) {
            consume(READLINE_KEYWORD, "");
            consume(PAREN_IZQ, "Se esperaba '(' después de 'readLine'.");
            consume(PAREN_DER, "Se esperaba ')' después de '('.");
        } else if (check(PAREN_IZQ)) {
            consume(PAREN_IZQ, "");
            expresion_aritmetica();
            consume(PAREN_DER, "Se esperaba ')' para cerrar la expresión entre paréntesis.");
        } else {
            error(peek(), "Expresión aritmética malformada.",
                    "Se esperaba un ID, un número, una cadena literal, readLine(), o una expresión entre paréntesis '(...)'.");
        }
    }

    private Token consume(Token.TokenType type, String message) {
        // Lógica de recuperación de errores: Si el token actual es un ERROR léxico
        // y NO es el tipo de token que esperamos, lo reportamos y avanzamos.
        // Esto evita que un error léxico cause múltiples errores sintácticos en cascada.
        if (peek().type == ERROR && type != ERROR) {
            String lexerErrorMessage = peek().errorMessage != null ? peek().errorMessage : peek().lexeme + " (Caracter inesperado)";
            errors.add(String.format("[Línea %d, Col %d] Error léxico: %s (token ignorado para análisis sintáctico)",
                    peek().line, peek().column, lexerErrorMessage));
            advance(); // Consumir el token de error léxico
            if (check(type)) { // Intentar ver si el siguiente token es el esperado
                return advance();
            }
            // Si incluso después de recuperar, no encontramos el token esperado, lanzamos un error sintáctico.
            // Usamos peek() porque advance() ya se ejecutó si el ERROR fue consumido.
            // Este error sintáctico será un error secundario debido al error léxico.
            throw error(peek(), message, message); 
        }
        
        // Manejo específico para el EOL inesperado antes del token esperado
        // Si el EOL es el problema, pero esperamos algo más crucial (como PAREN_DER),
        // priorizamos el mensaje de lo esperado.
        if (peek().type == EOL && type != EOL && type != EOF) {
            // No lanzamos un error sobre el EOL directamente aquí, sino que permitimos
            // que la siguiente comprobación `check(type)` falle y use el `message` proporcionado.
            // Esto asegura que el mensaje "Se esperaba ')' para cerrar 'print'." tenga prioridad.
        }
        
        if (check(type)) {
            return advance();
        }
        
        // Si no se encontró el tipo esperado (y no fue un ERROR léxico ni un EOL prioritario),
        // generamos el error con el mensaje proporcionado.
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