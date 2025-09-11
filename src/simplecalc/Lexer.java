// simplecalc/Lexer.java

package simplecalc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Lexer {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int start = 0;
    private int current = 0;
    private int line = 1;

    private static final Map<String, Token.TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("fun", Token.TokenType.FUN_KEYWORD);
        keywords.put("val", Token.TokenType.VAL_KEYWORD);
        keywords.put("var", Token.TokenType.VAR_KEYWORD);
        keywords.put("if", Token.TokenType.IF_KEYWORD);
        keywords.put("print", Token.TokenType.PRINT_KEYWORD);
        keywords.put("readLine", Token.TokenType.READLINE_KEYWORD);
        keywords.put("while", Token.TokenType.WHILE_KEYWORD);
        keywords.put("for", Token.TokenType.FOR_KEYWORD);
        keywords.put("in", Token.TokenType.IN_KEYWORD);
    }

    public Lexer(String source) {
        this.source = source;
    }

    public List<Token> scanTokens() {
        while (!isAtLexerEnd()) {
            start = current;
            scanToken();
        }
        tokens.add(new Token(Token.TokenType.EOF, "", null, line, calculateColumnForCurrentPos(current)));
        return tokens;
    }

    private void scanToken() {
        char c = advanceLexerChar();

        switch (c) {
            case '(': addToken(Token.TokenType.PAREN_IZQ); break;
            case ')': addToken(Token.TokenType.PAREN_DER); break;
            case '{': addToken(Token.TokenType.LLAVE_IZQ); break;
            case '}': addToken(Token.TokenType.LLAVE_DER); break;
            case '+': addToken(Token.TokenType.OP_SUMA); break;
            case '-': addToken(Token.TokenType.OP_RESTA); break;
            case '*': addToken(Token.TokenType.OP_MULT); break;
            case ':': addToken(Token.TokenType.DOS_PUNTOS); break;

            case '=':
                addToken(matchLexerChar('=') ? Token.TokenType.OP_IGUAL_IGUAL : Token.TokenType.ASIGNACION);
                break;
            case '<':
                if (matchLexerChar('=')) {
                    addErrorTokenSimple("Operador relacional '<=' no está permitido. Use '<' o '=='."); // Usar simple
                } else {
                    addToken(Token.TokenType.OP_MENOR);
                }
                break;
            case '>':
                if (matchLexerChar('=')) {
                    addErrorTokenSimple("Operador relacional '>=' no está permitido. Use '>' o '=='."); // Usar simple
                } else {
                    addToken(Token.TokenType.OP_MAYOR);
                }
                break;
            case '/':
                addToken(Token.TokenType.OP_DIV);
                break;
            case '.':
                if (matchLexerChar('.')) {
                    addToken(Token.TokenType.DOT_DOT);
                } else {
                    addErrorTokenSimple("Caracter inesperado: '.' (se esperaba '..' para rango)"); // Usar simple
                }
                break;

            case ' ':
            case '\r':
            case '\t':
                break;
            case '\n':
                addToken(Token.TokenType.EOL, "\\n");
                line++;
                break;

            case '"':
                string();
                break;

            default:
                if (isDigit(c)) {
                    number();
                } else if (isAlphaStart(c)) {
                    identifier();
                } else {
                    StringBuilder invalidSequenceBuilder = new StringBuilder();
                    invalidSequenceBuilder.append(c);

                    while (!isAtLexerEnd() &&
                           !Character.isWhitespace(peekLexerChar()) &&
                           peekLexerChar() != '\n' &&
                           !isClearDelimiter(peekLexerChar())
                           ) {
                        invalidSequenceBuilder.append(advanceLexerChar());
                    }
                    // NUEVA LÓGICA: Llamar a addErrorToken con contexto
                    addErrorToken("Secuencia de caracteres inesperada: '%s'", invalidSequenceBuilder.toString());
                }
                break;
        }
    }

    private boolean isClearDelimiter(char c) {
        return c == '(' || c == ')' || c == '{' || c == '}' ||
               c == '+' || c == '-' || c == '*' || c == '/' ||
               c == '=' || c == '<' || c == '>' || c == ':' ||
               c == '"' || (c == '.' && current + 1 < source.length() && source.charAt(current + 1) == '.');
    }

    private void identifier() {
        while (isAlphaNumeric(peekLexerChar())) advanceLexerChar();

        String text = source.substring(start, current);
        Token.TokenType type = keywords.get(text);

        if (type == null) {
            addToken(Token.TokenType.ID);
        } else {
            addToken(type);
        }
    }

    private void number() {
        while (isDigit(peekLexerChar())) advanceLexerChar();

        String numStr = source.substring(start, current);
        try {
            addToken(Token.TokenType.NUMERO_ENTERO, Integer.parseInt(numStr));
        } catch (NumberFormatException e) {
             addErrorTokenSimple("Número entero inválido o muy grande: '" + numStr + "'"); // Usar simple
        }
    }
    
    private void string() {
        while (peekLexerChar() != '"' && !isAtLexerEnd()) {
            char peeked = peekLexerChar();
            if (peeked == '\n' || peeked == '\r') {
                 addErrorTokenSimple("Salto de línea o retorno de carro no permitido en cadena literal."); // Usar simple
                 while(peekLexerChar() != '"' && !isAtLexerEnd() && peekLexerChar() != '\n' && peekLexerChar() != '\r') {
                     advanceLexerChar();
                 }
                 return;
            }
            advanceLexerChar();
        }

        if (isAtLexerEnd()) {
            addErrorTokenSimple("Cadena literal no terminada."); // Usar simple
            return;
        }
        advanceLexerChar();
        String value = source.substring(start + 1, current - 1);
        addToken(Token.TokenType.CADENA_LITERAL, value);
    }

    // --- Métodos de ayuda del Lexer ---

    private boolean isAtLexerEnd() {
        return current >= source.length();
    }

    private char advanceLexerChar() {
        return source.charAt(current++);
    }

    private boolean matchLexerChar(char expected) {
        if (isAtLexerEnd()) return false;
        if (source.charAt(current) != expected) return false;
        current++;
        return true;
    }

    private char peekLexerChar() {
        if (isAtLexerEnd()) return '\0';
        return source.charAt(current);
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private boolean isAlphaStart(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    private boolean isAlphaNumeric(char c) {
        return isAlphaStart(c) || isDigit(c);
    }

    private void addToken(Token.TokenType type) {
        addToken(type, null);
    }

    private void addToken(Token.TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line, calculateColumnForCurrentPos(start)));
    }
    
    // Nuevo método para agregar errores léxicos simples sin contexto especial
    private void addErrorTokenSimple(String message) {
        String problematicLexeme = source.substring(start, current);
        tokens.add(new Token(Token.TokenType.ERROR, problematicLexeme + " (" + message + ")",
                             null, line, calculateColumnForCurrentPos(start)));
    }

    // Método principal para agregar errores léxicos, intentando incluir el contexto
    private void addErrorToken(String format, Object... args) {
        String problemLexeme = String.format(format, args);
        String fullProblematicLexeme = problemLexeme;
        int errorColumn = calculateColumnForCurrentPos(start);
        int errorStart = start;

        // Lógica para intentar incluir el lexema anterior si es un número o ID
        if (!tokens.isEmpty()) {
            Token lastToken = tokens.get(tokens.size() - 1);
            // Si el error actual está inmediatamente después del último token
            // y el último token es un ID o NUMERO_ENTERO, lo consideramos parte del mismo lexema fallido
            // Asumimos que no hay espacios en blanco entre el token válido y el inválido.
            if (lastToken.line == line && 
                (lastToken.type == Token.TokenType.ID || lastToken.type == Token.TokenType.NUMERO_ENTERO) &&
                lastToken.column + lastToken.lexeme.length() == calculateColumnForCurrentPos(start)) {
                
                fullProblematicLexeme = lastToken.lexeme + problemLexeme;
                errorColumn = lastToken.column; // El error empieza donde empezó el token anterior
                errorStart = start - lastToken.lexeme.length(); // El inicio real en la fuente
                
                // NOTA: Para que esto funcione perfectamente, deberíamos *reemplazar* el último token
                // con el token de error extendido, o al menos no añadir el token válido antes.
                // Sin embargo, modificar la lista de tokens ya generados es complicado.
                // Por ahora, solo se modifica el MENSAJE del error, y el resaltado se hará desde
                // la columna del token anterior.
            }
        }
        
        // El lexema del token en sí seguirá siendo solo la parte inválida (ej. "%2") para el resaltado directo.
        // Pero el mensaje incluirá el contexto.
        tokens.add(new Token(Token.TokenType.ERROR, source.substring(start, current), // Lexema real del token de error
                             null, line, calculateColumnForCurrentPos(start), 
                             String.format("Secuencia de caracteres inesperada: '%s'", fullProblematicLexeme)));
    }

    private int calculateColumnForCurrentPos(int tokenStartIndex) {
        int col = 1;
        int currentLineActualStart = 0;
        for (int i = tokenStartIndex - 1; i >= 0; i--) {
            if (source.charAt(i) == '\n') {
                currentLineActualStart = i + 1;
                break;
            }
        }
        col = tokenStartIndex - currentLineActualStart + 1;
        return col;
    }
}