/*
============================================================
Proyecto: Calculadora Simple basado en Kotlin
Desarrollado por 
Orozco Reyes Hiram
Ortiz Ceballso Jorge
Salgado Rojas Marelin Iral

Lenguajes y Automatas II
Profa: Martínez Moreno Martha

Avance 2: se genera la validación por código intermedio
Transforma operaciones aritméticas de infija a prefija
Utiliza cuadruplos para manejar las operaciones aritmeticas
Almacena variables temporales para asignar los valores a variables declaradas
Branch: Prefija.


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