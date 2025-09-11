// simplecalc/Token.java

package simplecalc;

public class Token {
    public enum TokenType {
        // ... (Todos tus tipos de token existentes)
        FUN_KEYWORD, VAL_KEYWORD, VAR_KEYWORD, IF_KEYWORD, PRINT_KEYWORD, READLINE_KEYWORD,
        WHILE_KEYWORD, FOR_KEYWORD, IN_KEYWORD,
        
        ID, NUMERO_ENTERO, CADENA_LITERAL,
        
        OP_SUMA, OP_RESTA, OP_MULT, OP_DIV,
        
        OP_MENOR, OP_MAYOR, OP_IGUAL_IGUAL,
        
        ASIGNACION,
        
        PAREN_IZQ, PAREN_DER,
        LLAVE_IZQ, LLAVE_DER,
        DOS_PUNTOS,
        DOT_DOT,
        
        EOL,        
        WHITESPACE,
        ERROR,      
        EOF         
    }
    
    public final TokenType type;
    public final String lexeme;
    public final Object literal;
    public final int line;
    public final int column;
    // NUEVO: Campo para el mensaje de error cuando el tipo es ERROR
    public final String errorMessage; 
    
    // Constructor para tokens normales
    public Token(TokenType type, String lexeme, Object literal, int line, int column) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
        this.column = column;
        this.errorMessage = null; // No hay mensaje de error para tokens normales
    }

    // NUEVO: Constructor para tokens de ERROR con mensaje personalizado
    public Token(TokenType type, String lexeme, Object literal, int line, int column, String errorMessage) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
        this.column = column;
        this.errorMessage = errorMessage; // Almacena el mensaje extendido
    }
    
    @Override
    public String toString() {
        String literalStr = (literal != null) ? literal.toString() : "";
        // Si es un token de ERROR, muestra el mensaje extendido en lugar del lexema directo
        String displayLexeme = (type == TokenType.ERROR && errorMessage != null) ? errorMessage : lexeme;
        
        return String.format("| %-25s | %-20s | %-15s | %4d | %4d |",
                type, displayLexeme, literalStr, line, column);
    }
    
    public static String getTableHeader() {
        return "+---------------------------+----------------------+-----------------+------+------+\n" +
               "| Tipo de Token             | Lexema               | Literal         | Line | Col  |\n" +
               "+---------------------------+----------------------+-----------------+------+------+";
    }
    
    public static String getTableFooter() {
        return "+---------------------------+----------------------+-----------------+------+------+";
    }
}