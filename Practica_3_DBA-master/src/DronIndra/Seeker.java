package DronIndra;

import static ACLMessageTools.ACLMessageTools.getDetailsLARVA;
import static ACLMessageTools.ACLMessageTools.getJsonContentACLM;
import IntegratedAgent.IntegratedAgent;
import YellowPages.YellowPages;
import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Puesto que en java no existen las estructuras hemos decidido optar por hacer una pequeña clase para contemplar las posiciones de las casillas (x,y).
 * @author Equipo de desarrollo completo
 */
class Casilla {

    public int x;
    public int y;
};

/**
 * El agente Seeker extiende al AgenteSupremo y es el encargado de leer sensores y delegar a los rescuer que acudan a posiciones donde se encuentran los alemanes a rescatar. 
 * @author Equipo de desarrollo completo
 */
public class Seeker extends AgenteSupremo {

    int alemanes_rescatados = 0;
    float distance, angular;

    int posx_rescuer1 = 50;
    int posx_rescuer2 = 30;
    int posx_rescuer3 = 70;

    int posy_rescuer1 = 30;
    int posy_rescuer2 = 70;
    int posy_rescuer3 = 70;

    JsonObject details;

 
    /**
     * Este método se encarga de elegir los sensores DISTANCEDELUX y ANGULARDELUX más económicos del listado de tiendas disponibles en nuestra sesión. Posteriormente llama al método comprarSensor con los parámetros obtenidos.
     * @param shops
     * @author Equipo de desarrollo completo
     */
    void elegirYComprarSensores(ArrayList<String> shops) {// se eligen en base a los sensores previamente parseados 
        JsonArray productos_tienda = new JsonArray();
        //recorrer tiendas 
        String mejor_angular = "";
        String mejor_distance = "";
        String tienda_angular = "";
        String tienda_distance = "";
        int precio_angular = 1000000;
        int precio_distance = 100000;
        for (String tienda : shops) {
            productos_tienda = listado_precios.get(tienda).asArray();

            Info("productos tienda: " + productos_tienda.toString());


            for (JsonValue p : productos_tienda) {
               

                //si contiene la cadena thermal
                if (p.asObject().get("reference").toString().contains("DISTANCEDELUX") && p.asObject().get("price").asInt() < precio_distance) {
                    tienda_distance = tienda;
                    precio_distance = p.asObject().get("price").asInt();
                    mejor_distance = p.asObject().get("reference").asString();

                }
                //si contiene la cadena CHARGE
                if (p.asObject().get("reference").toString().contains("ANGULARDELUX") && p.asObject().get("price").asInt() < precio_angular) {
                    tienda_angular = tienda;
                    precio_angular = p.asObject().get("price").asInt();
                    mejor_angular = p.asObject().get("reference").asString();
                }
            }
        }


        comprarSensor(tienda_angular, mejor_angular, precio_angular);
        comprarSensor(tienda_distance, mejor_distance, precio_distance);
    }
/**
 * Este método contempla todos los estados por los que va a pasar el agente a lo largo de su ciclo de vida y se encarge de hacer las llamadas pertinentes a otros métodos y de interpretar las respuestas de otros agentes.
 * Los estados posibles son los siguientes: ESPERANDO - espera respuesta del coach y calcula las posiciones de su alineación de rescuers en base al mapa en el que se va a trabajar. Si todo va bien cambia el estado a SESSION_REGISTER
 *                                          SESSION_REGISTER- obtinene las yellowpages y las actualiza con la nueva información. Se registra en el worldManager guardando los coins en un monedero para ser usasdas posteriormente, y si todo va bien cambia el estado a MARKET_PLACEÇ
 *                                          MARKET_PLACE - Pide las yellowPages y las actualiza. Se piden las tiendas de la sesión y se procede a comprar los sensores necesarios.Si todo va bien se procede al estado de LOGIN.
 *                                          LOGIN - Logueamos al agente en el worldManager con los tickets de compra adquiridos y las posiciones iniciales del mismo en el mapa. Si todo va bien pasamos al estado CAZA.
 *                                          CAZA - Primeramente recargarmos, y posteriormente mientras el número de alemanes sea menor que 10 y el estado sea diferente que EXIT, leemos sensores y si obtenermos respuesta válida de los mismos delegamos al rescuer correspondiente que acuda al rescate de un alemán; en caso de que los sensores den datos erroneos desplazamos el dron a otra posición para volver a obtener nuevas lecturas más satisfactorias de los sensores. Si encontramos a todos los alemanes cambiamos al estado INFORMAR_SEEKER_CASA
 *                                          INFORMAR_SEEKER_CASA - una vez encontrados todos los alemanes, el seeker informa a cada seeker que vuelva a casa(a su posición de inicio). Si todo va bien pasamos al estado INFORMAR_COACH_ULTIMO.
 *                                          INFORMAR_COACH_ULTIMO - se informa al coach para que comience a cerrar las sesiones. Se pasa al estado EXIT.
 *                                          EXIT - se cierra sensión con Sphinx y se termina el ciclo de vida del agente.
 * @author Equipo de desarrollo completo
 */
    public void plainExecute() {
        // Basic iteration
        switch (myStatus.toUpperCase()) {
            case "ESPERANDO":
                esperando();
                posx_rescuer1 = (int) (anchura_mapa * 0.5);
                posy_rescuer1 = (int) (altura_mapa * 0.3);

                posx_rescuer2 = (int) (anchura_mapa * 0.3);
                posy_rescuer2 = (int) (altura_mapa * 0.7);

                posx_rescuer3 = (int) (anchura_mapa * 0.7);
                posy_rescuer3 = (int) (altura_mapa * 0.7);
                break;
            case "SESSION_REGISTER":
                in = queryYellowPages(_identitymanager); // As seen oon slides

                myError = in.getPerformative() != ACLMessage.INFORM;
                if (myError) {
                    Info("\t" + ACLMessage.getPerformative(in.getPerformative())
                            + " Query YellowPages failed due to " + getDetailsLARVA(in));
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                myYP = new YellowPages();
                myYP.updateYellowPages(in);
                // It might be the case that YP are right but we dont find an appropriate service for us, then leave
                if (myYP.queryProvidersofService(myService).isEmpty()) {
                    Info("\t" + "There is no agent providing the service " + myService);
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                // Choose one of the available service providers, i.e., the first one
                myWorldManager = myYP.queryProvidersofService(myService).iterator().next();

                Info(myYP.queryProvidersofService("shop").toString());
                Info(myYP.queryProvidersofService(myConvID).toString());

                Info("Iniciando sesión en el worldManager con rescuer. ");
                in = sessionRegister();

                switch (in.getPerformative()) {
                    case ACLMessage.INFORM:
                        Info("Contenido inform session register: " + in.getContent());

                        //PARSEAR MONEDERO Y GUARDARLO
                        JsonArray coins = new JsonArray();
                        coins = Json.parse(in.getContent()).asObject().get("coins").asArray();//creamos un objeto json a partir del contenido de inbox
                        for (JsonValue p : coins) {//llenar monedero
                            monedero.add(p.asString());
                        }
                        System.out.println("MONEDERO: " + monedero.toString() + "/n");
                        myStatus = "MARKET_PLACE";
                        break;
                    case ACLMessage.FAILURE:
                        Info("Contenido failure: " + in.getContent());

                        break;

                    case ACLMessage.REFUSE:
                        Info("Contenido refuse: " + in.getContent());
                        break;
                    default:
                        Error(ACLMessage.getPerformative(in.getPerformative()) + " Could not open a session with "
                                + myServiceProvider + " due to " + getDetailsLARVA(in));
                        abortSession();
                }

                break;
            case "MARKET_PLACE":
                Info("Ejecutando opciones de mercadeo");
                // First update Yellow Pages
                in = queryYellowPages(_identitymanager); // As seen oon slides
                switch (in.getPerformative()) {
                    case ACLMessage.INFORM:

                        //se actualizan las YellowPages
                        Info("Contenido inform market place actualizacion yellowpages: " + in.getContent());
                        myYP = new YellowPages();
                        myYP.updateYellowPages(in);

                        shops = new ArrayList(myYP.queryProvidersofService(myConvID));//obtener todas las tiendas

                        break;
                    default:
                        Error(ACLMessage.getPerformative(in.getPerformative()) + " Could not open a session with "
                                + myServiceProvider + " due to " + getDetailsLARVA(in));
                        abortSession();
                }

                String mejor_compass = "";
                int mejor_precio_compass = 1000000;
                for (String shop : shops) {
                    //averiguar el indice de las tiendas
                    in = pedirPreciosTienda(shop);

                    switch (in.getPerformative()) {
                        case ACLMessage.INFORM:

                            Info("Contenido inform comprobar tiendas: " + in.getContent());
                            JsonObject aux = new JsonObject();
                            aux = Json.parse(in.getContent()).asObject();//creamos un objeto json a partir del contenido de inbox
                            JsonValue result = aux.get("products");//obtenemos los productos

                            //JsonArray aux = Json.parse(in.getContent()).asArray();
                            Info("Contenido result : " + result.toString());

                            listado_precios.add(shop, result);

                            break;
                        default:
                            Error(ACLMessage.getPerformative(in.getPerformative()) + " Could not open a session with "
                                    + myServiceProvider + " due to " + getDetailsLARVA(in));
                            abortSession();
                    }

                }

                //elegir sensores de cada tienda
                elegirYComprarSensores(shops);
                myStatus = "LOGIN";
                break;

            case "LOGIN":
                Info("LOGIN SEEKER");
                in = login();
                switch (in.getPerformative()) {
                    case ACLMessage.INFORM:

                        Info("Contenido inform login SEEKER: " + in.getContent());
                        JsonArray capacidades = new JsonArray();
                        //creamos un objeto json a partir del contenido de inbox
                        capacidades = Json.parse(in.getContent()).asObject().get("capabilities").asArray();
                        for (JsonValue p : capacidades) {//llenar monedero
                            capabilities.add(p.asString());
                        }

                        break;
                    case ACLMessage.FAILURE:

                        Info("Contenido failure login SEEKER: " + in.getContent());
                        break;
                    case ACLMessage.REFUSE:

                        Info("Contenido refuse login seeker: " + in.getContent());
                        break;
                    default:
                        Error(ACLMessage.getPerformative(in.getPerformative())
                                + " Could not open a session with " + myServiceProvider + " due to " + getDetailsLARVA(in));
                        abortSession();
                }
                myStatus = "CAZA";
                break;
            case "CAZA":
                int anguCuadrante;
                int aux_x=0, aux_y=0;
                recargar(new ArrayList(myYP.queryProvidersofService("shop@" + myConvID)));

                while (alemanes_rescatados < 10 && !myStatus.equals("EXIT")) {
                    readSensors();
                    if(angular<-20000){
                        switch (cuadrante_seeker) {
                            case 0:
                                aux_x=200;
                                aux_y=100;
                                break;
                            case 1:
                                aux_x=200;                   
                                aux_y=200;
                                break;
                            case 2:
                                aux_x=100;
                                aux_y=200;
                                break;
                            default:
                                break;
                        }
                        recargar(new ArrayList(myYP.queryProvidersofService("shop@" + myConvID)));
                        recorrido(aux_x, aux_y);
                        ejecutarMovientos();
                        cuadrante_seeker++;
                    }else{
                        Casilla c = calcularPosicion();

                        in = mandarRescuer(c.x, c.y);

                        switch (in.getPerformative()) {
                            case ACLMessage.INFORM:
                                    alemanes_rescatados++;
                                    Info("Alemanes rescatados = " + alemanes_rescatados);
                                //}
                                break;
                            case ACLMessage.FAILURE:

                                Info("Contenido failure caza seeker: " + in.getContent());
                                myStatus = "EXIT";
                                break;
                            case ACLMessage.REFUSE:

                                Info("Contenido refuse caza seeker: " + in.getContent());
                                myStatus = "EXIT";
                                break;
                            default:
                                Error(ACLMessage.getPerformative(in.getPerformative()) + " Could not open a session with " + myServiceProvider + " due to " + getDetailsLARVA(in));
                                abortSession();
                        }
                    }
                }
                if (alemanes_rescatados == 10) {
                    myStatus = "INFORMAR_SEEKER_CASA";
                }
                break;
            case "INFORMAR_SEEKER_CASA":
                Info("Informar que nos vamos a casa RESCUER 1");
                informarRescuerCasa("rescuer1");
                try {
                   //Ponemos a "Dormir" el programa durante los ms que queremos
                   Thread.sleep(10000);
                } catch (Exception e) {
                   System.out.println(e);
                }
                informarRescuerCasa("rescuer2");
                try {
                   //Ponemos a "Dormir" el programa durante los ms que queremos
                   Thread.sleep(10000);
                } catch (Exception e) {
                   System.out.println(e);
                }
                informarRescuerCasa("rescuer3");
                try {
                   //Ponemos a "Dormir" el programa durante los ms que queremos
                   Thread.sleep(10000);
                } catch (Exception e) {
                   System.out.println(e);
                }
                myStatus = "INFORMAR_COACH_ULTIMO";
                break;
            case "INFORMAR_COACH_ULTIMO":
                Info("Informar último Exit LARVA");
                informarCoachUltimo();

                myStatus = "EXIT";
                break;
            case "EXIT":
                Info("The agent say goodbye");
                in = cerrarSesionConSphinx();
                switch (in.getPerformative()) {
                    case ACLMessage.INFORM:

                        Info("Contenido inform exit: " + in.getContent());
                        break;
                    default:
                        Error(ACLMessage.getPerformative(in.getPerformative()) + " Could not open a session with "
                                + myServiceProvider + " due to " + getDetailsLARVA(in));
                        abortSession();
                }
                _exitRequested = true;
                break;
        }
    }
/**
 * Se encarga de leer los sensores parseando los datos obtenidos por los mismos.
 * @author Equipo de desarrollo completo.
 */
    public void readSensors() {

        out = new ACLMessage();
        out.addReceiver(new AID(myWorldManager, AID.ISLOCALNAME));
        out.setSender(this.getAID());
        out.createReply();
        out.setConversationId(myConvID);
        out.setProtocol("REGULAR");
        JsonObject readJson = new JsonObject();
        readJson.add("operation", "read");
        String read = readJson.toString();
        out.setContent(read);
        out.setConversationId(myConvID);
        out.setPerformative(ACLMessage.QUERY_REF);

        this.send(out);
        this.in = blockingReceive();

        String respuestaRead = this.in.getContent();
        switch (in.getPerformative()) {
            case ACLMessage.INFORM:
                //
                Info("Contenido inform read sensores seeker: " + in.getContent());
                String respuestaCompra = in.getContent();

                //Interpretar la respuesta
                //Interpretar el read
                JsonObject respuesta = Json.parse(respuestaRead).asObject();

                String result = respuesta.get("result").asString();
                Info("Read: ");

                // Details
                this.details = new JsonObject();
                this.details = respuesta.get("details").asObject();
                Info("details: " + this.details);
              
                JsonArray vector_percepciones;
                vector_percepciones = this.details.get("perceptions").asArray();

                //PARSEO SENSORES
                for (JsonValue s : vector_percepciones) {
                    if (s.asObject().get("sensor").toString().equals("\"distance\"")) {
                        distance = s.asObject().get("data").asArray().get(0).asFloat();

                       
                    }
                    if (s.asObject().get("sensor").toString().equals("\"angular\"")) {
                        angular = s.asObject().get("data").asArray().get(0).asFloat();
                    }
                    
                }
                break;
            default:
                Error(ACLMessage.getPerformative(in.getPerformative()) + " Could not open a session with "
                        + myServiceProvider + " due to " + getDetailsLARVA(in));
                abortSession();
        }
        
    }
/**
 * El método se encarga de calcular la casilla en la que se encuentra el alemán más cercano en base a los valores de los sensores con los que cuenta nuestro agente.
 * @author Equipo de desarrollo completo.
 * @return aleman Devuelve la casilla en la que se encuentra el alemán más cercano.
 */
    public Casilla calcularPosicion() {
        Casilla aleman = new Casilla();
        // Normalizar el ángulo

        if (angular < 0) {
            angular += 360;
        }

        // Calcular la posición relativa con respecto al seeker
        if (angular == 0) {
            aleman.x = 0;
            aleman.y = -(int) Math.round(distance);
        } else if (angular == 90) {
            aleman.x = (int) Math.round(distance);
            aleman.y = 0;
        } else if (angular == 180) {
            aleman.x = 0;
            aleman.y = (int) Math.round(distance);
        } else if (angular == 270) {
            aleman.x = - (int) Math.round(distance);
            aleman.y = 0;
        } else if (angular > 0 && angular < 90) {
            aleman.x = (int) Math.round(distance * Math.sin(Math.toRadians(angular)));
            aleman.y = (int) Math.round(distance * -Math.cos(Math.toRadians(angular)));

        } else if (angular > 90 && angular < 180) {
            angular -= 90;
            aleman.x = (int) Math.round(distance * Math.cos(Math.toRadians(angular)));
            aleman.y = (int) Math.round(distance * Math.sin(Math.toRadians(angular)));
        } else if (angular > 180 && angular < 270) {
            angular -= 180;
            aleman.x = (int) Math.round(distance * -Math.sin(Math.toRadians(angular)));
            aleman.y = (int) Math.round(distance * Math.cos(Math.toRadians(angular)));

        } else if (angular > 270 && angular < 360) {
            angular -= 270;
            Info("El angular vale > > " + angular);
            aleman.x = (int) Math.round(distance * -Math.cos(Math.toRadians(angular)));
            aleman.y = (int) Math.round(distance * -Math.sin(Math.toRadians(angular)));

        }

        // Obtenemos la posición absoluta del aleman
        Info("Posiciones inicio-> " + posx_inicio + " y " + posy_inicio);
        Info("Posiciones -> " + posicion_x + " y " + posicion_y);
        Info("Posiciones aleman antes suma -> " + aleman.x + " e " + aleman.y);
        aleman.x += posicion_x;
        aleman.y += posicion_y;
        Info("Posiciones aleman -> " + aleman.x + " e " + aleman.y);
        return aleman;
    }
    /**
     * El método se encarga de bajar el agente y comprueba si es necesario realizar una recarga mediante el parámetro booleano recargar.
     * @param recargar en caso de que sea necesario recargar el parámetro obtendrá el valor true y resetearemos nuestra variable de control de energía.
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
            energia -= 4;
        }
    }
/**
 * El método se encarga de que dadas una posiciones x e y, obtener el agente mejor posicionado para acudir a dichas posiciones. Una vez obtenido el agente le delega acudir a esas posiciones para rescatar al objetivo.
 * @param x posición x del mapa donde enviar al agente rescuer
 * @param y posición y del mapa donde enviar al agente rescuer
 * @return Devuelve una espera bloqueada para obtener respuesta posteriormente por parte del agente rescuer correspondiente.
 */
    protected ACLMessage mandarRescuer(int x, int y) {
        double distancia1 = Math.sqrt(((x - posx_rescuer1) * (x - posx_rescuer1)) + ((y - posy_rescuer1) * (y - posy_rescuer1)));
        double distancia2 = Math.sqrt(((x - posx_rescuer2) * (x - posx_rescuer2)) + ((y - posy_rescuer2) * (y - posy_rescuer2)));
        double distancia3 = Math.sqrt(((x - posx_rescuer3) * (x - posx_rescuer3)) + ((y - posy_rescuer3) * (y - posy_rescuer3)));
        Info("Pos x del 1: " + posx_rescuer1 + " Pos y del 1: " + posy_rescuer1
                + " Pos x del 2: " + posx_rescuer2 + " Pos y del 2: " + posy_rescuer2
                + " Pos x del 3: " + posx_rescuer3 + " Pos y del 3: " + posy_rescuer3);
        Info("Distancia1: " + distancia1 + " Distancia2: " + distancia2 + " Distancia3: " + distancia3);
        String mejor = "";
        if (distancia1 < distancia2) {
            if (distancia1 < distancia3) {
                mejor = "rescuer1";
            } else {
                mejor = "rescuer3";
            }
        } else if (distancia2 < distancia3) {
            mejor = "rescuer2";
        } else {
            mejor = "rescuer3";
        }
        Info("Mejor es:" + mejor);
        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(myConvID);
        out.addReceiver(new AID(mejor, AID.ISLOCALNAME));
        out.setProtocol("REGULAR");
        JsonObject envio = new JsonObject();
        envio.add("posicionx", x);
        envio.add("posiciony", y);
        out.setContent(envio.toString());
        out.setPerformative(ACLMessage.QUERY_IF);
        this.send(out);
        return blockingReceive();
    }

/**
 * El método se encarga de decirle a un agente rescuer que vuelva a su casa(posición de inicio)
 * @param rescuer El agente rescuer al que queremos enviarle el mensaje
 */
    protected void informarRescuerCasa(String rescuer) {

        out = new ACLMessage();
        out.setSender(getAID());
        out.setConversationId(myConvID);
        out.addReceiver(new AID(rescuer, AID.ISLOCALNAME));
        out.setProtocol("REGULAR");
        out.setContent("");
        out.setPerformative(ACLMessage.INFORM);
        this.send(out);
    }

}