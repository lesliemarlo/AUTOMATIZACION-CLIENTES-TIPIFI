package com.informaperu.cliente.config;

public class ProgressBar {
	 private static final int BAR_WIDTH = 50; // Width of progress bar
	    private int lastProgress = 0;
	    
	    /**
	     * Actualiza la barra de progreso
	     * @param progress Porcentaje de progreso (0-100)
	     */
	    public void update(int progress) {
	        // Avoid printing again if progress hasn't changed significantly
	        if (progress - lastProgress < 5 && progress < 100 && lastProgress > 0) {
	            return;
	        }
	        
	        lastProgress = progress;
	        
	        int completed = (int) (BAR_WIDTH * progress / 100.0);
	        StringBuilder bar = new StringBuilder("[");
	        
	        for (int i = 0; i < BAR_WIDTH; i++) {
	            if (i < completed) {
	                bar.append("█");
	            } else {
	                bar.append(" ");
	            }
	        }
	        
	        bar.append("] ").append(progress).append("% completado");
	        
	        // Clear the current line and update the progress bar
	        System.out.print("\r" + bar);
	        
	        // Add a newline when progress is complete
	        if (progress >= 100) {
	            System.out.println();
	        }
	    }
}
