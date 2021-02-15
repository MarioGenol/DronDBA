package DronIndra;

import static ACLMessageTools.ACLMessageTools.getDetailsLARVA;
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
import java.util.LinkedList;
import java.util.Queue;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * El agente Rescuer extiende al AgenteSupremo y es el encargado de realizar las acciones requeridas por el agente Seeker y así rescatar a los objetivos. 
 * @author Equipo de desarrollo completo
 */
public class Rescuer extends AgenteSupremo {

/**
 * Este método contempla todos los estados por los que va a pasar el agente a lo largo de su ciclo de vida y se encarge de hacer las llamadas pertinentes a otros métodos y de interpretar las respuestas de otros agentes.
 * Los estados posibles son los siguientes: ESPERANDO - espera respuesta del coach y obtiene información del mapa en que va participar en la búsqueda de objetivos. Si todo va bien cambia el estado a SESSION_REGISTER.
 *                                          SESSION_REGISTER- obtinene las yellowpages y las actualiza con la nueva información. Se registra en el worldManager guardando los coins en un monedero para ser usasdas posteriormente, y si todo va bien cambia el estado a MARKET_PLACE
 *                                          MARKET_PLACE - Pide las yellowPages y las actualiza. Se piden las tiendas de la sesión y se procede a comprar los sensores necesarios.Si todo va bien se procede al estado de LOGIN.
 *                                          LOGIN - Logueamos al agente en el worldManager con los tickets de compra adquiridos y las posiciones iniciales del mismo en el mapa. Si todo va bien pasamos al estado CAZA.
 *                                          CAZA - Esperamos respuesta por parte del agente Seeker y en base a la respuesta : 
 *                                                                              si el agente nos dice que comencemos la caza el recuer realiza una primera recarga, calcula el recorrido al que desplazarse en base a las posiciones trasmitidas por el seeker y ejecuta los movimientos. Una vez ejecutados avisa al seeker de que ha terminado.
 *                                                                              Por otro lado, si el agente Seeker nos dice que vayamos a casa, el rescuer acude a su posición de inicio.                                                                            
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
                        Error(ACLMessage.getPerformative(in.getPerformative()) + " Could not open a session with " + myServiceProvider + " due to " + getDetailsLARVA(in));
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

                        shops = new ArrayList(myYP.queryProvidersofService("shop@" + myConvID));//obtener todas las tiendas

                        break;
                    default:
                        Error(ACLMessage.getPerformative(in.getPerformative()) + " Could not open a session with " + myServiceProvider + " due to " + getDetailsLARVA(in));
                        abortSession();
                }


                actualizarListadoPreciosTiendas(shops);
          

                myStatus = "LOGIN";
                break;

            case "LOGIN":
                Info("LOGIN RESCUER");
                in = login();
             
                switch (in.getPerformative()) {
                    case ACLMessage.INFORM:

                        Info("Contenido inform login rescuer: " + in.getContent());
                        JsonArray capacidades = new JsonArray();
                        capacidades = Json.parse(in.getContent()).asObject().get("capabilities").asArray();//creamos un objeto json a partir del contenido de inbox
                        for (JsonValue p : capacidades) {//llenar capabilities
                            capabilities.add(p.asString());
                        }

                        break;
                    case ACLMessage.FAILURE:

                        Info("Contenido failure login rescuer: " + in.getContent());
                        break;
                    case ACLMessage.REFUSE:

                        Info("Contenido refuse login rescuer: " + in.getContent());
                        break;
                    default:
                        Error(ACLMessage.getPerformative(in.getPerformative()) + " Could not open a session with " + myServiceProvider + " due to " + getDetailsLARVA(in));
                        abortSession();
                }
                myStatus = "CAZA";
               
                break;
            case "CAZA":

                in = this.blockingReceive();
                switch (in.getPerformative()) {
                    case ACLMessage.QUERY_IF:

                        Info("Contenido QUERY_IF CAZA rescuer: " + in.getContent() + " sender: " + in.getSender());

                        if (in.getSender().toString().contains("seeker2")) {
                            Info("Hemos recibido mensaje del seeker para empezar la caza");
                            JsonObject aux = Json.parse(in.getContent()).asObject();
                            recargar(new ArrayList(myYP.queryProvidersofService("shop@" + myConvID)));
                            recorrido(aux.get("posicionx").asInt(), aux.get("posiciony").asInt());
                            ejecutarMovientos();
                            avisarSeeker();
                        }

                        break;
                    case ACLMessage.INFORM:
                        if (in.getSender().toString().contains("seeker2")) {
                            Info("Volviendo a casa a la pos " + posx_inicio + "," + posy_inicio);
                            recargar(new ArrayList(myYP.queryProvidersofService("shop@" + myConvID)));
                            recorrido(posx_inicio, posy_inicio);
                            ejecutarMovientos(); 
                           
                        }
                        break;
                    default:
                        Error(ACLMessage.getPerformative(in.getPerformative()) + " Could not open a session with " + myServiceProvider + " due to " + getDetailsLARVA(in));
                        abortSession();
                }
               
                break;
        }
    }

    /**
     * El método se encarga de informar al agente Seeker de que ha realizado una acción satisfactoriamente. 
     * @author El equipo de desarrollo completo.
     */
    public void avisarSeeker() {

        out = new ACLMessage();
        out.addReceiver(new AID("seeker2", AID.ISLOCALNAME));
        out.setSender(this.getAID());
        out.setProtocol("REGULAR");
        out.setContent("");
        out.createReply();
        out.setConversationId(myConvID);
        out.setPerformative(ACLMessage.INFORM);

        this.send(out);
    }
     
}