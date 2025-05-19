// Nunca cambia la declaracion del package!
package cc.carretera;

import es.upm.babel.cclib.Monitor;
import es.upm.aedlib.map.HashTableMap;

/**
 * Implementación del recurso compartido Carretera con Monitores
 */
public class CarreteraMonitor implements Carretera {

  private final int nSegmentos; //número de segmentos (parámetro constructor) 
  private final int nCarriles; //número de carriles (parámetro constructor) 
  
  private final String[][] posOcup; //indica qué segmentos de la carretera están ocupados por qué coche
  private final HashTableMap<String, Pos> segOcup; //para evitar consultas de complejudad O(n) al buscar la posición 
    //que ocupa un coche dado su id, empleamos esta estructura con búsquedas O(cte)
  private final HashTableMap<String, Integer> tiempoRestante; //indica cuánto le queda al coche que ocupa en ese segmento para abandonarlo

  private final Monitor mutex; //monitor para garantizar la exclusión mutua
  private final Monitor.Cond cond; //una única condición

  public CarreteraMonitor(int segmentos, int carriles) {
    this.nSegmentos  = segmentos;
    this.nCarriles   = carriles;

    this.posOcup = new String[segmentos][carriles];
    this.segOcup = new HashTableMap<>();
    this.tiempoRestante = new HashTableMap<>();

    this.mutex = new Monitor();
    this.cond = mutex.newCond();
  }

  /** Coloca el coche <id> en el primer segmento cuando haya hueco. */
  public Pos entrar(String id, int tks) {
    this.mutex.enter();
    try {
      while (!hayHueco(0)) {
        this.cond.await(); //bloqueamos el coche si todos los carriles del primer segmento están ocupados
      }

      //buscamos un hueco libre en algún carril del primer segmento
      int carrilLibre = carrilLibre(0); 
      this.posOcup[0][carrilLibre] = id; //como hay hueco, cuando lo encontramos, lo ocupamos con el coche
      Pos res = new Pos(1, carrilLibre + 1); 
      this.segOcup.put(id, res);
      this.tiempoRestante.put(id, tks);
      this.cond.signal(); //liberamos al coche del bloqueo
      return res;

    } finally {
      this.mutex.leave();
    }
  }

  public Pos avanzar(String id, int tks) {
    this.mutex.enter();
    try {
      while (true) {
        Pos act = this.segOcup.get(id); //obtenemos la posición actual del coche(empezando por 1)
        int segArray = act.getSegmento() - 1; //y seleccionamos el segmento actual(empezando por 0)

        if (segArray + 1 >= this.nSegmentos) {
          return null; //el coche ya está en el último segmento
        }

        //buscamos un hueco libre en algún carril del siguiente segmento
        if (this.tiempoRestante.get(id) == 0 && hayHueco(segArray + 1)) {
          // si lo encontramos vaciamos la posición anterior y ocupamos la nueva
          this.posOcup[segArray][act.getCarril() - 1] = null;
          int carril = carrilLibre(segArray + 1); 
          this.posOcup[segArray + 1][carril] = id;
          Pos res = new Pos(segArray + 2, carril + 1);
          this.segOcup.put(id, res);
          this.tiempoRestante.put(id, tks);
          this.cond.signal(); //liberamos el bloqueo sobre el coche que ya ha podido avanzar
          return res;
        }
        this.cond.await(); //si no encontramos hueco, bloqueamos el coche
      }
    } finally {
      this.mutex.leave();
    }
  }

  // Bloquea al coche hasta que su temporizador llegue a 0
  public void circulando(String id) {
    this.mutex.enter();
    try {
      while (this.tiempoRestante.get(id) > 0) {
        this.cond.await(); //si tks aún no es 0, bloqueamos el coche en esa Pos
      }

      this.cond.signal(); //cuando ya sea 0, liberamos al coche

    } finally {
      this.mutex.leave();
    }
  }

  // Elimina el coche de la carretera, liberando su segmento
  public void salir(String id) {
    this.mutex.enter();

    try {
      Pos res = this.segOcup.get(id);
      if (res == null) return; //si no aparece en el mapa de posiciones ocupadas entonces hemos acabado
      //si continua la ejecución del método entonces liberamos su posición y eliminamos al coche de todos los atributos
      this.posOcup[res.getSegmento() - 1][res.getCarril() - 1] = null;
      this.segOcup.remove(id);
      this.tiempoRestante.remove(id);
      this.cond.signal();

    } finally {
      this.mutex.leave();
    }
  }

  //Avanza un tick: decrementa temporizadores y despierta en caso necesario
  public void tick() {
    this.mutex.enter();
    try {
      boolean algunoTermina = false; //buscamos indicar si se libera alguna Pos
      for (String coche : this.tiempoRestante.keys()) {
        //recorremos y actualizamos todos los tiempos de los coches que ocupan alguna Pos en nuestra carretera
        int t = this.tiempoRestante.get(coche) - 1; 
        if (t < 0) t = 0; //si el tiempo de algún coche pasa a ser negativo lo ponemos a 0
        this.tiempoRestante.put(coche, t); //añadimos el nuevo tiempo asociado al coche actual
        if (t == 0) algunoTermina = true;  //si el tiempo de algún coche es 0, esa Pos queda liberada
      }
      if (algunoTermina) this.cond.signal();
    } finally {
      this.mutex.leave();
    }
  }

///////// Métodos auxiliares privados
  //buscamos si hay algún hueco libre en el segmento(empezando en 0) pasado como parámetro
  private boolean hayHueco(int segArray) {
    for (int carril = 0; carril < this.nCarriles; carril++)
      if (this.posOcup[segArray][carril] == null) return true;
    return false;
  }

  //buscamos qué carril hay libre en ese segmento(empezando en 0)
  private int carrilLibre(int segArray) {
    for (int carril = 0; carril < this.nCarriles; carril++)
      if (this.posOcup[segArray][carril] == null) return carril;
    throw new IllegalStateException("No hay carril libre en el segmento " + segArray);
  }
}
