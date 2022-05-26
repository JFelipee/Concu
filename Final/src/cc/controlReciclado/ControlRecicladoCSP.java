package cc.controlReciclado;
import org.jcsp.lang.*;

import es.upm.aedlib.fifo.FIFOList;


public class ControlRecicladoCSP implements ControlReciclado, CSProcess {
	private enum Estado { LISTO, SUSTITUIBLE, SUSTITUYENDO }

	private final int MAX_P_CONTENEDOR;
	private final int MAX_P_GRUA;


	//Canal por cada operacion
	private Any2OneChannel cNotificarPeso;
	private Any2OneChannel cIncrementarPeso;
	private Any2OneChannel cNotificarSoltar;


	
	//Cola cuando dewpende de parametros de entrada
	private FIFOList<PetNotificarPeso> colaNotificarP;
	private FIFOList<PetNotificarPeso> colaIncrementarP;
	private FIFOList<PetPreparaSusti> colaPrepararSustitucion;
	
	static class PetNotificarPeso
	{
		int peso; 
		One2OneChannel canal; 
		
		PetNotificarPeso(int peso, One2OneChannel canal)
		{
			this.peso= peso;
			this.canal= canal;
		}
	}
	static class PetPreparaSusti
	{
		One2OneChannel canal; 
		
		PetPreparaSusti(One2OneChannel canal)
		{
			this.canal= canal;
		}
	}
	
	public ControlRecicladoCSP (int max_p_contenedor, int max_p_grua) 
	{
		MAX_P_CONTENEDOR = max_p_contenedor;
		MAX_P_GRUA = max_p_grua;
		
		//inicializacion de canales
		cNotificarPeso= Channel.any2one();
		cIncrementarPeso= Channel.any2one();
		cNotificarSoltar= Channel.any2one();
		cPrepararSustitucion= Channel.any2one();
		cNotificarSustitucion= Channel.any2one();
		
		//inicilizacion de cola
		colaPrepararSustitucion= new FIFOList<PetPreparaSusti>();
		
		new ProcessManager(this).start();
	}

	public void notificarPeso(int p) throws IllegalArgumentException
	{
		//comprobacion de cpres
		if(p<=0 || p>MAX_P_GRUA) 
		{
			throw new IllegalArgumentException();
		}
		
		//creo canal de respuesta
		One2OneChannel canalR= Channel.one2one();
		
		//creo la peticion 
		PetNotificarPeso peticion= new PetNotificarPeso(p, canalR);
		
		//comunica con el servidor
		cNotificarPeso.out().write(peticion);
		
		// read()  aunque no devuelva nada
		peticion.canal.in().read();
		
	}

	public void incrementarPeso(int p) throws IllegalArgumentException
	{
;
		
		// comunica con el servidor 
		cIncrementarPeso.out().write(peticion);
		
		// read()  aunque no devuelva nada
		peticion.canal.in().read();
		
	}

	public void notificarSoltar() 
	{
		
		// cPre: cierta
		cNotificarSoltar.out().write(null);
		cNotificarSoltar.in().read();
	}

	public void prepararSustitucion()
	{
		
		//comunicacion del servidor
		cPrepararSustitucion.out().write(peticion);
		
		// read()  aunque no devuelva nada
		peticion.canal.in().read();
	}

	;
		cNotificarSustitucion.in().read();
	}

	public void run()
	{
		// Declaracion del estado del recurso
		int peso= 0; 
		Estado estado= Estado.LISTO;
		int accediendo= 0;
		
		// Nombres simbolicos para los indices de servicios
		final int NOTIFICARPESO=0;
		final int INCREMENTARPESO=1;
		final int NOTIFICARSOLTAR=2;
		final int PREPARARSUSTITUCION=3;
		final int NOTIFICARSUSTITUCION=4;
		
		//entradas de select
		Guard[] inputs=
		{
				cNotificarPeso.in(),
				cIncrementarPeso.in(),
				cNotificarSoltar.in(),

		int choice=0;
		
		while(true)
		{
			
			try {
				choice= servicios.fairSelect();
			} catch (ProcessInterruptedException e) {}
			
			switch(choice) 
			{
				case NOTIFICARPESO:
					PetNotificarPeso peticionPeso= (PetNotificarPeso)cNotificarPeso.in().read();
					colaNotificarP.enqueue(peticionPeso);
					break;
					
				case INCREMENTARPESO:
					PetNotificarPeso peticionIncremPeso= (PetNotificarPeso)cIncrementarPeso.in().read();
					colaIncrementarP.enqueue(peticionIncremPeso);
					break;
					
				case NOTIFICARSOLTAR:
					cNotificarSoltar.in().read();
					//post
					accediendo= accediendo-1;
					cNotificarSoltar.out().write(true);
					break;
					
				case PREPARARSUSTITUCION:
					PetPreparaSusti peticionPrepararSus= (PetPreparaSusti)cPrepararSustitucion.in().read();
					colaPrepararSustitucion.enqueue(peticionPrepararSus);
					break;
	
				case NOTIFICARSUSTITUCION:
					cNotificarSustitucion.in().read();
					
					// Post
					peso= 0;
					estado= Estado.LISTO;
					accediendo= 0;
					
					cNotificarSustitucion.out().write(true);
					break;	
			}//bucle switch
			
			
			/**
			 * Codigo de desbloqueo, para los procesos
			 * con peticiones donde su CPRE es cierta
			 */
			boolean cambiado= true;
			while(cambiado)
			{
				cambiado= false;
				boolean aux=false;
				int sizeColaPet= colaNotificarP.size();
				
				//bucle desbloqueo notificarPEso()
				for(int i=0; i< sizeColaPet && !aux; i++) 
				{
					PetNotificarPeso pesoNotificar= colaNotificarP.dequeue();
					// comprobacion de la cpre
					if(estado != Estado.SUSTITUYENDO) 
					{
						// seccion critica
						//POST, se modifica la variable estado  en cualquier caso
						if((peso+pesoNotificar.peso)> MAX_P_CONTENEDOR) 
						{
							estado= Estado.SUSTITUIBLE;
						}
						else estado= Estado.LISTO;
						//salgo del bucle
						aux=true;
						cambiado=true;
						pesoNotificar.canal.out().write(null);
					}//cpre
					
					//sino se cumple la cpre, vuelvo a encolar
					else colaNotificarP.enqueue(pesoNotificar);
				}
				
			
			}//bucle desbloqueo
		}//bucle while
		
	}
	
	
	
}
