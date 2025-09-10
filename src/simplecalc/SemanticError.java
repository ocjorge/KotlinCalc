package simplecalc;

public class SemanticError extends RuntimeException {
    public final Token token;
    public final String specificMessage;
    
    public SemanticError(Token token, String message, String specificMessage) {
        super(message);
        this.token = token;
        this.specificMessage = specificMessage;
    }
    
    @Override
    public String getMessage() {
        if (token != null) {
            return String.format("[Línea %d, Col %d] Error semántico cerca de '%s': %s",
                    token.line, token.column, token.lexeme, specificMessage);
        }
        return specificMessage;
    }
}