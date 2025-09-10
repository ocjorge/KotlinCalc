// Parser.java - Convertido a sintaxis de Kotlin
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

    // Programa Kotlin: fun main() { ... }
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
                errors.add(String.format("[Línea %d, Col %d] Error léxico: %s. Se ignora.",
                        peek().line, peek().column, peek().lexeme));
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
        } else if (check(WHILE_KEYWORD)) { // Nuevo: Sentencia while
            System.out.println("DEBUG: sentencia: WHILE_KEYWORD -> while_stmt()");
            while_stmt();
        } else if (check(FOR_KEYWORD)) { // Nuevo: Sentencia for
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

    // Declaración en Kotlin: val/var nombre: Int = valor
    private void declaracion_stmt() {
        Token declarationType = advance(); // val o var
        Token varNameToken = consume(ID, "Se esperaba un nombre de variable después de '" + declarationType.lexeme + "'.");
        consume(DOS_PUNTOS, "Se esperaba ':' después del nombre de variable '" + varNameToken.lexeme + "'.");
        consume(ID, "Se esperaba un tipo después de ':'."); // Simplificación: asume que el tipo es un ID
        consume(ASIGNACION, "Se esperaba '=' después del tipo.");
        expresion_aritmetica();

        System.out.println("DEBUG declaracion_stmt: Declarando variable: " + varNameToken.lexeme);
        declaredVariables.add(varNameToken.lexeme);
        System.out.println("DEBUG declaracion_stmt: declaredVariables: " + declaredVariables);

        consumeOptionalEOLs();
    }

    private void asignacion_stmt() {
        Token varNameToken = consume(ID, "Se esperaba un nombre de variable para la asignación.");
        checkVariableInitialized(varNameToken); // Verificar si la variable ya fue declarada
        consume(ASIGNACION, "Se esperaba '=' después del nombre de variable '" + varNameToken.lexeme + "'.");
        expresion_aritmetica();

        System.out.println("DEBUG asignacion_stmt: Asignando a variable: " + varNameToken.lexeme);
        System.out.println("DEBUG asignacion_stmt: declaredVariables: " + declaredVariables);

        consumeOptionalEOLs();
    }

    // readLine() en Kotlin
    private void entrada_stmt() {
        consume(READLINE_KEYWORD, "Error interno: Se esperaba 'readLine' para entrada_stmt.");
        consume(PAREN_IZQ, "Se esperaba '(' después de 'readLine'.");
        consume(PAREN_DER, "Se esperaba ')' después de '('.");
        consumeOptionalEOLs();
    }

    // print() en Kotlin
    private void salida_stmt() {
        consume(PRINT_KEYWORD, "Error interno: Se esperaba 'print' para salida_stmt.");
        consume(PAREN_IZQ, "Se esperaba '(' después de 'print'.");
        valor_salida();
        consume(PAREN_DER, "Se esperaba ')' para cerrar 'print'.");
        consumeOptionalEOLs();
    }

    private void valor_salida() {
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

    // if en Kotlin
    private void if_stmt() {
        consume(IF_KEYWORD, "Error interno: Se esperaba 'if' para if_stmt.");
        consume(PAREN_IZQ, "Se esperaba '(' después de 'if'.");
        condicion_simple();
        consume(PAREN_DER, "Se esperaba ')' después de la condición en 'if'.");
        bloque_if();
    }

    // Nuevo método para manejar el bloque del if
    private void bloque_if() {
        consume(LLAVE_IZQ, "Se esperaba '{' después de 'if (...)'.");
        consumeOptionalEOLs();

        while (!check(LLAVE_DER) && !isAtEnd()) {
            if (peek().type == ERROR) {
                errors.add(String.format("[Línea %d, Col %d] Error léxico: %s. Se ignora.",
                        peek().line, peek().column, peek().lexeme));
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

    // Nuevo: while en Kotlin
    private void while_stmt() {
        consume(WHILE_KEYWORD, "Se esperaba 'while'.");
        consume(PAREN_IZQ, "Se esperaba '(' después de 'while'.");
        condicion_simple();
        consume(PAREN_DER, "Se esperaba ')' después de la condición en 'while'.");
        bloque_loop(); // Reusa el manejo de bloque para loops
    }

    // Nuevo: for en Kotlin (simplificado para 'for varName in rangeStart..rangeEnd')
    private void for_stmt() {
        consume(FOR_KEYWORD, "Se esperaba 'for'.");
        consume(PAREN_IZQ, "Se esperaba '(' después de 'for'.");
        
        Token loopVarName = consume(ID, "Se esperaba un nombre de variable para el bucle 'for'.");
        declaredVariables.add(loopVarName.lexeme); // Declarar la variable del bucle

        consume(IN_KEYWORD, "Se esperaba 'in' después del nombre de la variable en 'for'.");
        expresion_aritmetica(); // Expresión de inicio del rango
        consume(DOT_DOT, "Se esperaba '..' para definir el rango en el bucle 'for'.");
        expresion_aritmetica(); // Expresión de fin del rango

        consume(PAREN_DER, "Se esperaba ')' después del rango en 'for'.");
        bloque_loop(); // Reusa el manejo de bloque para loops
    }

    // Método para manejar los bloques de los ciclos (similar a bloque_if)
    private void bloque_loop() {
        consume(LLAVE_IZQ, "Se esperaba '{' después de la cabecera del ciclo.");
        consumeOptionalEOLs();

        while (!check(LLAVE_DER) && !isAtEnd()) {
            if (peek().type == ERROR) {
                errors.add(String.format("[Línea %d, Col %d] Error léxico: %s. Se ignora.",
                        peek().line, peek().column, peek().lexeme));
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

    //ESTE METODO NO ESTOY SEGURO SI FUNCIONA O NO (anteriormente para accion_unica_if)
    private void accion_unica_if() {
        if (check(PRINT_KEYWORD)) {
            consume(PRINT_KEYWORD, "");
            consume(PAREN_IZQ, "Se esperaba '(' después de 'print'.");
            valor_salida();
            consume(PAREN_DER, "Se esperaba ')' para cerrar 'print'.");
        } else if (check(ID) && (current + 1 < tokens.size() && tokens.get(current + 1).type == ASIGNACION)) {
            asignacion_stmt();
        } else {
            error(peek(), "Acción inválida después de 'if (...)'.",
                    "Se esperaba una sentencia 'print(...)' o una asignación 'variable = ...'.");
        }
    }

    private void consumeOptionalEOLs() {
        while (match(EOL)) {
            // Seguir consumiendo EOLs
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
        if (check(ID)) {
            Token idToken = peek();
            checkVariableInitialized(idToken);
            consume(ID, "");
        } else if (check(NUMERO_ENTERO)) {
            consume(NUMERO_ENTERO, "");
        } else if (check(CADENA_LITERAL)) {
            // Soporte para cadenas literales en expresiones
            consume(CADENA_LITERAL, "");
        } else if (check(READLINE_KEYWORD)) {
            // Manejo de readLine() como parte de una expresión
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

    // ---- Métodos de ayuda del Parser ----
    private Token consume(Token.TokenType type, String message) {
        if (peek().type == EOL && type != EOL && type != EOF) {
            throw error(peek(), "Salto de línea inesperado.",
                    "Se esperaba '" + type + "' para continuar/terminar la sentencia, pero se encontró un salto de línea. " + message);
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
                case WHILE_KEYWORD: // Añadido
                case FOR_KEYWORD:   // Añadido
                case LLAVE_DER:
                case EOF:
                    System.out.println("DEBUG: synchronize: encontrado " + peek().type + ". Retornando.");
                    return;
                default:
                // Sigue avanzando
            }
            System.out.println("DEBUG: synchronize: avanzando desde " + peek().type);
            advance();
        }
        System.out.println("DEBUG: Salida de synchronizeToStatementBoundary() porque isAtEnd() es true.");
    }
}