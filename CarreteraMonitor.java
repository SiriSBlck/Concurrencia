// Nunca cambia la declaracion del package!
package cc.carretera;

import es.upm.babel.cclib.Monitor;
import es.upm.aedlib.map.*;
/**
 * Implementación del recurso compartido Carretera con Monitores
 */
public class CarreteraMonitor implements Carretera {
  // TODO: añadir atributos para representar el estado del recurso y
  // la gestión de la concurrencia (monitor y conditions)
  private int nSegmentos;
  private int nCarriles; 
  private HashTableMap<String, Pos> segOcup; //indica qué segmentos de la carretera están ocupados por qué coche
  private HashTableMap<String, Integer> tiempoRestante; //indica cuánto le queda al coche que ocupa en ese segmento para abandonarlo
  private Monitor mutex; // monitor para garantizar la exclusión mutua
  private Monitor pEntrar; //monitor que controla la entrada
  private Monitor pAvanzar; //monitor que controla el avance
  private Monitor pCircular; //monitor que controla la circulación

  public CarreteraMonitor(int segmentos, int carriles) {
    // TODO: inicializar estado, monitor y conditions
  }

  public Pos entrar(String id, int tks) {
    // TODO: implementar entrar
    return null;
  }

  public Pos avanzar(String id, int tks) {
    // TODO: implementar avanzar
    return null;
  }

  public void circulando(String id) {
    // TODO: implementar circulando
  }

  public void salir(String id) {
    this.tiempoRestante.remove(id); 
    this.tiempoRestante.put(id, this.nSegmentos); //añadimos la nueva entrada de ese coche con ticks a 0
    Pos act = this.segOcup.get(id); //devuelve la posición del coche que sale
    this.segOcup.remove(id);
    this.segOcup.put(id, new Pos(0, act.getCarril()));
  }

  public void tick() {
    // TODO: implementar tick
  }
}
