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
        setSize(1000, 600); 
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
                "    val numero: Int = 42\n" +
                "    var contador: Int = 0\n" +
                "    while (contador < 3) {\n" +
                "        print(\"Contador: \")\n" +
                "        print(contador)\n" +
                "        print(\"\\n\")\n" +
                "        contador = contador + 1\n" +
                "    }\n" +
                "    \n" +
                "    val limiteFor: Int = 5\n" +
                "    for (i in 0..limiteFor) {\n" +
                "        print(\"Iteracion For: \")\n" +
                "        print(i)\n" +
                "        print(\"\\n\")\n" +
                "    }\n" +
                "    \n" +
                "    val input: String = readLine()\n" +
                "    if (numero > 50) {\n" +
                "        print(\"Mayor que 50\")\n" +
                "    }\n" +
                "    print(\"Fin del programa de ejemplo.\")\n" +
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
        boolean syntaxValid = parser.parse();
        List<String> syntaxAndSemanticErrors = parser.getErrors();

        if (!lexicalErrors.isEmpty()) {
            sb.append("--- Errores Léxicos Detectados ---\n");
            for (String err : lexicalErrors) {
                sb.append(err).append("\n");
                highlightErrorFromMessage(err);
            }
            sb.append("\n");
        }

        if (!syntaxAndSemanticErrors.isEmpty()) {
            sb.append("--- Errores Sintácticos/Semánticos Detectados ---\n");
            for (String err : syntaxAndSemanticErrors) {
                sb.append(err).append("\n");
                highlightErrorFromMessage(err);
            }
            sb.append("\n");
        }

        if (lexicalErrors.isEmpty() && syntaxValid) {
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

    // Siempre mostrar errores léxicos si existen
    if (!lexicalErrors.isEmpty()) {
        sb.append("");
    }

    // Realizar análisis sintáctico independientemente de los errores léxicos
    Parser parser = new Parser(tokens);
    boolean syntaxValid = false;
    try {
        syntaxValid = parser.parse();
    } catch (Exception e) {
        sb.append("Error grave durante el análisis sintáctico: ").append(e.getMessage()).append("\n");
    }
    
    List<String> syntaxErrors = parser.getErrors().stream()
                                      .filter(err -> !err.contains("Error semántico"))
                                      .collect(Collectors.toList());

    // Mostrar errores sintácticos
    if (!syntaxErrors.isEmpty()) {
        sb.append("--- Errores Sintácticos Detectados ---\n");
        for (String err : syntaxErrors) {
            sb.append(err).append("\n");
            highlightErrorFromMessage(err);
        }
        sb.append("\n");
    }

    // Determinar el estado final
    if (!lexicalErrors.isEmpty() && !syntaxErrors.isEmpty()) {
        sb.append(">>> El código contiene errores léxicos y sintácticos. <<<\n");
        statusLabel.setText("Resultado: Léxica y sintácticamente INVÁLIDO.");
        statusLabel.setForeground(Color.RED);
    } else if (!lexicalErrors.isEmpty()) {
        sb.append(">>> El código contiene errores léxicos que pueden afectar el análisis sintáctico. <<<\n");
        statusLabel.setText("Resultado: Léxicamente INVÁLIDO.");
        statusLabel.setForeground(Color.RED);
    } else if (!syntaxErrors.isEmpty()) {
        sb.append(">>> El código es léxicamente válido pero contiene errores sintácticos. <<<\n");
        statusLabel.setText("Resultado: Sintácticamente INVÁLIDO.");
        statusLabel.setForeground(Color.RED);
    } else if (!syntaxValid) {
        sb.append("--- El análisis sintáctico terminó prematuramente o con estado inválido ---\n");
        statusLabel.setText("Resultado: Sintácticamente INVÁLIDO.");
        statusLabel.setForeground(Color.RED);
    } else {
        sb.append(">>> El código es léxica y sintácticamente VÁLIDO. <<<\n");
        statusLabel.setText("Resultado: Léxica y sintácticamente VÁLIDO.");
        statusLabel.setForeground(new Color(0, 128, 0));
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

        if (!lexicalErrors.isEmpty()) {
            sb.append("--- Errores Léxicos Detectados (Impiden Análisis Semántico) ---\n");
            for (String err : lexicalErrors) {
                sb.append(err).append("\n");
                highlightErrorFromMessage(err);
            }
            sb.append("\n");
            sb.append(">>> No se puede realizar análisis semántico debido a errores léxicos. <<<\n");
            statusLabel.setText("Resultado: Semánticamente INVÁLIDO (Errores léxicos).");
            statusLabel.setForeground(Color.RED);
        } else {
            Parser parser = new Parser(tokens);
            parser.parse();
            List<String> semanticErrors = parser.getErrors().stream()
                                               .filter(err -> err.contains("Error semántico"))
                                               .collect(Collectors.toList());
            List<String> syntaxErrors = parser.getErrors().stream()
                                                .filter(err -> !err.contains("Error semántico"))
                                                .collect(Collectors.toList());

            if (!syntaxErrors.isEmpty()) {
                sb.append("--- Errores Sintácticos Detectados (Impiden Análisis Semántico Completo) ---\n");
                for (String err : syntaxErrors) {
                    sb.append(err).append("\n");
                    highlightErrorFromMessage(err);
                }
                sb.append("\n");
                sb.append(">>> No se puede garantizar un análisis semántico completo debido a errores sintácticos. <<<\n");
                statusLabel.setText("Resultado: Semánticamente INVÁLIDO (Errores sintácticos).");
                statusLabel.setForeground(Color.RED);
            } else if (!semanticErrors.isEmpty()) {
                sb.append("--- Errores Semánticos Detectados ---\n");
                for (String err : semanticErrors) {
                    sb.append(err).append("\n");
                    highlightErrorFromMessage(err);
                }
                sb.append("\n");
                statusLabel.setText("Resultado: Semánticamente INVÁLIDO.");
                statusLabel.setForeground(Color.RED);
            } else {
                sb.append(">>> El código es semánticamente VÁLIDO. <<<\n");
                statusLabel.setText("Resultado: Semánticamente VÁLIDO.");
                statusLabel.setForeground(new Color(0, 128, 0));
            }
        }

        outputArea.setText(sb.toString());
        outputArea.setCaretPosition(0);
    }
    
    private void highlightErrorFromMessage(String errorMessage) {
        try {
            if (errorMessage.startsWith("[")) {
                String locationPart = errorMessage.substring(1, errorMessage.indexOf("]"));
                String[] parts = locationPart.split(",");
                // CORRECCIÓN: Acceder a los elementos del arreglo y aplicar replace
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

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new SimpleCalcGUI().setVisible(true);
            }
        });
    }
}