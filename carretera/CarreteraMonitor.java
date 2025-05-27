// Nunca cambia la declaracion del package!
package cc.carretera;

import es.upm.babel.cclib.Monitor;
import es.upm.babel.cclib.Monitor.Cond;
import java.util.Map;
import java.util.HashMap;
import java.util.Queue;
import java.util.LinkedList;

/**
 * Implementación del recurso compartido Carretera con Monitores
 * Gestiona el acceso de coches a una carretera segmentada en carriles,
 * permitiendo entrada, avance, circulación interna y salida de coches.
 */
public class CarreteraMonitor implements Carretera {
	private final int nSegmentos; //número total de segmentos (parámetro constructor)
	private final int nCarriles; //número de Carriles (parámetro constructor) 

	private static final Monitor mutex = new Monitor(); //monitor para garantizar la exclusión mutua

	private Map<String, EstadoCoche> coches; //mapa que indica en qué estado está cada coche
	private enum TipoPeticion {ENTRAR, AVANZAR, CIRCULANDO } //indica el tipo de petición de la que proviene la solicitud
	private Map<String, PeticionSignal> peticiones; //mapa para evitar consultas de complejudad O(n) al buscar peticiones 
	//de cada hilo, empleamos esta estructura con búsquedas O(cte.)
	private Queue<PeticionSignal> colaPeticiones; //Cola para establecer un orden de prioridad al desbloquear señales(FIFO)

	//Constructor que inicializa los atributos principales del recurso.
	public CarreteraMonitor(int segmentos, int carriles) {
		this.nSegmentos = segmentos;
		this.nCarriles = carriles;
		this.coches = new HashMap<>();
		this.peticiones = new HashMap<>();
		this.colaPeticiones = new LinkedList<>();
	}

	/* 
	 * Los coches utilizan entrar para entrar en el primer segmento,
	 * quedan bloqueados si no hay carril libre o entran a la carretera
	 */
	@Override
	public Pos entrar(String id, int tks) {
		mutex.enter();
		try {
			if (carrilLibre(1) == -1) { //mientras que no haya carriles libres
				if (!this.peticiones.containsKey(id)) { //y no hay peticiones de este coche
					PeticionSignal p = new PeticionSignal(id, 1, 0, TipoPeticion.ENTRAR);
					this.peticiones.put(id, p);
					this.colaPeticiones.add(p);
					//añadimos una nueva petición de tipo entrar a la cola
				}
				this.peticiones.get(id).getCond().await(); //y mantenemos bloqueado el coche
			}
			//Cuando se pueda atendemos (se ha liberado un carril) nuestra petición y la eliminamos de los registros
			PeticionSignal p = this.peticiones.get(id); 
			this.colaPeticiones.remove(p);
			this.peticiones.remove(id);
			//Actualizamos(nueva posición y ticks) y retornamos la posición del coche y lo desbloqueamos
			Pos newPosCoche = new Pos(1, carrilLibre(1));
			this.coches.put(id, new EstadoCoche(newPosCoche, tks));
			desbloquear(); //Intentamos desbloquear otras peticiones pendientes
			return newPosCoche;

		} finally {
			mutex.leave();
		}
	}

	/* 
	 * Los coches utilizan para avanzar al siguiente segmento,
	 * quedan bloqueados si no hay carril libre en el siguiente segmento o aún les quedan ticks.
	 */
	@Override
	public Pos avanzar(String id, int tks) {
		mutex.enter();
		try {
			EstadoCoche estado = this.coches.get(id);
			int nextSeg = estado.getPos().getSegmento() + 1;
			//mientras que no haya un carril libre en el próximo seg o aún le queden ticks al coche en el carril actual
			if (carrilLibre(nextSeg) == -1 || estado.getNTicksRest() > 0) {
				if (!this.peticiones.containsKey(id)) { //si no hay ninguna petición de este coche
					PeticionSignal p = new PeticionSignal(id, nextSeg, estado.getNTicksRest(), TipoPeticion.AVANZAR);
					this.peticiones.put(id, p);
					this.colaPeticiones.add(p);
					//añadimos la petición del tipo avanzar
				} //e.o.c. bloqueamos el coche y actualizamos el estado 
				this.peticiones.get(id).getCond().await();
				estado = this.coches.get(id);
			}
			//Cuando se pueda atendemos nuestra petición y la eliminamos de los registros
			PeticionSignal p = this.peticiones.get(id);
			this.colaPeticiones.remove(p);
			this.peticiones.remove(id);
			//Actualizamos (posición y ticks) y retornamos la posición del coche y lo desbloqueamos
			Pos newPosCoche = new Pos(nextSeg, carrilLibre(nextSeg));
			this.coches.put(id, new EstadoCoche(newPosCoche, tks));
			desbloquear();
			return newPosCoche;

		} finally {
			mutex.leave();
		}
	}


	/*
	 * Para avanzar dentro de un segmento, los coches utilizan circulando
	 * quedan bloqueados, simulando el avance en el segmento en el que están
	 * hasta que alcanzan el final del segmento
	 */
	@Override
	public void circulando(String id) {
		mutex.enter();
		try {
			EstadoCoche estado = this.coches.get(id);
			//mientras que aún le queden ticks al coche para permanecer en ese carril
			if (estado.getNTicksRest() > 0) { 
				if (!this.peticiones.containsKey(id)) { //si no hay peticiones para ese coche
					//añadimos la petición del tipo circulando
					PeticionSignal p = new PeticionSignal(id, estado.getPos().getSegmento(), estado.getNTicksRest(), TipoPeticion.CIRCULANDO);
					this.peticiones.put(id, p);
					this.colaPeticiones.add(p);
				} 
				this.peticiones.get(id).getCond().await(); //y bloqueamos el coche
			} //una vez atendemos la nuestra petición la eliminamos y desbloqueamos el coche
			PeticionSignal p = peticiones.get(id);
			this.peticiones.remove(id);
			this.colaPeticiones.remove(p);
			desbloquear();
		} finally {
			mutex.leave();
		}
	}

	/*
	 * Los coches utilizan salir para abandonar el último segmento.
	 * Nunca quedan bloqueados
	 */
	@Override
	public void salir(String id) {
		mutex.enter();
		try {
			this.coches.remove(id);
			desbloquear(); //Puede liberar carriles para otras peticiones
		} finally {
			mutex.leave();
		}
	}

	/*
	 * El proceso del reloj simplemente genera los ticks que marcan la velocidad del simulador.
	 * Cada ejecución de tick provoca que los coches avancen en sus segmentos 
	 * (les quedará un tick menos para llegar al final)
	 */
	@Override
	public void tick() {
		mutex.enter();
		try {
			for (String id : this.coches.keySet()) { //para cada coche, decrementamos sus ticks
				EstadoCoche estado = this.coches.get(id);
				if (estado.getNTicksRest() > 0) {
					estado.nTicksRest--;
					PeticionSignal p = this.peticiones.get(id);
					if (p != null)
						p.actTicks(); //actualizamos los ticks en la petición
				}
			}
			desbloquear(); //Desbloquea si alguna condición se ha cumplido
		} finally {
			mutex.leave();
		}
	}

	///////////////////// Métodos auxiliares

	//Desbloqueo FIFO: solo una petición por ciclo, en orden de llegada
	private void desbloquear() {
		int numPs = this.colaPeticiones.size();
		boolean atendida = false; //Marcamos si se ha desbloqueado alguna petición
		for (int i = 0 ; i < numPs && !atendida ; i++) {
			PeticionSignal p = this.colaPeticiones.poll(); //Extraemos la petición más antigua
			switch (p.getTipo()) {
			case ENTRAR:
				if (carrilLibre(1) != -1) //Se puede entrar si hay carril libre en el primer segmento
					atendida = true;
				break;
			case AVANZAR:
				if (carrilLibre(p.getSeg()) != -1 && p.getNTicksRest() == 0)//Solo avanza si hay carril libre en el siguiente segmento y no le quedan ticks
					atendida = true;
				break;
			default:
				if (p.getNTicksRest() == 0) //Se desbloquea si ya no quedan ticks de espera
					atendida = true;
				break;
			}
			if (atendida) { //Eliminamos la petición del mapa y señalizamos el hilo correspondiente
				this.peticiones.remove(p.getId());
				p.getCond().signal();
			} else {
				this.colaPeticiones.add(p); //Volvemos a meterlo en la cola para volver a intentarlo más tarde
			}
		}
	}

	/*
	 * Devuelve el número de carril libre ó -1 si no hay carriles libres
	 */
	private int carrilLibre(int seg) {
		boolean[] ocup = new boolean[this.nCarriles]; //vector para marcar carriles ocupados
		// Para cada coche en el mapa
		for (String id : this.coches.keySet()) {
			EstadoCoche estado = this.coches.get(id);
			// Si el coche está en segmento, marcamos su carril como ocupado
			if (estado.getPos().getSegmento() == seg)
				ocup[estado.getPos().getCarril() - 1] = true;
		}
		// Encontramos el primer carril libre
		for (int i = 0; i < this.nCarriles; i++) {
			if (!ocup[i])
				return i + 1; //devolvemos el índice del carril libre
		}
		return -1; //No hay carriles libres
	}

	//////////////////////////// CLASES INTERNAS

	// Clase interna para representar el estado de cada coche
	private static class EstadoCoche {
		private Pos posicion;
		private int nTicksRest;

		EstadoCoche(Pos posicion, int nTicksRest) {
			this.posicion = posicion;
			this.nTicksRest = nTicksRest;
		}
		Pos getPos() {
			return posicion;
		}
		int getNTicksRest() {
			return nTicksRest;
		}
	}

	// Clase interna para guardar peticiones de desbloquear
	private static class PeticionSignal {
		private String id;
		private int nTicksRest;
		private int seg;
		private Monitor.Cond cond;
		private TipoPeticion tipo;

		PeticionSignal(String id, int segmento, int nTicksRest, TipoPeticion tipo) {
			this.id = id;
			this.seg = segmento;
			this.nTicksRest = nTicksRest;
			this.cond = mutex.newCond();
			this.tipo = tipo;
		}
		public String getId() {
			return id;
		}
		public int getNTicksRest() {
			return nTicksRest;
		}
		public int getSeg() {
			return seg;
		}
		public Monitor.Cond getCond() {
			return cond;
		}
		public TipoPeticion getTipo() {
			return tipo;
		}
		public void actTicks() {
			nTicksRest--;
		}
	}
}
