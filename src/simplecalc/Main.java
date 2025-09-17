/*
============================================================
Proyecto: Calculadora Simple basado en Kotlin
Desarrollado por 
Orozco Reyes Hiram
Ortiz Ceballso Jorge
Salgado Rojas Marelin Iral

Lenguajes y Automatas II
Profa: Martínez Moreno Martha

Descripción: programa que maneja etapas lexica, 
sintactica, semantica por validación de entradas.


*/

package simplecalc;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class Main {
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