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
        setTitle("KotlinCalc IDE - Compilador");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initComponents();
        errorPainter = new DefaultHighlighter.DefaultHighlightPainter(Color.PINK);
    }

    private void initComponents() {
        // Paneles
        JPanel mainPanel = new JPanel(new BorderLayout(5, 5));
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        JPanel centerPanel = new JPanel(new GridLayout(1, 1));
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        // Componentes
        inputArea = new JTextArea();
        inputArea.setFont(new Font("Monospaced", Font.PLAIN, 14));
        JScrollPane inputScrollPane = new JScrollPane(inputArea);
        inputScrollPane.setBorder(BorderFactory.createTitledBorder("Código KotlinCalc"));

        // Aquí añadimos la numeración
        LineNumberingTextArea lineNumbers = new LineNumberingTextArea(inputArea);
        inputScrollPane.setRowHeaderView(lineNumbers);


        // Texto de ejemplo en Kotlin
        inputArea.setText("fun main() {\n" +
                "    val numero: Int = 42\n" +
                "    var resultado: Int = numero + 10\n" +
                "    print(\"El resultado es: \")\n" +
                "    print(resultado)\n" +
                "    \n" +
                "    val input: String = readLine()\n" +
                "    \n" +
                "    if (resultado > 50) {\n" +
                "        print(\"Mayor que 50\")\n" +
                "    }\n" +
                "}");

        outputArea = new JTextArea();
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        outputArea.setEditable(false);
        JScrollPane outputScrollPane = new JScrollPane(outputArea);
        outputScrollPane.setBorder(BorderFactory.createTitledBorder("Salida del Compilador"));

        // Botón para compilar código
        JButton processButton = new JButton("Compilar Código KotlinCalc");
        processButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                processCode();
            }
        });

        // Nuevo botón para cargar archivo
        JButton loadFileButton = new JButton("Cargar Archivo");
        loadFileButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadFile();
            }
        });

        // Nuevo botón para limpiar
        JButton clearButton = new JButton("Limpiar");
        clearButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                clearEditorAndOutput();
            }
        });
        
        // Panel para agrupar los botones
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(processButton);
        buttonPanel.add(loadFileButton);
        buttonPanel.add(clearButton);


        statusLabel = new JLabel("Listo.");

        // Layout
        topPanel.add(inputScrollPane, BorderLayout.CENTER);
        topPanel.add(buttonPanel, BorderLayout.SOUTH); // Añadir el panel de botones aquí

        centerPanel.add(outputScrollPane);

        bottomPanel.add(statusLabel);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        // Preferencias de tamaño para las áreas de texto
        inputScrollPane.setPreferredSize(new Dimension(780, 200));
        outputScrollPane.setPreferredSize(new Dimension(780, 300));

        setContentPane(mainPanel);
    }

    private void processCode() {
        String sourceCode = inputArea.getText();
        outputArea.setText(""); // Limpiar salida anterior
        inputArea.getHighlighter().removeAllHighlights(); // Limpiar resaltados de error anteriores

        // 1. Análisis Léxico
        Lexer lexer = new Lexer(sourceCode);
        List<Token> tokens = lexer.scanTokens();
        
        System.out.println("----- TOKENS DEL LEXER (Total: " + tokens.size() + ") -----");

        StringBuilder sb = new StringBuilder();
        sb.append("--- Tokens Reconocidos ---\n");
        sb.append(Token.getTableHeader()).append("\n");
        for (Token token : tokens) {
            // Modificación: No imprimir EOL ni EOF en la tabla de análisis léxico
            if (token.type != Token.TokenType.EOL && token.type != Token.TokenType.EOF) {
                sb.append(token.toString()).append("\n");
            }
        }
        sb.append(Token.getTableFooter()).append("\n\n");

        // Filtrar tokens de error léxico para mostrar en la lista de errores
        List<String> lexicalErrors = tokens.stream()
                                           .filter(t -> t.type == Token.TokenType.ERROR)
                                           .map(t -> String.format("[Línea %d, Col %d] Error Léxico: %s", t.line, t.column, t.lexeme))
                                           .collect(Collectors.toList());

        // 2. Análisis Sintáctico (y Semántico Básico)
        Parser parser = new Parser(tokens);
        boolean syntaxValid = parser.parse();
        List<String> syntaxAndSemanticErrors = parser.getErrors();

        // 3. Mostrar Resultados
        if (!lexicalErrors.isEmpty()) {
            sb.append("--- Errores Léxicos Detectados ---\n");
            for (String err : lexicalErrors) {
                sb.append(err).append("\n");
                try {
                    // Intentar resaltar el error léxico en el inputArea
                    if (err.startsWith("[")) {
                        String locationPart = err.substring(1, err.indexOf("]"));
                        String[] parts = locationPart.split(",");
                        int line = Integer.parseInt(parts[0].replace("Línea ", "").trim());
                        int col = Integer.parseInt(parts[1].replace("Col ", "").trim());
                        highlightError(line, col, getLexemeLength(err)); // Resaltar el lexema completo del error
                    }
                } catch (Exception ex) {
                    System.err.println("Error al intentar resaltar error léxico: " + ex.getMessage());
                }
            }
            sb.append("\n");
        }

        if (!syntaxAndSemanticErrors.isEmpty()) {
            sb.append("--- Errores Sintácticos/Semánticos Detectados ---\n");
            for (String err : syntaxAndSemanticErrors) {
                sb.append(err).append("\n");
                // Intentar resaltar el error en el inputArea
                try {
                    // Formato esperado: "[Línea L, Col C] Error..."
                    if (err.startsWith("[")) {
                        String locationPart = err.substring(1, err.indexOf("]"));
                        String[] parts = locationPart.split(",");
                        int line = Integer.parseInt(parts[0].replace("Línea ", "").trim());
                        int col = Integer.parseInt(parts[1].replace("Col ", "").trim());
                        highlightError(line, col); // Resaltar solo un carácter para errores sintácticos/semánticos
                    }
                } catch (Exception ex) {
                    // No se pudo parsear la ubicación del error del mensaje
                    System.err.println("Error al intentar resaltar error sintáctico/semántico: " + ex.getMessage());
                }
            }
            sb.append("\n");
        }

        if (lexicalErrors.isEmpty() && syntaxValid) {
            sb.append(">>> El código KotlinCalc es léxica y sintácticamente VÁLIDO. <<<\n");
            statusLabel.setText("Resultado: VÁLIDO.");
            statusLabel.setForeground(new Color(0, 128, 0)); // Verde
        } else {
            sb.append(">>> El código contiene errores. <<<\n");
            statusLabel.setText("Resultado: INVÁLIDO.");
            statusLabel.setForeground(Color.RED);
        }

        outputArea.setText(sb.toString());
        outputArea.setCaretPosition(0); // Scroll al inicio
    }
    
    // Método auxiliar para obtener la longitud del lexema problemático en el mensaje de error léxico
    private int getLexemeLength(String errorMessage) {
        int startIndex = errorMessage.indexOf("Error Léxico: '");
        if (startIndex != -1) {
            startIndex += "Error Léxico: '".length();
            int endIndex = errorMessage.indexOf("'", startIndex);
            if (endIndex != -1) {
                return endIndex - startIndex;
            }
        }
        startIndex = errorMessage.indexOf("Secuencia de caracteres inesperada: '");
        if (startIndex != -1) {
            startIndex += "Secuencia de caracteres inesperada: '".length();
            int endIndex = errorMessage.indexOf("'", startIndex);
            if (endIndex != -1) {
                return endIndex - startIndex;
            }
        }
        return 1; // Por defecto, resaltar un solo carácter
    }

    private void highlightError(int line, int col) {
        highlightError(line, col, 1); // Resaltar un solo carácter por defecto
    }

    private void highlightError(int line, int col, int length) {
        try {
            // Las líneas/columnas son 1-based, JTextArea es 0-based
            int docLine = line - 1;
            int docCol = col - 1;

            int startOffset = inputArea.getLineStartOffset(docLine) + docCol;
            int endOffset = startOffset + length;
            
            // Asegurarse que endOffset no exceda la longitud del texto o la línea
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

    // Nuevo método para cargar archivos
    private void loadFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setCurrentDirectory(new java.io.File(".")); // Directorio actual por defecto
        fileChooser.setDialogTitle("Seleccionar Archivo de Código KotlinCalc");
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

    // Nuevo método para limpiar el editor y la salida
    private void clearEditorAndOutput() {
        inputArea.setText("");
        outputArea.setText("");
        inputArea.getHighlighter().removeAllHighlights();
        statusLabel.setText("Editor y salida limpios.");
        statusLabel.setForeground(Color.BLACK);
    }

    public static void main(String[] args) {
        // Para mejor look & feel en algunos sistemas
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