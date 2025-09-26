package simplecalc;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.stream.Collectors;
import java.nio.file.Files;

public class SimpleCalcGUI extends JFrame {
    private JTextArea inputArea;
    private JTextArea outputArea;
    private JLabel statusLabel;
    private Highlighter.HighlightPainter errorPainter;

    public SimpleCalcGUI() {
        setTitle("Kotlin IDE - Compilador");
        setSize(1200, 700); // Aumentar tamaño para la nueva salida
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
        errorPainter = new DefaultHighlighter.DefaultHighlightPainter(Color.PINK);
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        JPanel centerPanel = new JPanel(new GridLayout(1, 1));
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        inputArea = new JTextArea();
        inputArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane inputScrollPane = new JScrollPane(inputArea);
        inputScrollPane.setBorder(BorderFactory.createTitledBorder("Código Kotlin"));

        LineNumberingTextArea lineNumbers = new LineNumberingTextArea(inputArea);
        inputScrollPane.setRowHeaderView(lineNumbers);

        inputArea.setText("fun main() {\n" +
                "    val a: Int = 10\n" +
                "    var b: Int = a + 2 * 5\n" +
                "    val c: Int = (b - 3) / 2\n" +
                "    print(\"El valor de c es: \")\n" +
                "    print(c)\n" +
                "    print(\"\\n\")\n" +
                "    var x: Int = 0\n" +
                "    while (x < 3) {\n" +
                "        x = x + 1\n" +
                "        print(\"x en while: \")\n" +
                "        print(x)\n" +
                "        print(\"\\n\")\n" +
                "    }\n" +
                "    for (y in 1..2) {\n" +
                "        print(\"y en for: \")\n" +
                "        print(y * 10)\n" +
                "        print(\"\\n\")\n" +
                "    }\n" +
                "    val mensaje: String = \"Resultado: \" + c\n" +
                "    print(mensaje)\n" +
                "    print(\"\\n\")\n" +
                "}");

        outputArea = new JTextArea();
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        outputArea.setEditable(false);
        JScrollPane outputScrollPane = new JScrollPane(outputArea);
        outputScrollPane.setBorder(BorderFactory.createTitledBorder("Salida del Compilador"));

        JButton processButton = new JButton("Compilar Completo");
        processButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                processCode();
            }
        });

        JButton lexicalButton = new JButton("Análisis Léxico");
        lexicalButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                processLexicalAnalysis();
            }
        });

        JButton syntaxButton = new JButton("Análisis Sintáctico");
        syntaxButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                processSyntaxAnalysis();
            }
        });

        JButton semanticButton = new JButton("Análisis Semántico");
        semanticButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                processSemanticAnalysis();
            }
        });
        
        // NUEVO BOTÓN: Generar Intermedio
        JButton generateIntermediateButton = new JButton("Generar Intermedio");
        generateIntermediateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                generateIntermediateCode();
            }
        });

        JButton loadFileButton = new JButton("Cargar Archivo");
        loadFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadFile();
            }
        });

        JButton clearButton = new JButton("Limpiar");
        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clearEditorAndOutput();
            }
        });
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        buttonPanel.add(processButton);
        buttonPanel.add(lexicalButton);
        buttonPanel.add(syntaxButton);
        buttonPanel.add(semanticButton);
        buttonPanel.add(generateIntermediateButton); // Añadir el nuevo botón
        buttonPanel.add(loadFileButton);
        buttonPanel.add(clearButton);

        statusLabel = new JLabel("Listo.");

        topPanel.add(inputScrollPane, BorderLayout.CENTER);
        topPanel.add(buttonPanel, BorderLayout.SOUTH);

        centerPanel.add(outputScrollPane);

        bottomPanel.add(statusLabel);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        inputScrollPane.setPreferredSize(new Dimension(980, 200));
        outputScrollPane.setPreferredSize(new Dimension(980, 300));

        setContentPane(mainPanel);
    }

    private void processCode() {
        outputArea.setText("");
        inputArea.getHighlighter().removeAllHighlights();
        statusLabel.setText("Iniciando compilación completa...");
        statusLabel.setForeground(Color.BLACK);

        String sourceCode = inputArea.getText();
        Lexer lexer = new Lexer(sourceCode);
        List<Token> tokens = lexer.scanTokens();
        
        StringBuilder sb = new StringBuilder();
        List<String> lexicalErrors = tokens.stream()
                                           .filter(t -> t.type == Token.TokenType.ERROR)
                                           .map(t -> String.format("[Línea %d, Col %d] Error Léxico: %s", t.line, t.column, 
                                                                   t.errorMessage != null ? t.errorMessage : t.lexeme + " (Caracter inesperado)"))
                                           .collect(Collectors.toList());

        Parser parser = new Parser(tokens);
        parser.parse(); // Intentará ejecutar todo y recogerá todos los errores
        List<String> allParserErrors = parser.getErrors(); // Obtiene todos los errores (sintácticos y semánticos)

        if (!lexicalErrors.isEmpty()) {
            sb.append("--- Errores Léxicos Detectados ---\n");
            for (String err : lexicalErrors) {
                sb.append(err).append("\n");
                highlightErrorFromMessage(err);
            }
            sb.append("\n");
        }

        if (!allParserErrors.isEmpty()) {
            sb.append("--- Errores Sintácticos/Semánticos Detectados ---\n");
            for (String err : allParserErrors) {
                sb.append(err).append("\n");
                highlightErrorFromMessage(err);
            }
            sb.append("\n");
        }

        if (lexicalErrors.isEmpty() && allParserErrors.isEmpty()) {
            sb.append(">>> El código Kotlin es léxica, sintáctica y semánticamente VÁLIDO. <<<\n");
            statusLabel.setText("Resultado: VÁLIDO.");
            statusLabel.setForeground(new Color(0, 128, 0));
        } else {
            sb.append(">>> El código contiene errores. <<<\n");
            statusLabel.setText("Resultado: INVÁLIDO.");
            statusLabel.setForeground(Color.RED);
        }

        outputArea.setText(sb.toString());
        outputArea.setCaretPosition(0);
    }

    private void processLexicalAnalysis() {
        outputArea.setText("");
        inputArea.getHighlighter().removeAllHighlights();
        statusLabel.setText("Realizando análisis léxico...");
        statusLabel.setForeground(Color.BLACK);

        String sourceCode = inputArea.getText();
        Lexer lexer = new Lexer(sourceCode);
        List<Token> tokens = lexer.scanTokens();
        
        StringBuilder sb = new StringBuilder();
        sb.append("--- Tokens Reconocidos ---\n");
        sb.append(Token.getTableHeader()).append("\n");
        for (Token token : tokens) {
            if (token.type != Token.TokenType.EOL && token.type != Token.TokenType.EOF) {
                sb.append(token.toString()).append("\n");
            }
        }
        sb.append(Token.getTableFooter()).append("\n\n");

        List<String> lexicalErrors = tokens.stream()
                                           .filter(t -> t.type == Token.TokenType.ERROR)
                                           .map(t -> String.format("[Línea %d, Col %d] Error Léxico: %s", t.line, t.column, 
                                                                   t.errorMessage != null ? t.errorMessage : t.lexeme + " (Caracter inesperado)"))
                                           .collect(Collectors.toList());

        if (!lexicalErrors.isEmpty()) {
            sb.append("--- Errores Léxicos Detectados ---\n");
            for (String err : lexicalErrors) {
                sb.append(err).append("\n");
                highlightErrorFromMessage(err);
            }
            sb.append("\n");
            statusLabel.setText("Resultado: Léxicamente INVÁLIDO.");
            statusLabel.setForeground(Color.RED);
        } else {
            sb.append(">>> El código es léxicamente VÁLIDO. <<<\n");
            statusLabel.setText("Resultado: Léxicamente VÁLIDO.");
            statusLabel.setForeground(new Color(0, 128, 0));
        }

        outputArea.setText(sb.toString());
        outputArea.setCaretPosition(0);
    }

    private void processSyntaxAnalysis() {
        outputArea.setText("");
        inputArea.getHighlighter().removeAllHighlights();
        statusLabel.setText("Realizando análisis sintáctico...");
        statusLabel.setForeground(Color.BLACK);

        String sourceCode = inputArea.getText();
        Lexer lexer = new Lexer(sourceCode);
        List<Token> tokens = lexer.scanTokens();

        List<String> lexicalErrors = tokens.stream()
                                           .filter(t -> t.type == Token.TokenType.ERROR)
                                           .map(t -> String.format("[Línea %d, Col %d] Error Léxico: %s", t.line, t.column, 
                                                                   t.errorMessage != null ? t.errorMessage : t.lexeme + " (Caracter inesperado)"))
                                           .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();

        Parser parser = new Parser(tokens);
        parser.parse(); // Ejecuta el parseo y registra todos los errores (sintácticos y semánticos)

        List<String> allParserErrors = parser.getErrors();
        List<String> syntaxErrors = allParserErrors.stream()
                                              .filter(err -> !err.contains("Error semántico")) 
                                              .collect(Collectors.toList());

        if (!lexicalErrors.isEmpty()) {
            sb.append("--- Errores Léxicos Detectados ---\n");
            for (String err : lexicalErrors) {
                sb.append(err).append("\n");
                highlightErrorFromMessage(err);
            }
            sb.append("\n");
        }
        
        if (!syntaxErrors.isEmpty()) {
            sb.append("--- Errores Sintácticos Detectados ---\n");
            for (String err : syntaxErrors) {
                sb.append(err).append("\n");
                highlightErrorFromMessage(err);
            }
            sb.append("\n");
        }

        if (lexicalErrors.isEmpty() && syntaxErrors.isEmpty()) {
            sb.append(">>> El código es sintácticamente VÁLIDO. <<<\n");
            statusLabel.setText("Resultado: Sintácticamente VÁLIDO.");
            statusLabel.setForeground(new Color(0, 128, 0));
        } else {
            sb.append(">>> El código contiene errores sintácticos o léxicos. <<<\n");
            statusLabel.setText("Resultado: Sintácticamente INVÁLIDO.");
            statusLabel.setForeground(Color.RED);
        }

        outputArea.setText(sb.toString());
        outputArea.setCaretPosition(0);
    }

    private void processSemanticAnalysis() {
        outputArea.setText("");
        inputArea.getHighlighter().removeAllHighlights();
        statusLabel.setText("Realizando análisis semántico...");
        statusLabel.setForeground(Color.BLACK);

        String sourceCode = inputArea.getText();
        Lexer lexer = new Lexer(sourceCode);
        List<Token> tokens = lexer.scanTokens();

        List<String> lexicalErrors = tokens.stream()
                                           .filter(t -> t.type == Token.TokenType.ERROR)
                                           .map(t -> String.format("[Línea %d, Col %d] Error Léxico: %s", t.line, t.column, 
                                                                   t.errorMessage != null ? t.errorMessage : t.lexeme + " (Caracter inesperado)"))
                                           .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();

        Parser parser = new Parser(tokens);
        parser.parse(); 

        List<String> allParserErrors = parser.getErrors(); 
        List<String> semanticErrors = allParserErrors.stream()
                                               .filter(err -> err.contains("Error semántico"))
                                               .collect(Collectors.toList());
        List<String> syntaxErrors = allParserErrors.stream()
                                                .filter(err -> !err.contains("Error semántico"))
                                                .collect(Collectors.toList());

        if (!lexicalErrors.isEmpty()) {
            sb.append("--- Errores Léxicos Detectados ---\n");
            for (String err : lexicalErrors) {
                sb.append(err).append("\n");
                highlightErrorFromMessage(err);
            }
            sb.append("\n");
        }
        
        if (!syntaxErrors.isEmpty()) {
            sb.append("--- Errores Sintácticos Detectados ---\n");
            for (String err : syntaxErrors) {
                sb.append(err).append("\n");
                highlightErrorFromMessage(err);
            }
            sb.append("\n");
        }

        if (!semanticErrors.isEmpty()) {
            sb.append("--- Errores Semánticos Detectados ---\n");
            for (String err : semanticErrors) {
                sb.append(err).append("\n");
                highlightErrorFromMessage(err);
            }
            sb.append("\n");
        }

        if (lexicalErrors.isEmpty() && syntaxErrors.isEmpty() && semanticErrors.isEmpty()) {
            sb.append(">>> El código es semánticamente VÁLIDO. <<<\n");
            statusLabel.setText("Resultado: Semánticamente VÁLIDO.");
            statusLabel.setForeground(new Color(0, 128, 0));
        } else {
            sb.append(">>> El código contiene errores. <<<\n");
            statusLabel.setText("Resultado: Semánticamente INVÁLIDO.");
            statusLabel.setForeground(Color.RED);
        }

        outputArea.setText(sb.toString());
        outputArea.setCaretPosition(0);
    }
    
    // NUEVO: Método para generar el código intermedio
    private void generateIntermediateCode() {
        outputArea.setText("");
        inputArea.getHighlighter().removeAllHighlights();
        statusLabel.setText("Generando código intermedio...");
        statusLabel.setForeground(Color.BLACK);

        String sourceCode = inputArea.getText();
        Lexer lexer = new Lexer(sourceCode);
        List<Token> tokens = lexer.scanTokens();
        
        StringBuilder sb = new StringBuilder();
        List<String> lexicalErrors = tokens.stream()
                                           .filter(t -> t.type == Token.TokenType.ERROR)
                                           .map(t -> String.format("[Línea %d, Col %d] Error Léxico: %s", t.line, t.column, 
                                                                   t.errorMessage != null ? t.errorMessage : t.lexeme + " (Caracter inesperado)"))
                                           .collect(Collectors.toList());

        Parser parser = new Parser(tokens);
        parser.parse(); // Ejecutar parseo completo para recolectar expresiones y errores
        List<String> allParserErrors = parser.getErrors();

        if (!lexicalErrors.isEmpty() || !allParserErrors.isEmpty()) {
            sb.append("--- Errores Detectados (Impiden Generación de Código Intermedio) ---\n");
            for (String err : lexicalErrors) {
                sb.append(err).append("\n");
                highlightErrorFromMessage(err);
            }
            for (String err : allParserErrors) {
                sb.append(err).append("\n");
                highlightErrorFromMessage(err);
            }
            sb.append("\n>>> No se puede generar código intermedio debido a los errores anteriores. <<<\n");
            statusLabel.setText("Resultado: Generación de Intermedio FALLIDA.");
            statusLabel.setForeground(Color.RED);
        } else {
            sb.append("--- Generación de Código Intermedio ---\n\n");
            List<Parser.ExpressionData> expressions = parser.getCollectedExpressions();

            if (expressions.isEmpty()) {
                sb.append("No se encontraron expresiones aritméticas válidas para procesar.\n");
            } else {
                for (int i = 0; i < expressions.size(); i++) {
                    Parser.ExpressionData data = expressions.get(i);
                    sb.append("Expresión #").append(i + 1).append(":\n");
                    sb.append(data.toString()).append("\n");
                }
            }
            sb.append(">>> Código intermedio generado exitosamente. <<<\n");
            statusLabel.setText("Resultado: Intermedio Generado.");
            statusLabel.setForeground(new Color(0, 128, 0));
        }

        outputArea.setText(sb.toString());
        outputArea.setCaretPosition(0);
    }

    private void highlightErrorFromMessage(String errorMessage) {
        try {
            if (errorMessage.startsWith("[")) {
                String locationPart = errorMessage.substring(1, errorMessage.indexOf("]"));
                String[] parts = locationPart.split(",");
                int line = Integer.parseInt(parts[0].replace("Línea ", "").trim());
                int col = Integer.parseInt(parts[1].replace("Col ", "").trim());
                
                String lexemeToHighlight = "";
                int lexemeLength = 1;
                
                int startLexicalError = errorMessage.indexOf("Error Léxico: '");
                if (startLexicalError != -1) {
                    startLexicalError += "Error Léxico: '".length();
                    int endLexicalError = errorMessage.indexOf("'", startLexicalError);
                    if (endLexicalError != -1) {
                        lexemeToHighlight = errorMessage.substring(startLexicalError, endLexicalError);
                        lexemeLength = lexemeToHighlight.length();
                        highlightError(line, col, lexemeLength);
                        return;
                    }
                }
                
                int startSyntaxSemanticError = errorMessage.indexOf("Error en '");
                if (startSyntaxSemanticError != -1) {
                     startSyntaxSemanticError += "Error en '".length();
                     int endSyntaxSemanticError = errorMessage.indexOf("'", startSyntaxSemanticError);
                     if (endSyntaxSemanticError != -1) {
                         lexemeToHighlight = errorMessage.substring(startSyntaxSemanticError, endSyntaxSemanticError);
                         lexemeLength = lexemeToHighlight.length();
                         highlightError(line, col, lexemeLength);
                         return;
                     }
                }
                int startSemanticErrorNear = errorMessage.indexOf("Error semántico cerca de '");
                if (startSemanticErrorNear != -1) {
                    startSemanticErrorNear += "Error semántico cerca de '".length();
                    int endSemanticErrorNear = errorMessage.indexOf("'", startSemanticErrorNear);
                    if (endSemanticErrorNear != -1) {
                        lexemeToHighlight = errorMessage.substring(startSemanticErrorNear, endSemanticErrorNear);
                        lexemeLength = lexemeToHighlight.length();
                        highlightError(line, col, lexemeLength);
                        return;
                    }
                }
                
                highlightError(line, col, 1);

            }
        } catch (Exception ex) {
            System.err.println("Error al intentar resaltar desde el mensaje: " + ex.getMessage());
        }
    }

    private void highlightError(int line, int col) {
        highlightError(line, col, 1);
    }

    private void highlightError(int line, int col, int length) {
        try {
            int docLine = line - 1;
            int docCol = col - 1;

            int startOffset = inputArea.getLineStartOffset(docLine) + docCol;
            int endOffset = startOffset + length;
            
            if (startOffset < inputArea.getDocument().getLength()) {
                 endOffset = Math.min(endOffset, inputArea.getDocument().getLength());
                 endOffset = Math.min(endOffset, inputArea.getLineEndOffset(docLine));

                 if(startOffset < endOffset){
                     inputArea.getHighlighter().addHighlight(startOffset, endOffset, errorPainter);
                 } else if (startOffset == endOffset && startOffset < inputArea.getDocument().getLength()){
                     inputArea.getHighlighter().addHighlight(startOffset, startOffset +1, errorPainter);
                 }
            }

        } catch (BadLocationException ex) {
            System.err.println("Error al resaltar: No se pudo obtener la ubicación " + line + "," + col + ". Detalle: " + ex.getMessage());
        }
    }

    private void loadFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setCurrentDirectory(new java.io.File("."));
        fileChooser.setDialogTitle("Seleccionar Archivo de Código Kotlin");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Archivos Kotlin (*.kt, *.txt)", "kt", "txt"));

        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            java.io.File selectedFile = fileChooser.getSelectedFile();
            try {
                String content = new String(Files.readAllBytes(selectedFile.toPath()));
                inputArea.setText(content);
                outputArea.setText("Archivo '" + selectedFile.getName() + "' cargado exitosamente.\n");
                statusLabel.setText("Archivo cargado.");
                statusLabel.setForeground(Color.BLACK);
            } catch (java.io.IOException ex) {
                outputArea.setText("Error al leer el archivo: " + ex.getMessage() + "\n");
                statusLabel.setText("Error al cargar archivo.");
                statusLabel.setForeground(Color.RED);
                System.err.println("Error al cargar archivo: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    private void clearEditorAndOutput() {
        inputArea.setText("");
        outputArea.setText("");
        inputArea.getHighlighter().removeAllHighlights();
        statusLabel.setText("Editor y salida limpios.");
        statusLabel.setForeground(Color.BLACK);
    }


}