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
import java.lang.Runtime;


public class Main {
    
    public static void estadisticasSistema(){
        // Obtener el objeto Runtime
        Runtime runtime = Runtime.getRuntime();

        // Constante para convertir bytes a megabytes
        final double MB = 1024.0 * 1024.0;

        // Mostrar estadísticas del sistema
        System.out.println("\nESTADISTICAS DEL SISTEMA:");
        System.out.println("   Procesadores disponibles: " + runtime.availableProcessors());
        System.out.println("   Memoria maxima: " + (runtime.maxMemory() / MB) + " MB");
        System.out.println("   Memoria total: " + (runtime.totalMemory() / MB) + " MB");
        System.out.println("   Memoria libre: " + (runtime.freeMemory() / MB) + " MB");
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
        
        estadisticasSistema();
        
    }
}