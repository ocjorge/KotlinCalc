package simplecalc;

import javax.swing.*;
import javax.swing.text.Element;
import java.awt.*;

public class LineNumberingTextArea extends JTextArea {
    private JTextArea textArea;

    public LineNumberingTextArea(JTextArea textArea) {
        this.textArea = textArea;
        setEditable(false);
        setBackground(Color.LIGHT_GRAY);
        setFont(new Font("Monospaced", Font.PLAIN, 14));
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        int lineHeight = textArea.getFontMetrics(textArea.getFont()).getHeight();
        int startOffset = textArea.viewToModel(new Point(0, 0));
        Element root = textArea.getDocument().getDefaultRootElement();
        int startLine = root.getElementIndex(startOffset) + 1;
        int endLine = root.getElementIndex(textArea.viewToModel(
                new Point(0, textArea.getHeight()))) + 1;

        int y = 0;
        for (int i = startLine; i <= endLine; i++) {
            y = i * lineHeight - 3;
            g.drawString(String.valueOf(i), 5, y);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(40, textArea.getHeight());
    }
}