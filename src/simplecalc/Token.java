package simplecalc;

public class Token {
    public enum TokenType {
        // Palabras Reservadas de Kotlin
        FUN_KEYWORD, VAL_KEYWORD, VAR_KEYWORD, IF_KEYWORD, PRINT_KEYWORD, READLINE_KEYWORD,
        WHILE_KEYWORD, FOR_KEYWORD, IN_KEYWORD, // Nuevas palabras clave para ciclos
        
        // Identificadores y Literales
        ID, NUMERO_ENTERO, CADENA_LITERAL,
        
        // Operadores Aritméticos
        OP_SUMA, OP_RESTA, OP_MULT, OP_DIV,
        
        // Operadores Relacionales
        OP_MENOR, OP_MAYOR, OP_IGUAL_IGUAL,
        
        // Operador de Asignación
        ASIGNACION, // '='
        
        // Delimitadores
        PAREN_IZQ, PAREN_DER,
        LLAVE_IZQ, LLAVE_DER,
        DOS_PUNTOS, // ':' para tipos en Kotlin
        DOT_DOT,    // ".." para rangos en ciclos for
        
        EOL,        // End Of Line
        
        // Especiales
        WHITESPACE, // Para ser ignorado por el parser
        ERROR,      // Para errores léxicos
        EOF         // End Of File/Input
    }
    
    public final TokenType type;
    public final String lexeme;
    public final Object literal;
    public final int line;
    public final int column;
    
    public Token(TokenType type, String lexeme, Object literal, int line, int column) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
        this.column = column;
    }
    
    @Override
    public String toString() {
        String literalStr = (literal != null) ? literal.toString() : "";
        return String.format("| %-25s | %-20s | %-15s | %4d | %4d |",
                type, lexeme, literalStr, line, column);
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