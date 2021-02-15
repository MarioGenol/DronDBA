/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package DronIndra;

import static ACLMessageTools.ACLMessageTools.getDetailsLARVA;
import IntegratedAgent.IntegratedAgent;
import Map2D.Map2DGrayscale;
import YellowPages.YellowPages;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.io.File;
import java.io.IOException;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Clase de la que heredan los agentes rescuer y seeker
 * @author Equipo de desarrollo completo
 */
public class AgenteSupremo extends IntegratedAgent {
    static int contador = 0;
    protected Map2DGrayscale map = new Map2DGrayscale();
    public int compass = 90;
    public int posicion_x, posicion_y, posicion_z;
    public int pos_x_objetivo, pos_y_objetivo;
    protected Queue<String> cola_de_movimientos = new LinkedList();
    protected YellowPages myYP;
    protected String myStatus, myService, myWorldManager, myWorld, myConvID;
    protected boolean myError;
    protected ACLMessage in, out;
    String quienSoy, queSoy;
    String myServiceProvider;
    int posx_inicio, posy_inicio;
    JsonObject listado_precios = new JsonObject();
    boolean tiendasComprobadas;
    ArrayList<String> shops;
    Vector<String> referencias_compras = new Vector<String>();//vector de tickets de sensores comprados
    Vector<String> monedero = new Vector<String>();//vector de coins
    Vector<String> capabilities = new Vector<String>();
    int altura_mapa, anchura_mapa;
    int altura;
    int energia = 0;
    int cuadrante_seeker=0;

    /**
     * Identificación del agente correspondiente con Sphinx 
     * @author Equipo de desarrollo completo
     */
    @Override
    public void setup() {
        // Hardcoded the only known agent: Sphinx
        _identitymanager = "Sphinx";
        super.setup();

        Info("Booting");

        // Description of my group
        myService = "Analytics group Indra";

        // The world I am going to open
        myWorld = "";
        //Choose the proper agent to setup
        if (this.whoAmI().contains("rescuer1")) {
            quienSoy = "rescuer1";

            queSoy = "RESCUER";

        }
        if (this.whoAmI().contains("rescuer2")) {
            quienSoy = "rescuer2";

            queSoy = "RESCUER";
        }
        if (this.whoAmI().contains("rescuer3")) {
            quienSoy = "rescuer3";

            queSoy = "RESCUER";
        }
        if (this.whoAmI().contains("seeker2")) {
            quienSoy = "seeker2";

            queSoy = "SEEKER";
        }

        out = new ACLMessage();
        out.addReceiver(new AID(_identitymanager, AID.ISLOCALNAME));
        out.setSender(this.getAID());
        out.setProtocol("ANALYTICS");
        out.setContent("");
        out.setEncoding(_myCardID.getCardID());
        out.setPerformative(ACLMessage.SUBSCRIBE);

        this.send(out);
        in = this.blockingReceive();

        if (in.getPerformative() != ACLMessage.INFORM) {
            Error(ACLMessage.getPerformative(in.getPerformative()) + " Could not"
                    + "confirm the registration in LARVA due to " + getDetailsLARVA(in));
            abortSession();
        }

        Info("Chekin confirmed in the platform");
        out = in.createReply();
        out.setContent("");
        out.setEncoding("");
        out.setPerformative(ACLMessage.QUERY_REF);

        this.send(out);
        in = this.blockingReceive();

        if (in.getPerformative() != ACLMessage.INFORM) {
            Error(ACLMessage.getPerformative(in.getPerformative()) + " Could not"
                    + "confirm the registration in LARVA due to " + getDetailsLARVA(in));
            abortSession();
        }

        myYP = new YellowPages();
        myYP.updateYellowPages(in);
        System.out.println("\n" + myYP.prettyPrint());
        // First state of the agent
        myStatus = "ESPERANDO";

        // To detect possible errors
        myError = false;

        _exitRequested = false;
    }

    /**
     * Registrarse en el World Manager
     * @author Equipo de desarrollo completo
     * @return espera bloquante
     */
    protected ACLMessage sessionRegister() {
        out = new ACLMessage();
        out.addReceiver(new AID(myWorldManager, AID.ISLOCALNAME));
        out.setSender(this.getAID());
        out.setProtocol("REGULAR");
        //JsonObject envio = new JsonObject();
        //envio.add("type", "RESCUER");
        out.setContent(new JsonObject().add("type", queSoy).toString());
        out.setConversationId(myConvID);
        out.setPerformative(ACLMessage.SUBSCRIBE);

        this.send(out);
        return this.blockingReceive();
    }

    /**
     * Consulta de las YP
     * @author lcv
     * @param im identity manager
     * @return espera bloquante
     */
    protected ACLMessage queryYellowPages(String im) {
        YellowPages res = null;

        out = new ACLMessage();
        out.setSender(getAID());
        out.addReceiver(new AID(im, AID.ISLOCALNAME));
        out.setProtocol("ANALYTICS");
        out.setContent("");
        out.setPerformative(ACLMessage.QUERY_REF);
        this.send(out);
        return blockingReceive();
    }
    /**
     * Funcion para consultar los precios de una tienda
     * @author Jose, Alexis
     * @param tienda tienda a consultar
     * @return espera bloqueante
     */
    protected ACLMessage pedirPreciosTienda(String tienda) {
        out = new ACLMessage();
        out.addReceiver(new AID(tienda, AID.ISLOCALNAME));
        out.setSender(this.getAID());
        out.setProtocol("REGULAR");
        out.setContent("{}");
        out.createReply();
        out.setConversationId(myConvID);
        out.setPerformative(ACLMessage.QUERY_REF);

        this.send(out);
        return this.blockingReceive();
    }
    /**
     * Compra del sensor correspondiente en la tienda deseada al precio indicado
     * @author Jose, Alexis
     * @param tienda tienda en la que realizamos la compra
     * @param referencia referencia del producto
     * @param precio precio del producto
     */
    void comprarSensor(String tienda, String referencia, int precio) {
        if (monedero.size() >= precio) {// si hay dinero para comprar
            JsonArray pago = new JsonArray();

            out = new ACLMessage();
            out.addReceiver(new AID(tienda, AID.ISLOCALNAME));
            out.setSender(this.getAID());
            JsonObject envio = new JsonObject();
            System.out.println("REFENCIA A COMPRAR " + referencia + "en tienda " + tienda + "/n");
            envio.add("operation", "buy");
            envio.add("reference", referencia);

            for (int i = 0; i < precio; i++) {
                //System.out.println("COIN A METER EN PAGO -> "+monedero.get(i)+"/n");
                pago.add(monedero.remove(monedero.size() - 1));
            }
            envio.add("payment", pago);
            System.out.println("CONTENIDO DEL PAGO: " + pago.toString() + "/n");
            String enviar = envio.toString();
            
            //operation buy  referencia a comprar   pago-> array de coins
            out.setContent(enviar);
            out.createReply();
            out.setConversationId(myConvID);
            out.setPerformative(ACLMessage.REQUEST);
            this.send(out);
            in = this.blockingReceive();
            
            //Tratamiento de la respuesta del servidor
            switch (in.getPerformative()) {
                case ACLMessage.INFORM:
                    //session = in.getConversationId();
                    Info("Contenido inform elegir y comprar sensores: " + in.getContent());
                    String respuestaCompra = in.getContent();

                    //Interpretar la respuesta
                    JsonObject respuesta = Json.parse(respuestaCompra).asObject();
                    String reference = "";
                    reference = respuesta.get("reference").asString();//obtener la referencia
                    referencias_compras.add(reference);//guardarla en el vector de referencias de las compras realizadas
                    
                    break;
                case ACLMessage.FAILURE:
                    Info("Contenido failure elegir y comprar sensores: " + in.getContent());

                    break;
                default:
                    Error(ACLMessage.getPerformative(in.getPerformative()) + " Could not open a session with "
                            + myServiceProvider + " due to " + getDetailsLARVA(in));
                    abortSession();
            }
        }

    }
    /**
     * Registrar nuestros agentes en el WM en la posición deseada
     * @author Equipo de desarrollo completo
     * @return Espera bloqueante
     */
    protected ACLMessage login() {
        //Tratamos con el agente correspondiente
        switch (quienSoy) {
            case "rescuer1":
                posx_inicio = (int) (anchura_mapa * 0.5);
                posy_inicio = (int) (altura_mapa * 0.3);

                break;
            case "rescuer2":
                posx_inicio = (int) (anchura_mapa * 0.3);
                posy_inicio = (int) (altura_mapa * 0.7);
                break;
            case "rescuer3":
                posx_inicio = (int) (anchura_mapa * 0.7);
                posy_inicio = (int) (altura_mapa * 0.7);
                break;
            case "seeker2":
                //Tratamiento de mapas grandes para posteriores movimientos
                if(anchura_mapa==300){
                    posx_inicio = (int) (anchura_mapa * 0.2);
                    posy_inicio = (int) (altura_mapa * 0.2);
                }else{
                    posx_inicio = (int) (anchura_mapa * 0.5);
                    posy_inicio = (int) (altura_mapa * 0.5);
                }
                break;

        }
       
        posicion_x = posx_inicio;
        posicion_y = posy_inicio;
        posicion_z = map.getLevel(posicion_x, posicion_y);
        posicion_z -= (posicion_z % 5);
        
        out = new ACLMessage();
        out.addReceiver(new AID(myWorldManager, AID.ISLOCALNAME));
        out.setSender(this.getAID());
        out.setProtocol("REGULAR");
        JsonArray lista_sensores = new JsonArray();
        JsonObject envio = new JsonObject();
        envio.add("operation", "login");
        
        //Sensores disponibles para nuestro agente
        for (int i = 0; i < referencias_compras.size(); i++) {
            Info("Referencia comprada " + i + " nombre: " + referencias_compras.get(i) + "/n");
            lista_sensores.add(referencias_compras.get(i));
        }
        //aportar todos los tickets
        envio.add("attach", lista_sensores);
        //dar las coordenadas
        envio.add("posx", posx_inicio);
        envio.add("posy", posy_inicio);
        String enviar = envio.toString();
        out.setContent(enviar);
        out.createReply();
        out.setConversationId(myConvID);
        out.setPerformative(ACLMessage.REQUEST);

        this.send(out);
        return this.blockingReceive();

    }
    /**
     * Informa al coach de que es el último para cerrar sesión
     * @author Jose, Alexis
     */
    protected void informarCoachUltimo() {
        out = new ACLMessage();
        out.addReceiver(new AID("coach1", AID.ISLOCALNAME));
        out.setSender(this.getAID());
        out.setProtocol("REGULAR");
        out.setContent("");
        out.createReply();
        out.setConversationId(myConvID);
        out.setPerformative(ACLMessage.CONFIRM);

        this.send(out);

    }
    /**
     * Le comunica al agente del servidor Sphinx que nos vamos
     * @author Jose, Alexis
     * @return espera bloqueante
     */
    protected ACLMessage cerrarSesionConSphinx() {
        out = new ACLMessage();
        out.addReceiver(new AID(_identitymanager, AID.ISLOCALNAME));
        out.setSender(this.getAID());
        out.setProtocol("ANALYTICS");
        out.setContent("");
        out.setConversationId(myConvID);
        out.setPerformative(ACLMessage.CANCEL);

        this.send(out);
        return this.blockingReceive();
    }
    /**
     * Una vez se ha creado el agente, espera nuevas ordenes y carga el mapa
     * @author Jose, Alexis
     */
    public void esperando() {
        in = this.blockingReceive();
        switch (in.getPerformative()) {
            case ACLMessage.QUERY_IF:

                //Info("Contenido QUERY_IF: " + in.getContent() + " sender: " + in.getSender());

                myStatus = "SESSION_REGISTER";
                JsonObject aux = Json.parse(in.getContent()).asObject();
                myConvID = in.getConversationId();
                myWorld = aux.get("mundo").toString();

                try {
                    map.loadMap("/home/alexismoga/NetBeansProjects/Practica_3_DBA/" + myWorld.replace("\"", "") + ".png");                    
                    altura_mapa = map.getHeight();
                    anchura_mapa = map.getWidth();
                } catch (IOException ex) {
                    Logger.getLogger(Seeker.class.getName()).log(Level.SEVERE, null, ex);
                }

                break;
            default:
                Error(ACLMessage.getPerformative(in.getPerformative()) + " Could not open a session with "
                        + myServiceProvider + " due to " + getDetailsLARVA(in));
                abortSession();
        }

    }
    /**
     * Función principal para dirigir los movimientos del agente.
     * Dada una posición este se orienta, calcula las alturas y guarda los moviemintos
     * @author Gonzalo, Mario
     * @param x poscicion x de la casilla
     * @param y poscicion y de la casilla
     */
    public void recorrido(int x, int y) {
        int diferencia_x = x - posicion_x;
        int diferencia_y = y - posicion_y;
        int direccion = 0;
        int distancia_al_suelo;
                
        while (!(diferencia_x == 0 && diferencia_y == 0)) {
            altura = map.getLevel(pos_x_objetivo, pos_y_objetivo);
            distancia_al_suelo = posicion_z - altura;
            if(energia < (distancia_al_suelo*4 + 50)){
                bajar(true);
            }
            // Calcular la dirección para ir al objetivo
            Info("Posicion rescuer WHILE x=" + posicion_x + ", y= " + posicion_y + ", z=" + posicion_z);
            direccion = calcularDireccion(diferencia_x, diferencia_y);

            // Rotar el dron
            if (direccion != compass) {
                orientarDron(direccion);
            }

            subir();
            cola_de_movimientos.add("moveF");
            energia -= 4;         

            posicion_x = pos_x_objetivo;
            posicion_y = pos_y_objetivo;
            diferencia_x = x - posicion_x;
            diferencia_y = y - posicion_y;
        }
        Info("Posicion rescuer fuera WHILE x=" + posicion_x + ", y= " + posicion_y + ", z=" + posicion_z);
        if (diferencia_x == 0 && diferencia_y == 0) {
            bajar(false);
        }
    }
    /**
     * Calcula la orientación de la casilla objetivo
     * @author Gonzalo, Mario
     * @param diferencia_x diferencia entre la casilla objetivo y la actual en x
     * @param diferencia_y diferencia entre la casilla objetivo y la actual en y
     * @return direccion Devuelve el angulo en el que se encuentra la casilla objetivo
     */
    public int calcularDireccion(int diferencia_x, int diferencia_y) {
        int direccion = 0;
        if (diferencia_x == 0) {
            if (diferencia_y > 0) {
                direccion = 180;            // SUR
                pos_x_objetivo = posicion_x;
                pos_y_objetivo = posicion_y + 1;
            }
            if (diferencia_y < 0) {
                direccion = 0;              // NORTE
                pos_x_objetivo = posicion_x;
                pos_y_objetivo = posicion_y - 1;
            }
        } else {
            if (diferencia_x > 0) {
                if (diferencia_y == 0) {
                    direccion = 90;         // ESTE
                    pos_x_objetivo = posicion_x + 1;
                    pos_y_objetivo = posicion_y;
                }
                if (diferencia_y > 0) {
                    direccion = 135;        // SURESTE
                    pos_x_objetivo = posicion_x + 1;
                    pos_y_objetivo = posicion_y + 1;
                }
                if (diferencia_y < 0) {
                    direccion = 45;         // NORESTE
                    pos_x_objetivo = posicion_x + 1;
                    pos_y_objetivo = posicion_y - 1;
                }
            } else {
                if (diferencia_x < 0) {
                    if (diferencia_y == 0) {
                        direccion = 270;    // OESTE
                        pos_x_objetivo = posicion_x - 1;
                        pos_y_objetivo = posicion_y;
                    }
                    if (diferencia_y > 0) {
                        direccion = 225;    // SUROESTE
                        pos_x_objetivo = posicion_x - 1;
                        pos_y_objetivo = posicion_y + 1;
                    }
                    if (diferencia_y < 0) {
                        direccion = 315;   // NOROESTE
                        pos_x_objetivo = posicion_x - 1;
                        pos_y_objetivo = posicion_y - 1;
                    }
                }
            }
        }

        return direccion;
    }
    /**
     * Establece los movimientos necesarios para que nuestro agente se oriente
     * hacia la dirección deseada
     * @author Gonzalo, Mario
     * @param nueva_direccion angulo de la nueva dirección
     */
    public void orientarDron(int nueva_direccion) {
        int inicio = compass;
        int fin = nueva_direccion;

        int diferencia = inicio - fin;
        int pasos = diferencia / 45;

        if (inicio != fin) {
            // Giro horario
            if (pasos < 0) {
                // Merece la pena hacer giro horario
                int pasos_valor_absoluto = Math.abs(pasos);
                if (pasos_valor_absoluto <= 4) {
                    for (int i = 0; i < pasos_valor_absoluto; i++) {
                        cola_de_movimientos.add("rotateR");
                        energia -= 4;
                    }
                } // No merece la pena hacer giro horario
                else {
                    pasos_valor_absoluto = 8 - pasos_valor_absoluto;
                    for (int i = 0; i < pasos_valor_absoluto; i++) {
                        cola_de_movimientos.add("rotateL");
                        energia -= 4;
                    }
                }
            } // Giro antihorario
            else {
                // Merece la pena hacer giro antihorario
                if (pasos <= 4) {
                    for (int i = 0; i < pasos; i++) {
                        cola_de_movimientos.add("rotateL");
                        energia -= 4;
                    }
                } // No merece la pena hacer giro antihorario
                else {
                    pasos = 8 - pasos;
                    for (int i = 0; i < pasos; i++) {
                        cola_de_movimientos.add("rotateR");
                        energia -= 4;
                    }
                }
            }
        }
        compass = nueva_direccion;
    }
    /**
     * Sube a nuestro agente hasta la altura de la casilla objetivo
     * @author Gonzalo, Mario
     */
    public void subir() {
        altura = map.getLevel(pos_x_objetivo, pos_y_objetivo);
        altura -= (altura % 5);
        Info("En subir, altura dron = " + posicion_z + " y la altura objetivo = " + altura);
        for (int i = posicion_z; i < altura; i += 5) {
            cola_de_movimientos.add("moveUP");
            energia -= 20;
            posicion_z += 5;
            Info("Sube 5 unidades: " + posicion_z);
        }
    }
    /**
     * Baja a nuestro agente hasta la altura de la casilla objetivo.Recarga si
        es necesario y realiza un rescate si se encuentra en un objetivo
     * @author Gonzalo, Mario
     * @param recargar describe si es necesario recargar
     */
    public void bajar(boolean recargar) {
        altura = map.getLevel(pos_x_objetivo, pos_y_objetivo);
        altura -= (altura % 5);

        Info("En bajar, altura dron = " + posicion_z + " y la altura objetivo = " + altura);
        for (int i = posicion_z; i > altura; i -= 5) {
            cola_de_movimientos.add("moveD");
            posicion_z -= 5;
            energia -= 20;
            Info("Baja 5 unidades: " + posicion_z);
        }
        
        cola_de_movimientos.add("touchD");
        energia -= 4;
        if(recargar){
            cola_de_movimientos.add("recharge");
            energia = 1000; 
        }else{
            cola_de_movimientos.add("rescue");
            energia -= 4;
        }
    }
    /**
     * Comprobamos las tiendas para obtener los precios de los artículos
     * @author Equipo de desarrollo completo
     * @param shops tiendas consultadas
     */
    public void actualizarListadoPreciosTiendas(ArrayList<String> shops) {
        listado_precios = new JsonObject();
        for (String shop : shops) {
            //averiguar el indice de las tiendas
            
            in = pedirPreciosTienda(shop);
            //Tratamos la respuesta
            switch (in.getPerformative()) {
                case ACLMessage.INFORM:
                    Info("Contenido inform comprobar tiendas: " + in.getContent());
                    JsonObject aux = new JsonObject();
                    aux = Json.parse(in.getContent()).asObject();//creamos un objeto json a partir del contenido de inbox
                    JsonValue result = aux.get("products");//obtenemos los productos

                    //Info("Contenido result : " + result.toString());

                    listado_precios.add(shop, result);

                    break;
                default:
                    Error(ACLMessage.getPerformative(in.getPerformative()) + " Could not open a session with " + myServiceProvider + " due to " + getDetailsLARVA(in));
                    abortSession();
            }

        }
    }
    /**
     * Realizamos una recarga de energía de la tienda más barata
     * @author Equipo de desarrollo completo
     * @param shops tienda donde se comprará la recarga
     */
    public void recargar(ArrayList<String> shops) {
        actualizarListadoPreciosTiendas(shops);
        JsonArray productos_tienda = new JsonArray();       
        String mejor_recarga = "";
        String tienda_recarga = "";
        
        int precio_recarga = 1000000;
        //recorrer tiendas 
        for (String tienda : shops) {
            productos_tienda = listado_precios.get(tienda).asArray();

            //Info("productos tienda: " + productos_tienda.toString());

            for (JsonValue p : productos_tienda) {

                //si contiene la cadena CHARGE y es la más barata hasta el momento
                if (p.asObject().get("reference").toString().contains("CHARGE") && p.asObject().get("price").asInt() < precio_recarga) {
                    tienda_recarga = tienda;
                    precio_recarga = p.asObject().get("price").asInt();
                    mejor_recarga = p.asObject().get("reference").asString();
                }
            }
        }
        //Realizamos el pago de la recarga
        String ticket_recarga = "";
        if (monedero.size() >= precio_recarga) {// si hay dinero para comprar
            JsonArray pago = new JsonArray();

            out = new ACLMessage();
            out.addReceiver(new AID(tienda_recarga, AID.ISLOCALNAME));
            out.setSender(this.getAID());
            JsonObject envio = new JsonObject();
            System.out.println("REFENCIA A COMPRAR " + mejor_recarga + "en tienda " + tienda_recarga + "/n");
            envio.add("operation", "buy");
            envio.add("reference", mejor_recarga);

            for (int i = 0; i < precio_recarga; i++) {
                //System.out.println("COIN A METER EN PAGO -> "+monedero.get(i)+"/n");
                pago.add(monedero.remove(monedero.size() - 1));
            }
            envio.add("payment", pago);
            System.out.println("CONTENIDO DEL PAGO: " + pago.toString() + "/n");
            String enviar = envio.toString();

            out.setContent(enviar);//operation buy  referencia a comprar   pago-> array de coins
            out.createReply();
            out.setConversationId(myConvID);
            out.setPerformative(ACLMessage.REQUEST);
            this.send(out);
            in = this.blockingReceive();

            switch (in.getPerformative()) {
                case ACLMessage.INFORM:
                    //session = in.getConversationId();
                    Info("Contenido inform elegir y comprar sensores: " + in.getContent());
                    String respuestaCompra = in.getContent();

                    //Interpretar la respuesta
                    JsonObject respuesta = Json.parse(respuestaCompra).asObject();
                    String reference = "";
                    reference = respuesta.get("reference").asString();//obtener la referencia
                    referencias_compras.add(reference);//guardarla en el vector de referencias de las compras realizadas
                    ticket_recarga = reference;
                    
                    break;
                case ACLMessage.FAILURE:
                    //session = in.getConversationId();
                    Info("Contenido failure elegir y comprar sensores: " + in.getContent());

                    break;
                default:
                    Error(ACLMessage.getPerformative(in.getPerformative()) + " Could not open a session with "
                            + myServiceProvider + " due to " + getDetailsLARVA(in));
                    abortSession();
            }
            
            //Usamos el tiket de recarga 
            out = new ACLMessage();
            out.addReceiver(new AID(myWorldManager, AID.ISLOCALNAME));
            out.setSender(this.getAID());
            out.setProtocol("REGULAR");
            out.createReply();
            out.setConversationId(myConvID);
            out.setPerformative(ACLMessage.REQUEST);
            envio = new JsonObject();

            envio.add("operation", "recharge");
            envio.add("recharge", ticket_recarga);

            enviar = envio.toString();
            out.setContent(enviar);
            send(out);
            Info("BLOQUEADO TRAS RECARGA");
            in = blockingReceive();
            //tratamos la respuesta
            switch (in.getPerformative()) {
                case ACLMessage.INFORM:

                    Info("Contenido inform  RECARGA RESCUER: " + in.getContent());
                    energia = 1000;

                    break;
                case ACLMessage.FAILURE:

                    Info("Contenido failure RECARGA RESCUER : " + in.getContent());
                    break;
                case ACLMessage.REFUSE:

                    Info("Contenido refuse RECARGA RESCUER: " + in.getContent());
                    break;
                default:
                    Error(ACLMessage.getPerformative(in.getPerformative()) + " Could not open a session with "
                            + myServiceProvider + " due to " + getDetailsLARVA(in));
                    abortSession();
            }
        }
    }
    /**
     * Mandamos al servidor la cola de movimientos que nuestro agente va a realizar
     * @author Equipo de desarrollo completo
     */
    public void ejecutarMovientos() {
        while (!cola_de_movimientos.isEmpty()) {
            out = new ACLMessage();
            out.addReceiver(new AID(myWorldManager, AID.ISLOCALNAME));
            out.setSender(this.getAID());
            out.setProtocol("REGULAR");
            JsonObject envio = new JsonObject();
            String accion = cola_de_movimientos.poll();
            envio.add("operation", accion);
            if(accion.equals("recharge")){
                recargar(shops);
            }else{
                String enviar = envio.toString();
                out.setContent(enviar);
                out.createReply();
                out.setConversationId(myConvID);
                out.setPerformative(ACLMessage.REQUEST);
                this.send(out);
                in = blockingReceive();
                switch (in.getPerformative()) {
                    case ACLMessage.INFORM:

                        Info("Contenido inform  EJECUTAR MOVIMIENTOS: " + in.getContent() + "realizamos" + accion);

                        break;
                    case ACLMessage.FAILURE:

                        Info("Contenido failure EJECUTAR MOVIMIENTOS: " + in.getContent());
                        break;
                    case ACLMessage.REFUSE:

                        Info("Contenido refuse EJECUTAR MOVIMIENTOS: " + in.getContent());
                        break;
                    default:
                        Error(ACLMessage.getPerformative(in.getPerformative()) + " Could not open a session with "
                                + myServiceProvider + " due to " + getDetailsLARVA(in));
                        abortSession();
                }
            }            
        }
        cola_de_movimientos.clear();
    }
}