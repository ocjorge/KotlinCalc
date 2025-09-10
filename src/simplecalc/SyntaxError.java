package simplecalc;

public class SyntaxError extends RuntimeException {
    public final Token token;
    public final String specificMessage;
    
    public SyntaxError(Token token, String message, String specificMessage) {
        super(message);
        this.token = token;
        this.specificMessage = specificMessage;
    }
    
    @Override
    public String getMessage() {
        if (token != null && token.type != Token.TokenType.EOF) {
            return String.format("[Línea %d, Col %d] Error en '%s': %s",
                    token.line, token.column, token.lexeme, specificMessage);
        } else if (token != null) {
            return String.format("[Línea %d, Col %d] Error al final de la entrada: %s",
                    token.line, token.column, specificMessage);
        }
        return specificMessage;
    }
}