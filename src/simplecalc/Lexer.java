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
                    addErrorTokenSimple("Operador relacional '<=' no está permitido. Use '<' o '=='.");
                } else {
                    addToken(Token.TokenType.OP_MENOR);
                }
                break;
            case '>':
                if (matchLexerChar('=')) {
                    addErrorTokenSimple("Operador relacional '>=' no está permitido. Use '>' o '=='.");
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
                    addErrorTokenSimple("Caracter inesperado: '.' (se esperaba '..' para rango)");
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
                    addErrorToken(invalidSequenceBuilder.toString(), "Secuencia de caracteres inesperada: '%s'");
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
             addErrorTokenSimple("Número entero inválido o muy grande: '" + numStr + "'");
        }
    }
    
    private void string() {
        int currentTokenLine = line;
        int currentTokenCol = calculateColumnForCurrentPos(start);

        while (peekLexerChar() != '"' && !isAtLexerEnd()) {
            char peeked = peekLexerChar();
            if (peeked == '\n' || peeked == '\r') {
                String partialStringLexeme = source.substring(start, current);
                tokens.add(new Token(Token.TokenType.ERROR, partialStringLexeme, null, 
                                     currentTokenLine, currentTokenCol, 
                                     "Salto de línea o retorno de carro no permitido en cadena literal."));
                
                while (!isAtLexerEnd() && peekLexerChar() != '\n' && peekLexerChar() != '\r') {
                    advanceLexerChar();
                }
                return;
            }
            advanceLexerChar();
        }

        if (isAtLexerEnd()) {
            String partialStringLexeme = source.substring(start, current);
            tokens.add(new Token(Token.TokenType.ERROR, partialStringLexeme, null, 
                                 currentTokenLine, currentTokenCol, 
                                 "Cadena literal no terminada."));
            return;
        }
        
        advanceLexerChar();
        String value = source.substring(start + 1, current - 1);
        addToken(Token.TokenType.CADENA_LITERAL, value);
    }

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
    
    private void addErrorTokenSimple(String message) {
        String problematicLexeme = source.substring(start, current);
        tokens.add(new Token(Token.TokenType.ERROR, problematicLexeme, 
                             null, line, calculateColumnForCurrentPos(start),
                             message));
    }

    private void addErrorToken(String problematicSubstring, String formatMessage) {
        String contextualLexeme = problematicSubstring;
        int errorColumn = calculateColumnForCurrentPos(start);

        if (!tokens.isEmpty()) {
            Token lastToken = tokens.get(tokens.size() - 1);
            if (lastToken.line == line && 
                (lastToken.type == Token.TokenType.ID || lastToken.type == Token.TokenType.NUMERO_ENTERO) &&
                (lastToken.column + lastToken.lexeme.length() == calculateColumnForCurrentPos(start))) {
                
                contextualLexeme = lastToken.lexeme + problematicSubstring;
                errorColumn = lastToken.column; 
            }
        }
        
        tokens.add(new Token(Token.TokenType.ERROR, problematicSubstring,
                             null, line, errorColumn, 
                             String.format(formatMessage, contextualLexeme)));
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