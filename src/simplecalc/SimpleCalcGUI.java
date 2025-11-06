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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashSet;
import java.util.Set;


public class SimpleCalcGUI extends JFrame {
    private JTextArea inputArea;
    private JTextArea outputArea; // Declara aquí para que sea accesible
    private JLabel statusLabel;
    private Highlighter.HighlightPainter errorPainter;

    // Campos para almacenar métricas del Parser OPTIMIZADO
    private int lastOptimizedTotalQuadruples;
    private int lastOptimizedUniqueTempVars;
    private long lastOptimizedCompilationDurationMs;

    // Campos para almacenar métricas del LegacyParser (NO OPTIMIZADO)
    private int lastLegacyTotalQuadruples;
    private int lastLegacyUniqueTempVars;
    private long lastLegacyCompilationDurationMs;

    // Campo para almacenar la instancia del parser si el análisis optimizado fue exitoso
    private Parser lastSuccessfulParser;


    public SimpleCalcGUI() {
        setTitle("Kotlin IDE - Compilador");
        setSize(1200, 700);
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

        LineNumberingTextArea lineNumbersInput = new LineNumberingTextArea(inputArea); // Renombrado para claridad
        inputScrollPane.setRowHeaderView(lineNumbersInput);

        // Código de ejemplo para pruebas de optimización
        inputArea.setText("fun main() {\n" +
                "    val valorInicial: Int = 100\n" +
                "    val constante1: Int = 5 + 3\n" +
                "    var constante2: Int = (20 / 4) * 2\n" +
                "    \n" +
                "    print(\"Constante 1: \")\n" +
                "    print(constante1)\n" +
                "    print(\"\\nConstante 2: \")\n" +
                "    print(constante2)\n" +
                "    print(\"\\n\")\n" +
                "\n" +
                "    val a: Int = valorInicial\n" +
                "    var b: Int = a\n" +
                "    var c: Int = b + constante1\n" +
                "    \n" +
                "    print(\"Valor de c: \")\n" +
                "    print(c)\n" +
                "    print(\"\\n\")\n" +
                "\n" +
                "    val d: Int = 10\n" +
                "    var e: Int = d * (constante1 - 2)\n" +
                "    \n" +
                "    print(\"Valor de e: \")\n" +
                "    print(e)\n" +
                "    print(\"\\n\")\n" +
                "\n" +
                "    var f: Int = e\n" +
                "    val g: Int = f / 2 + constante2\n" +
                "    \n" +
                "    print(\"Valor de g: \")\n" +
                "    print(g)\n" +
                "    print(\"\\n\")\n" +
                "    \n" +
                "    val h: Int = a\n" +
                "    var i: Int = h + b\n" +
                "    \n" +
                "    print(\"Valor de i: \")\n" +
                "    print(i)\n" +
                "    print(\"\\n\")\n" +
                "\n" +
                "    val k: Int = 7\n" +
                "    var l: Int = k * (a + 3) - (b / c)\n" +
                "    \n" +
                "    print(\"Valor de l: \")\n" +
                "    print(l)\n" +
                "    print(\"\\n\")\n" +
                "\n" +
                "    print(\"Fin del programa de prueba de optimizacion.\\n\")\n" +
                "}");

        outputArea = new JTextArea();
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        outputArea.setEditable(false);
        outputArea.setLineWrap(true); // Habilitar ajuste de línea
        outputArea.setWrapStyleWord(true); // Ajuste de palabra para que no rompa palabras
        outputArea.setRows(100);   
        JScrollPane outputScrollPane = new JScrollPane(outputArea);
        
        outputScrollPane.setBorder(BorderFactory.createTitledBorder("Salida del Compilador"));
        
        // AÑADIR NÚMEROS DE LÍNEA AL ÁREA DE SALIDA
        LineNumberingTextArea lineNumbersOutput = new LineNumberingTextArea(outputArea);
        outputScrollPane.setRowHeaderView(lineNumbersOutput);


        JButton processButton = new JButton("Compilar");
        processButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                processCode();
            }
        });

        JButton lexicalButton = new JButton("Léxico");
        lexicalButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                processLexicalAnalysis();
            }
        });

        JButton syntaxButton = new JButton("Sintáctico");
        syntaxButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                processSyntaxAnalysis();
            }
        });

        JButton semanticButton = new JButton("Semántico");
        semanticButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                processSemanticAnalysis();
            }
        });

        // Botones para el Parser OPTIMIZADO
        JButton generateOptimizedIntermediateButton = new JButton("Intermedio Optimizado");
        generateOptimizedIntermediateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                generateOptimizedIntermediateCode();
            }
        });

        JButton showOptimizedMetricsButton = new JButton("Métricas Optimizado");
        showOptimizedMetricsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                displayOptimizedMetrics();
            }
        });

        

        // Botones para el LegacyParser (NO OPTIMIZADO)
        JButton generateLegacyIntermediateButton = new JButton("Intermedio Sin Optimizar");
        generateLegacyIntermediateButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                generateLegacyIntermediateCode();
            }
        });

        JButton showLegacyMetricsButton = new JButton("Métricas Sin Optimizar");
        showLegacyMetricsButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                displayLegacyMetrics();
            }
        });
        
        // NUEVO BOTÓN: Mostrar Código Kotlin Optimizado
        JButton showOptimizedKotlinCodeButton = new JButton("Código Optimizado");
        showOptimizedKotlinCodeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showOptimizedKotlinCode();
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

        // Panel de botones con todos los botones
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        buttonPanel.add(processButton);
        buttonPanel.add(lexicalButton);
        buttonPanel.add(syntaxButton);
        buttonPanel.add(semanticButton);
        buttonPanel.add(generateOptimizedIntermediateButton);
        buttonPanel.add(showOptimizedMetricsButton);
        buttonPanel.add(generateLegacyIntermediateButton);
        buttonPanel.add(showLegacyMetricsButton);
        buttonPanel.add(showOptimizedKotlinCodeButton);
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

        // Instancia un nuevo parser cada vez para asegurar un estado limpio
        Parser parser = new Parser(tokens);
        parser.parse();
        List<String> allParserErrors = parser.getErrors();

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
        // NOTA: NO GUARDAMOS el parser aquí. lastSuccessfulParser solo se actualiza
        // si se ejecuta "Generar Intermedio (Optimizado)" exitosamente.
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

        // Instancia un nuevo parser cada vez para asegurar un estado limpio
        Parser parser = new Parser(tokens);
        parser.parse();
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

        // Instancia un nuevo parser cada vez para asegurar un estado limpio
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

    // --- Métodos para el Parser OPTIMIZADO ---
    private void generateOptimizedIntermediateCode() {
        outputArea.setText("");
        inputArea.getHighlighter().removeAllHighlights();
        statusLabel.setText("Generando código intermedio (Optimizado)...");
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

        long startTime = System.nanoTime();

        // Siempre crear un nuevo parser para asegurar un estado limpio
        Parser parser = new Parser(tokens);
        parser.parse();
        List<String> allParserErrors = parser.getErrors();

        long endTime = System.nanoTime();
        lastOptimizedCompilationDurationMs = (endTime - startTime) / 1_000_000;

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
            sb.append("\n>>> No se puede generar código intermedio optimizado debido a los errores anteriores. <<<\n");
            statusLabel.setText("Resultado: Generación de Intermedio Optimizado FALLIDA.");
            statusLabel.setForeground(Color.RED);

            lastOptimizedTotalQuadruples = 0;
            lastOptimizedUniqueTempVars = 0;
            this.lastSuccessfulParser = null; // Reiniciar si hay errores

        } else {
            sb.append("--- Generación de Código Intermedio (Optimizado) ---\n\n");
            List<Parser.ExpressionData> expressions = parser.getCollectedExpressions();

            int currentTotalQuadruples = 0;
            Set<String> currentUniqueTempVars = new HashSet<>();

            if (expressions.isEmpty()) {
                sb.append("No se encontraron expresiones válidas para procesar.\n");
            } else {
                for (int i = 0; i < expressions.size(); i++) {
                    Parser.ExpressionData data = expressions.get(i);
                    sb.append("Expresión #").append(i + 1).append(" ");
                    sb.append("Línea ").append(data.lineNumber).append("\n");
                    sb.append(data.toString()).append("\n");

                    currentTotalQuadruples += data.quadruples.size();
                    Pattern p = Pattern.compile("t\\d+");
                    for (String quad : data.quadruples) {
                        Matcher m = p.matcher(quad);
                        while (m.find()) {
                            currentUniqueTempVars.add(m.group());
                        }
                    }
                }
            }

            lastOptimizedTotalQuadruples = currentTotalQuadruples;
            lastOptimizedUniqueTempVars = currentUniqueTempVars.size();

            sb.append(">>> Código intermedio optimizado generado exitosamente. <<<\n");
            statusLabel.setText("Resultado: Intermedio Optimizado Generado.");
            statusLabel.setForeground(new Color(0, 128, 0));
            this.lastSuccessfulParser = parser; // ¡Guarda la instancia del parser si es exitoso!
        }

        outputArea.setText(sb.toString());
        outputArea.setCaretPosition(0);
    }

    private void displayOptimizedMetrics() {
        outputArea.setText("");
        inputArea.getHighlighter().removeAllHighlights();

        StringBuilder sb = new StringBuilder();
        sb.append("--- Métricas de la Última Generación de Código Intermedio (Optimizado) ---\n");
        sb.append("  Tiempo de procesamiento del Parser (con optimizaciones): ").append(lastOptimizedCompilationDurationMs).append(" ms\n");
        sb.append("  Total de Cuádruplos generados: ").append(lastOptimizedTotalQuadruples).append("\n");
        sb.append("  Máximo de Variables Temporales distintas: ").append(lastOptimizedUniqueTempVars).append("\n");
        sb.append("\nPara obtener métricas actualizadas, genere el código intermedio (Optimizado) primero.\n");

        outputArea.setText(sb.toString());
        outputArea.setCaretPosition(0);
        statusLabel.setText("Métricas optimizadas mostradas.");
        statusLabel.setForeground(Color.BLUE);
    }

    // --- NUEVO MÉTODO: Mostrar Código Kotlin Optimizado ---
    private void showOptimizedKotlinCode() {
        outputArea.setText("");
        inputArea.getHighlighter().removeAllHighlights();
        statusLabel.setText("Generando código Kotlin optimizado...");
        statusLabel.setForeground(Color.BLACK);

        // Se requiere que el paso de "Generar Intermedio (Optimizado)" se haya ejecutado exitosamente
        // para que 'lastSuccessfulParser' contenga una instancia válida y sin errores.
        if (lastSuccessfulParser == null || !lastSuccessfulParser.getErrors().isEmpty()) {
            outputArea.setText("No se pudo generar código Kotlin optimizado. Asegúrese de que el análisis de Código Intermedio (Optimizado) haya sido exitoso (sin errores léxicos, sintácticos o semánticos).\n");
            statusLabel.setText("Generación de Código Kotlin Optimizado FALLIDA (errores o no se compiló).");
            statusLabel.setForeground(Color.RED);
            return;
        }

        try {
            KotlinCodeGenerator codeGenerator = new KotlinCodeGenerator(
                lastSuccessfulParser.getCollectedExpressions(),
                lastSuccessfulParser.getVariableTypes(), // Usar el getter público
                lastSuccessfulParser.getVariableIsVar()
            );
            String optimizedKotlinCode = codeGenerator.generateOptimizedKotlinCode();

            outputArea.setText("--- Código Kotlin Optimizado ---\n\n");
            outputArea.append(optimizedKotlinCode);
            outputArea.append("\n--- Fin del Código Kotlin Optimizado ---\n");
            statusLabel.setText("Código Kotlin optimizado generado y mostrado.");
            statusLabel.setForeground(Color.BLUE);

        } catch (Exception ex) {
            outputArea.setText("Error al generar el código Kotlin optimizado: " + ex.getMessage() + "\n");
            statusLabel.setText("Generación de Código Kotlin Optimizado FALLIDA.");
            statusLabel.setForeground(Color.RED);
            ex.printStackTrace();
        }
    }


    // --- Métodos para el LegacyParser (NO OPTIMIZADO) ---
    private void generateLegacyIntermediateCode() {
        outputArea.setText("");
        inputArea.getHighlighter().removeAllHighlights();
        statusLabel.setText("Generando código intermedio (No Optimizado)...");
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

        long startTime = System.nanoTime();

        // Siempre crear un nuevo legacyParser para asegurar un estado limpio
        LegacyParser legacyParser = new LegacyParser(tokens);
        legacyParser.parse();
        List<String> allParserErrors = legacyParser.getErrors();

        long endTime = System.nanoTime();
        lastLegacyCompilationDurationMs = (endTime - startTime) / 1_000_000;

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
            sb.append("\n>>> No se puede generar código intermedio no optimizado debido a los errores anteriores. <<<\n");
            statusLabel.setText("Resultado: Generación de Intermedio No Optimizado FALLIDA.");
            statusLabel.setForeground(Color.RED);

            lastLegacyTotalQuadruples = 0;
            lastLegacyUniqueTempVars = 0;

        } else {
            sb.append("--- Generación de Código Intermedio (No Optimizado) ---\n\n");
            List<LegacyParser.ExpressionData> expressions = legacyParser.getCollectedExpressions(); // Usar LegacyParser.ExpressionData

            int currentTotalQuadruples = 0;
            Set<String> currentUniqueTempVars = new HashSet<>();

            if (expressions.isEmpty()) {
                sb.append("No se encontraron expresiones válidas para procesar.\n");
            } else {
                for (int i = 0; i < expressions.size(); i++) {
                    LegacyParser.ExpressionData data = expressions.get(i); // Usar LegacyParser.ExpressionData
                    sb.append("Expresión #").append(i + 1).append(" ");
                    sb.append("Línea ").append(data.lineNumber).append("\n");
                    sb.append(data.toString()).append("\n");

                    currentTotalQuadruples += data.quadruples.size();
                    Pattern p = Pattern.compile("t\\d+");
                    for (String quad : data.quadruples) {
                        Matcher m = p.matcher(quad);
                        while (m.find()) {
                            currentUniqueTempVars.add(m.group());
                        }
                    }
                }
            }

            lastLegacyTotalQuadruples = currentTotalQuadruples;
            lastLegacyUniqueTempVars = currentUniqueTempVars.size();

            sb.append(">>> Código intermedio no optimizado generado exitosamente. <<<\n");
            statusLabel.setText("Resultado: Intermedio No Optimizado Generado.");
            statusLabel.setForeground(new Color(0, 128, 0));
        }

        outputArea.setText(sb.toString());
        outputArea.setCaretPosition(0);
    }

    private void displayLegacyMetrics() {
        outputArea.setText("");
        inputArea.getHighlighter().removeAllHighlights();

        StringBuilder sb = new StringBuilder();
        sb.append("--- Métricas de la Última Generación de Código Intermedio (No Optimizado) ---\n");
        sb.append("  Tiempo de procesamiento del Parser (sin optimizaciones): ").append(lastLegacyCompilationDurationMs).append(" ms\n");
        sb.append("  Total de Cuádruplos generados: ").append(lastLegacyTotalQuadruples).append("\n");
        sb.append("  Máximo de Variables Temporales distintas: ").append(lastLegacyUniqueTempVars).append("\n");
        sb.append("\nPara obtener métricas actualizadas, genere el código intermedio (No Optimizado) primero.\n");

        outputArea.setText(sb.toString());
        outputArea.setCaretPosition(0);
        statusLabel.setText("Métricas no optimizadas mostradas.");
        statusLabel.setForeground(Color.MAGENTA); // Un color distinto para métricas legacy
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