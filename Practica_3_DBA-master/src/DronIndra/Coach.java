/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package DronIndra;

import static ACLMessageTools.ACLMessageTools.getDetailsLARVA;
import static ACLMessageTools.ACLMessageTools.getJsonContentACLM;
import IntegratedAgent.IntegratedAgent;
import Map2D.Map2DGrayscale;
import YellowPages.YellowPages;
import com.eclipsesource.json.JsonObject;
import jade.core.AID;
import jade.lang.acl.ACLMessage;

/**
 * Agente que maneja los inicios de la conexión con el servidor
 * @author alexismoga
 */
public class Coach extends IntegratedAgent {

    ACLMessage inbox, outbox;
    private boolean myError;
    Cartographer mapa = new Cartographer();
    //Awacs awacs = new Awacs();
    String dronList = "listener1";
    String dronResc1 = "rescuer1";
    String dronResc2 = "rescuer2";
    String dronResc3 = "rescuer3";
    String dronSeeker = "seeker2";
    protected YellowPages myYP;
    protected String myStatus, myService, myWorldManager, myWorld, myConvID;

    @Override
    public void setup() {
        _identitymanager = "Sphinx";
        super.setup();

        Info("Booting Coach");
        myService = "Analytics group Indra";
        myWorld = "World5";
        // First state of the agent
        myStatus = "CHECKIN-LARVA";

        // To detect possible errors
        myError = false;

        _exitRequested = false;
    }

    @Override
    public void plainExecute() {
        plainWithErrors();
    }

    @Override
    public void takeDown() {
        Info("Taking down");
        super.takeDown();
    }

    protected ACLMessage sendCheckinLARVA(String im) {
        outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(im, AID.ISLOCALNAME));
        outbox.setContent("");
        outbox.setProtocol("ANALYTICS");
        outbox.setEncoding(_myCardID.getCardID());
        outbox.setPerformative(ACLMessage.SUBSCRIBE);
        send(outbox);
        return blockingReceive();
    }

    protected ACLMessage sendCheckoutLARVA(String im) {
        outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(im, AID.ISLOCALNAME));
        outbox.setContent("");
        outbox.setProtocol("ANALYTICS");
        outbox.setPerformative(ACLMessage.CANCEL);
        send(outbox);
        return blockingReceive();
    }

    protected ACLMessage sendSubscribeWM(String problem) {
        outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(myWorldManager, AID.ISLOCALNAME));
        outbox.setProtocol("ANALYTICS");
        outbox.setContent(new JsonObject().add("problem", problem).toString());
        outbox.setPerformative(ACLMessage.SUBSCRIBE);
        this.send(outbox);
        return this.blockingReceive();
    }

    protected ACLMessage sendCANCELWM() {
        outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(myWorldManager, AID.ISLOCALNAME));
        outbox.setContent("");
        outbox.setConversationId(myConvID);
        outbox.setProtocol("ANALYTICS");
        outbox.setPerformative(ACLMessage.CANCEL);
        send(outbox);
        return blockingReceive();
    }

    protected ACLMessage queryYellowPages(String im) {
        YellowPages res = null;

        outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(im, AID.ISLOCALNAME));
        outbox.setProtocol("ANALYTICS");
        outbox.setContent("");
        outbox.setPerformative(ACLMessage.QUERY_REF);
        this.send(outbox);
        return blockingReceive();
    }
    
    /**
     * Despierta al agente deseado
     * @param dron agente que despertaremos
     */
    protected void wakeUpAgents(String dron) {
        outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.setConversationId(myConvID);
        outbox.addReceiver(new AID(dron, AID.ISLOCALNAME));
        outbox.setProtocol("REGULAR");
        JsonObject envio = new JsonObject();
        envio.add("mundo", myWorld);
        outbox.setContent(envio.toString());
        outbox.setPerformative(ACLMessage.QUERY_IF);
        this.send(outbox);

    }
    /**
     * Indica al Listener que termina
     * @return espera bloqueante
     */
    protected ACLMessage sayFinishToListener() {
        outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(dronList, AID.ISLOCALNAME));
        outbox.setProtocol("REGULAR");
        outbox.setContent("FINISH");

        outbox.setPerformative(ACLMessage.REQUEST);
        this.send(outbox);
        return blockingReceive();
    }
    /**
     * Estados principales de la ejecución del agente coach:
     *  CHECKIN-LARVA: Se registra con el ID
     *  SUBSCRIBE-WM: Se suscribe al WM
     *  WAKE-UP AGENTS: Despierta a los agentes de nuestro sistema
     *  FINISH-LISTENER: Decimos al listener que termine
     *  CANCEL-WM: adiós al WM
     *  CHECKOUT-LARVA: adiós a Sphinx
     *  EXIT: terminamos
     */
    public void plainWithErrors() {
        // Basic iteration
        switch (myStatus.toUpperCase()) {
            case "CHECKIN-LARVA":
                //a. i
                Info("Checkin in LARVA with " + _identitymanager);
                inbox = sendCheckinLARVA(_identitymanager); // As seen in slides
                myError = (inbox.getPerformative() != ACLMessage.INFORM);
                if (myError) {
                    Info("\t" + ACLMessage.getPerformative(inbox.getPerformative())
                            + " Checkin failed due to " + getDetailsLARVA(inbox));
                    myStatus = "EXIT";
                    break;
                }
                myStatus = "SUBSCRIBE-WM";
                Info("\tCheckin ok");
                break;
            case "SUBSCRIBE-WM":
                //a. iii
                Info("Retrieve who is my WM");
                // First update Yellow Pages
                inbox = queryYellowPages(_identitymanager); // As seen oon slides
                myError = inbox.getPerformative() != ACLMessage.INFORM;
                if (myError) {
                    Info("\t" + ACLMessage.getPerformative(inbox.getPerformative())
                            + " Query YellowPages failed due to " + getDetailsLARVA(inbox));
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                myYP = new YellowPages();
                myYP.updateYellowPages(inbox);
                // It might be the case that YP are right but we dont find an appropriate service for us, then leave
                if (myYP.queryProvidersofService(myService).isEmpty()) {
                    Info("\t" + "There is no agent providing the service " + myService);
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                // Choose one of the available service providers, i.e., the first one
                myWorldManager = myYP.queryProvidersofService(myService).iterator().next();

                // Now it is time to start the game and turn on the lights within a given world
                //b.i
                inbox = sendSubscribeWM(myWorld);
                myError = inbox.getPerformative() != ACLMessage.INFORM;
                if (myError) {
                    Info(ACLMessage.getPerformative(inbox.getPerformative())
                            + " Could not open a session with "
                            + myWorldManager + " due to " + getDetailsLARVA(inbox));
                    myStatus = "CHECKOUT-LARVA";
                    break;
                }
                // Keep the Conversation ID and spread it amongs the team members
                //b.ii
                myConvID = inbox.getConversationId();
                // Move on to get the map
                myStatus = "WAKE-UP AGENTS";
                break;

  
            case "WAKE-UP AGENTS":
                //wake up Listener and Rescuer
                Info("Waking up Rescuer and Listener");

                wakeUpAgents(dronResc1);
                wakeUpAgents(dronResc2);
                wakeUpAgents(dronResc3);
                wakeUpAgents(dronSeeker);
                wakeUpAgents(dronList);

                inbox = blockingReceive();
                myStatus = "FINISH-LISTENER";
                break;
            case "FINISH-LISTENER":
                inbox = sayFinishToListener();
                myStatus = "CANCEL-WM";
                break;
            case "CANCEL-WM":
                Info("Closing the game");
                inbox = sendCANCELWM();
                myStatus = "CHECKOUT-LARVA";
                break;
            case "CHECKOUT-LARVA":
                Info("Exit LARVA");
                inbox = sendCheckoutLARVA(_identitymanager);
                myStatus = "EXIT";
                break;
            case "EXIT":
                Info("The agent dies");
                _exitRequested = true;
                break;
        }
    }

}