package cc.controlReciclado;
import es.upm.babel.cclib.Monitor;

import es.upm.aedlib.fifo.*;

public final class ControlRecicladoMonitor implements ControlReciclado {
	private enum Estado { LISTO, SUSTITUIBLE, SUSTITUYENDO }


	private final int MAX_P_CONTENEDOR;
	private final int MAX_P_GRUA;

	// Guarda el peeso del contenedor
	private int peso;
	// Guarda la cantidad de gruas que estan soltando peso
	private int accediendo;
	// Guarda el estado del contenedor
	private Estado estado;

	// Declaracion de atributos para exclusion mutua
	// y sincronizacion por condicion
	private Monitor mutexReciclado;

	// no depende de parametros de entrada notificarPeso()(una condicion)
	private Monitor.Cond controlPeso;
	
	// no depende de parametros de entrada prepararSustitucion()(una condicion)
	private Monitor.Cond controlSustitucion;
	
	// Depende de parametros de entrada IncementrarPeso(peso)
	// usaremos indexacion de clientes
	private FIFOList<PetAplazadaPeso> petPeso;
	

	public ControlRecicladoMonitor (int max_p_contenedor, int max_p_grua)
	{
		MAX_P_CONTENEDOR = max_p_contenedor;
		MAX_P_GRUA = max_p_grua;

		//inicializaciones de variables del recurso
		peso=0;
		estado= Estado.LISTO;
		accediendo=0;
		
		//inicializo mutex y conditions
		mutexReciclado= new Monitor();
		controlPeso= mutexReciclado.newCond();
		controlSustitucion= mutexReciclado.newCond();
		
		//cola de peticion de bloqueo para gruas
		petPeso=  new FIFOList<PetAplazadaPeso>();
		
	}

	public void notificarPeso(int p) throws IllegalArgumentException
	{

		//entrada del mutex
		mutexReciclado.enter();
		
		// PRE:p > 0 ^ p <=MAX_P_GRUA
		if(p<=0 || p>MAX_P_GRUA)
		{
			// Libera el recurso antes de lanzar la excepcion
			mutexReciclado.leave();
			throw new IllegalArgumentException();
				
		}
		
		// (!CPRE) y bloqueo
		if(estado == Estado.SUSTITUYENDO )
		{
			controlPeso.await();
		}
		
		// secciin critica
		//POST, se modifica la variable estado  en cualquier caso
		if((peso+p)> MAX_P_CONTENEDOR) 
		{
			estado= Estado.SUSTITUIBLE;
		}
		else estado= Estado.LISTO;

		
		//codigo desbloqueo
		desbloqueoGeneral();
		
		// salida de mutex
		mutexReciclado.leave();

	}

	public void incrementarPeso(int p) throws IllegalArgumentException
	{
		// entrada del mutex
		mutexReciclado.enter();
		
		// PRE: p > 0 ^ p <= MAX_P_GRUA
		if(p<=0 || p>MAX_P_GRUA) 
		{
			// Libera el recurso antes de lanzar la excepcion
			mutexReciclado.leave();
			throw new IllegalArgumentException();
		}
		
		// (!CPRE) y creo una peticion aplazada para encolar en cola de peticiones
		// de bloqueo
		if((peso+p)> MAX_P_CONTENEDOR || estado==Estado.SUSTITUYENDO)
		{
			// Se crea la peticion y se encola
			Monitor.Cond monitorAplazado= mutexReciclado.newCond();
			PetAplazadaPeso peticioBloqueo= new PetAplazadaPeso(monitorAplazado, p);
			petPeso.enqueue(peticioBloqueo);
			monitorAplazado.await();
		}
		
		// seccion critica, incremento de peso y de gruas 
		peso=peso +p;
		accediendo++;
		
		//desbloqueo
		desbloqueoGeneral();
		
		// salida del mutex
		mutexReciclado.leave();
		
	}


	public void notificarSoltar() 
	{
		//entrada del mutex
		mutexReciclado.enter();
		
		//CPRE: cierto
		
		// Seccion critica
		accediendo--;
		
		// Desbloqueo
		desbloqueoGeneral();
		
		// Salida del mutex
		mutexReciclado.leave();
		
	}

	public void prepararSustitucion()
	{
		//entrada del mutex
		mutexReciclado.enter();
		
		//(!CPRE) y bloqueo
		if(estado!=Estado.SUSTITUIBLE || accediendo>0)
		{
			controlSustitucion.await();
		}
		
		// Seccion critica
		estado=Estado.SUSTITUYENDO;
		accediendo= 0;
		
		// Desbloqueo
		desbloqueoGeneral();
		
		// Salida del mutex
		mutexReciclado.leave();
		
	}

	public void notificarSustitucion() 
	{
		// Entrada del mutex
		mutexReciclado.enter();
		
		//CPRE:Cierto
		
		// POST
		peso=0;
		estado= Estado.LISTO;
		accediendo= 0;
		
		// Desbloqueo
		desbloqueoGeneral();
		
		// Salida del mutex
		mutexReciclado.leave();
	}
	
	//--Auxiliares--
	private void desbloqueoGeneral() 
	{
		// Desbloqueos para notificarPeso()
		// si se cumple la CPRE
		if(estado!= Estado.SUSTITUYENDO && 
				controlPeso.waiting()>0)
		{
			controlPeso.signal();
		}
		
		// Desbloqueo para PrepararSustitucion()
		// si se cumple la CPRE
		else if(estado==Estado.SUSTITUIBLE && accediendo==0 && 
				controlSustitucion.waiting()>0)
		{
			controlSustitucion.signal();
		}
	
			
	}
	

	
}
