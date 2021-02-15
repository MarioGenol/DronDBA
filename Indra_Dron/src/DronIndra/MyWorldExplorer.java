package DronIndra;

import ControlPanel.TTYControlPanel;
import IntegratedAgent.IntegratedAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import com.eclipsesource.json.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Vector;


public class MyWorldExplorer extends IntegratedAgent {
    
    TTYControlPanel myControlPanel; //interfaz sensores

    String status; // login, read, heuristica, execute, logout
    String status_heuristica; //
    String receiver;
    ACLMessage inbox, outbox;
    JsonObject details, perceptions;
    private String key;
    private int width, height, maxflight;
    private ArrayList<String> capabilities;
    private JsonArray sensores;
    
    private int energia=1000;
    
    private Queue<String> cola_de_movimientos = new LinkedList();
    
    private int [][] mapa;
    
    private Vector<Integer>  sensor_gps= new Vector<Integer>();
    private int sensor_gps_x, sensor_gps_y, sensor_gps_z;
    private Vector<Vector<Integer>> sensor_visual=new Vector<Vector<Integer>>();
    private Vector<Vector<Integer>> sensor_thermal=new Vector<Vector<Integer>>();
    double sensor_angular,sensor_compass, sensor_distance;
    boolean sensor_alive,sensor_ontarget;
    
    
    int compass_actual;
    int angular_normalizado;
    int angular_objetivo;
    int compass_normalizado;
    
    int turno_actual = 0;
    int duracion_memoria = 5;
    
    //para el bordeo OJO REVISAR QUE SE USAN GLOBAL
    int altura_objetivo;
    int altura_actual;
    
    
    @Override
    public void setup() {
        super.setup();
        doCheckinPlatform();
        doCheckinLARVA();
        receiver = this.whoLarvaAgent();
        status = "login";
        _exitRequested= false;
        
        myControlPanel = new TTYControlPanel(getAID()); //vista sensores visuales
    }

    @Override
    public void plainExecute() {
        while (!_exitRequested) {
            switch (status) {
                case "login":
                    Login();
                    break;
                case "read":
                    readSensors();
                    break;
                case "heuristica":
                    heuristica();
                    break;
                case "execute":
                    execute();
                    break;
                case "logout":
                    Logout();
                    _exitRequested = true;
                    break;
                default:
                    Info("Error en el case principal.");
                    _exitRequested = true;
            }
        }

    }
    
    public void enviarMensaje(String receiver, String content) {
        if (inbox == null){
            this.outbox = new ACLMessage();
            outbox.setSender(this.getAID());
            outbox.addReceiver(new AID(receiver, AID.ISLOCALNAME));
        }else {
            outbox = inbox.createReply();
        }
        outbox.setContent(content);            

        this.send(outbox);
    }
    
    public void Login(){
        //Prepara el JSON para logearse
        JsonObject loginJson = new JsonObject();
        loginJson.add("command","login");
        loginJson.add("world","World8");  
        this.sensores = new JsonArray();
        
        this.sensores.add("alive");
        this.sensores.add("payload");
        this.sensores.add("ontarget");
        this.sensores.add("gps");
        this.sensores.add("compass");
        this.sensores.add("distance");
        this.sensores.add("angular");
        this.sensores.add("altimeter");
        this.sensores.add("visual");
        this.sensores.add("lidar");
        this.sensores.add("thermal");
        this.sensores.add("energy");
        loginJson.add("attach", sensores);
        
        String login = loginJson.toString();
        
        // Enviar el login al agente de Larva
        enviarMensaje(this.receiver, login);

        // Esperar la respuesta del servidor
        ACLMessage inLogin = this.blockingReceive();
        String respuestaLogin = inLogin.getContent();

        //Interpretar el login
        JsonObject respuesta = Json.parse(respuestaLogin).asObject();
        String result = respuesta.get("result").asString();
        if(result.equals("ok")){
            Info("Login:");
            // Llave
            this.key = respuesta.get("key").asString();
            Info("Key: " + this.key);
            // Anchura
            this.width = respuesta.get("width").asInt();
            Info("Width: " + this.width);
            // Altura
            this.height = respuesta.get("height").asInt();
            Info("Height: " + this.height);
            // Altura máxima
            this.maxflight = respuesta.get("maxflight").asInt();
            Info("Maxflight: " + this.maxflight);
            // Capacidades
            String capacidades = "";
            
            this.capabilities = new ArrayList<String>();
            for (JsonValue j : respuesta.get("capabilities").asArray()){
                capabilities.add(j.asString());
                capacidades += j.asString() + ", ";
            }
            //Info("Capacidades: " + capacidades);
               
            mapa = new int[width][height];
            
            this.status = "read";
        }else if(result.equals("error")){
            Info("Error: " + respuesta.get("details").asString());
        }
        
    }
    
    /**
     * Realiza una lectura de los sensores
     * @author José Manuel López Molina
     */
    public void readSensors(){
        JsonObject readJson = new JsonObject();
        readJson.add("command","read");
        readJson.add("key",key);
        
        String read = readJson.toString();
        
        // Enviar el read al agente de Larva
        enviarMensaje(this.receiver, read);

        // Esperar la respuesta del servidor
        this.inbox = this.blockingReceive();
        
        String respuestaRead = this.inbox.getContent();

        //Interpretar el read
        JsonObject respuesta = Json.parse(respuestaRead).asObject();
        String result = respuesta.get("result").asString();
        
        if(result.equals("ok")){
            Info("Read: ");
            
            // Details
            this.details = new JsonObject();
            this.details = respuesta.get("details").asObject();
            Info("details: " + this.details);
            /*
            VECTOR JSON QUE ALMACENA TODOS LOS SENSORES SIN FORMATEAR
            */
            JsonArray vector_percepciones;
            vector_percepciones = this.details.get("perceptions").asArray();
            
            //PARSEO SENSORES
            
             for (JsonValue s : vector_percepciones){
                if(s.asObject().get("sensor").toString().equals("\"alive\"")){
                    
                    sensor_alive=(1 == s.asObject().get("data").asArray().get(0).asInt());
                    //System.out.println("Sensor: "+sensor_alive);
                }
                if(s.asObject().get("sensor").toString().equals("\"ontarget\"")){
                    sensor_ontarget=(1 == s.asObject().get("data").asArray().get(0).asInt());
                    //System.out.println("Sensor: "+sensor_ontarget);
                }
                if(s.asObject().get("sensor").toString().equals("\"gps\"")){
                    for(JsonValue x : s.asObject().get("data").asArray()){
                        for(JsonValue y : x.asArray()){
                            sensor_gps.add(y.asInt());
                        }                       
                    }
                    sensor_gps_x = sensor_gps.get(0);
                    sensor_gps_y = sensor_gps.get(1);
                    sensor_gps_z = sensor_gps.get(2);
                    
                    //System.out.println("Sensor_gps: "+s.asObject().get("data").toString());
                    //System.out.println("PRUEBA gps: "+sensor_gps.toString());
                }
                if(s.asObject().get("sensor").toString().equals("\"visual\"")){
                    Vector<Integer> v =new Vector<Integer>();
                    for(JsonValue x : s.asObject().get("data").asArray()){
                      for(JsonValue y : x.asArray()){
                          v.add(y.asInt());
                      }
                       sensor_visual.add(new Vector<Integer>(v));
                       v.clear();
                    } 
                    
                    int primera_col = sensor_gps_x -3;
                    int primera_fil = sensor_gps_y - 3;                    
                    for(int i = 0; i < 7; i++){
                        for(int j = 0; j < 7; j++){
                            if(sensor_visual.get(i).get(j) >= maxflight || sensor_visual.get(i).get(j) == -2147483648){
                                int aux_fil = primera_fil + i;
                                int aux_col = primera_col + j;
                                
                                if(aux_fil < width && aux_fil >= 0){
                                    if(aux_col < height && aux_col >= 0){
                                        mapa[aux_fil][aux_col] = -2147483648;
                                    }
                                }                                
                            }
                        }
                    }
                    //System.out.println("Sensor_visual: "+s.asObject().get("data").toString());
                    //System.out.println("Sensor_visual prueba :"+sensor_visual);
                }
                if(s.asObject().get("sensor").toString().equals("\"compass\"")){
                    sensor_compass=s.asObject().get("data").asArray().get(0).asDouble();
                    
                    //System.out.println("Sensor compass: "+sensor_compass);
                }
                if(s.asObject().get("sensor").toString().equals("\"distance\"")){
                    sensor_distance=s.asObject().get("data").asArray().get(0).asDouble();
                    
                    //System.out.println("Sensor compass: "+sensor_compass);
                }
                if(s.asObject().get("sensor").toString().equals("\"angular\"")){
                    sensor_angular=s.asObject().get("data").asArray().get(0).asDouble();
                    //System.out.println("Sensor angular: "+sensor_angular);
                }
                //System.out.println("Sensor: "+s.asObject().get("sensor").toString());
            }
           
            this.status="heuristica";
        }else if(result.equals("error")){
            Info("Error: " + respuesta.get("details").asString());
            status = "logout";
        }
        
        //Actualizar interfaz sensores
        myControlPanel.feedData(inbox, width, height, maxflight);
        // width height devueltos por login
        myControlPanel.fancyShow();
        
        energia -= 12;
    }
    
    /**
     * Author Gonzalo y Mario
     */
    private void heuristica(){
        if(!sensor_alive){
            status = "logout";
        } else{
            boolean salir_heuristica = false;
            status_heuristica = "bajar";
            while(!salir_heuristica){
                switch (status_heuristica) {
                    case "bajar":
                        bajar();
                        break;
                    case "mejor_casilla":
                        mejorCasilla();
                        break;
                    case "orientar_dron":
                        orientar_dron();
                        break;
                    case "subir":
                        subir();
                        break;
                    case "moveF":
                        mover_delante();
                        salir_heuristica = true;
                        break;
                    case "fin":
                        salir_heuristica = true;
                        break;
                    case "bordear":
                        //bordear();
                        break;
                    default:
                        salir_heuristica = true;
                        status = "logout";
                        Info("Error en el case de la heuristica");
                }
            }
            turno_actual++;
            status = "execute";
            if(sensor_ontarget){
                status = "logout";
            }
        }
    }
    
    private int normalizarAngulo(double angulo){
        int angulo_return = 0;
        if(angulo < 0){
            angulo += 360;
        }
        
        //Norte
        if((angulo >= 0 && angulo < 22.5) || (angulo >= 337.5 && angulo < 360))
            angulo_return = 0;
        //Noreste
        if(angulo >= 22.5 && angulo < 67.5)
            angulo_return = 45;
        //Este
        if(angulo >= 67.5 && angulo < 112.5)
            angulo_return = 90;
        //Sureste
        if(angulo >= 112.5 && angulo < 157.5)
            angulo_return = 135;
        //Sur
        if(angulo >= 157.5 && angulo < 202.5)
            angulo_return = 180;
        //Suroeste
        if(angulo >= 202.5 && angulo < 247.5)
            angulo_return = 225;
        //Oeste
        if(angulo >= 247.5 && angulo < 292.5)
            angulo_return = 270;
        //Noroeste
        if(angulo >= 292.5 && angulo < 337.5)
            angulo_return = 315;
        
        return angulo_return;
    }
        
    private void bajar(){
        // Queremos bajar a repostar
        // O estamos en el objetivo
        if (energia < maxflight || sensor_distance == 0) {
            for(int i = sensor_gps_z; i >= sensor_visual.get(3).get(3) + 5; i -= 5){
                cola_de_movimientos.add("moveD");
                energia -= 5;
            }
            cola_de_movimientos.add("touchD");
            energia -= sensor_gps_z - sensor_visual.get(3).get(3);
            if (sensor_distance != 0){
                cola_de_movimientos.add("recharge");
                energia = 1000;
            }
            status_heuristica = "fin";         
        }
        else{
           status_heuristica = "mejor_casilla"; 
        }
    }
    
    /**
     * Normaliza angulos negativos devolviendo su equivalente positivo
     * @author alexis
     * 
     */
    /*private int normalizarAngulo(int angulo){
        int  newAngle = angulo;
        while (newAngle < 0) newAngle += 360;
        return newAngle;
    }*/
    
    
    private void mejorCasilla(){
        angular_normalizado = normalizarAngulo(sensor_angular);
        compass_normalizado = normalizarAngulo(sensor_compass);
        
        int j = angular_normalizado / 45;
        int [][] vector = new int[8][2];
        
        // Norte
        vector[0][0] =  sensor_gps_y - 1;
        vector[0][1] =  sensor_gps_x;
        
        // Noreste
        vector[1][0] =  sensor_gps_y - 1;
        vector[1][1] =  sensor_gps_x + 1;
        
        // Este
        vector[2][0] =  sensor_gps_y;
        vector[2][1] =  sensor_gps_x + 1;
        
        // Sureste
        vector[3][0] =  sensor_gps_y + 1;
        vector[3][1] =  sensor_gps_x + 1;
        
        // Sur
        vector[4][0] =  sensor_gps_y + 1;
        vector[4][1] =  sensor_gps_x;
        
        // Suroeste
        vector[5][0] =  sensor_gps_y + 1;
        vector[5][1] =  sensor_gps_x - 1;
        
        // Oeste
        vector[6][0] =  sensor_gps_y;
        vector[6][1] =  sensor_gps_x - 1;
        
        // Noroeste
        vector[7][0] =  sensor_gps_y - 1;
        vector[7][1] =  sensor_gps_x - 1;
        
        boolean encontrado = false;
        
        /*for(int i = 0; i < 8; i++){
            if(!encontrado){
                // Se suma i
                if (i % 2 == 0) {
                    j = (j + i) % 8;
                    // Comprobar que es una posición del mapa
                    if (vector[j][0] < width && vector[j][0] >= 0) {
                        if (vector[j][1] < height && vector[j][1] >= 0) {
                            // Comprobar que la casilla objetivo está libre
                            if (mapa[vector[j][0]][vector[j][1]] > -turno_actual) {
                                angular_objetivo = 45 * j;
                                mapa[vector[j][0]][vector[j][1]] = -turno_actual - duracion_memoria;
                                encontrado = true;
                            }
                        }
                    }
                } else {
                    if((j - i) < 0){
                        j = (j - i) + 8; 
                    }
                    else{
                        j = j - i;    
                    }
                    
                    
                    // Comprobar que es una posición del mapa
                    if (vector[j][0] < width && vector[j][0] >= 0) {
                        if (vector[j][1] < height && vector[j][1] >= 0) {
                            // Comprobar que la casilla objetivo está libre
                            if (mapa[vector[j][0]][vector[j][1]] > -turno_actual) {
                                angular_objetivo = 45 * j;
                                mapa[vector[j][0]][vector[j][1]] = -turno_actual - duracion_memoria;
                                encontrado = true;
                            }
                        }
                    }
                    
                }
            }
        }*/
        
        for(int i = 0; i < 8; i++){
            if(!encontrado){
                if ((j - i) < 0) {
                    j = (j - i) + 8;
                } else {
                    j = j - i;
                }

                // Comprobar que es una posición del mapa
                if (vector[j][0] < width && vector[j][0] >= 0) {
                    if (vector[j][1] < height && vector[j][1] >= 0) {
                        // Comprobar que la casilla objetivo está libre
                        if (mapa[vector[j][0]][vector[j][1]] >= 0) {
                            angular_objetivo = 45 * j;
                            mapa[vector[j][0]][vector[j][1]] = -turno_actual - duracion_memoria;
                            encontrado = true;
                        }
                    }
                }
            }
        }
        
        if(!encontrado){
            angular_objetivo = angular_normalizado;
        }
        
        status_heuristica = "orientar_dron";
    }
    
    private void orientar_dron(){
        int inicio = compass_normalizado;
        int fin = angular_objetivo;
        
        int diferencia = inicio - fin;
        int pasos = diferencia / 45;
        
        
        if(inicio != fin){
            // Giro horario
            if (pasos < 0) {
                // Merece la pena hacer giro horario
                int pasos_valor_absoluto = Math.abs(pasos);
                if (pasos_valor_absoluto <= 4) {
                    for (int i = 0; i < pasos_valor_absoluto; i++) {
                        cola_de_movimientos.add("rotateR");
                        energia--;
                    }
                } // No merece la pena hacer giro horario
                else {
                    pasos_valor_absoluto -= 4;
                    for (int i = 0; i < pasos_valor_absoluto; i++) {
                        cola_de_movimientos.add("rotateL");
                        energia--;
                    }
                }
            } // Giro antihorario
            else {
                // Merece la pena hacer giro antihorario
                if (pasos <= 4) {
                    for (int i = 0; i < pasos; i++) {
                        cola_de_movimientos.add("rotateL");
                        energia--;
                    }
                } // No merece la pena hacer giro antihorario
                else {
                    pasos -= 4;
                    for (int i = 0; i < pasos; i++) {
                        cola_de_movimientos.add("rotateR");
                        energia--;
                    }
                }
            } 
        }
        
        status_heuristica = "subir";
    }
    
    /**
     * Si el obstaculo no se puede bordear por arriba lo intenta por los lados
     * @author alexis
     * 
     */
    /*private void bordear(){
        //costeCasilla(derecha)>costeCasilla(izda)
        //if(sensor_visual.get(3).get(2)<= sensor_visual.get(3).get(4)){
            cola_de_movimientos.add("rotateR");
            compass_actual += 45;
       
        //}else{
            //cola_de_movimientos.add("rotateL");
            //compass_actual -= 45;
            
        //}
        energia--;
        status_heuristica = "subir";
    }*/
    
    private void subir(){
        int fil = 0, col = 0;
        //if(compass_actual < 0) compass_actual = normalizarAngulo(compass_actual);
        switch(angular_objetivo){
            case 0://N
                fil = 2;
                col = 3;
                break;
            case 45://NE
                fil = 2;
                col = 4;
                break;
            case 90://E
                fil = 3;
                col = 4;
                break;
            case 135://SE
                fil = 4;
                col = 4;
                break;
            case 180://S
                fil = 4;
                col = 3;
                break;           
            case 225://SW
                fil = 4;
                col = 2;
                break;
            case 270://W
                fil = 3;
                col = 2;
                break;
            case 315://NW
                fil = 2;
                col = 2;
                break;
        }
        
        altura_objetivo = sensor_visual.get(fil).get(col);
        altura_actual = sensor_gps_z;
        
        //Si no puede subir, bordea
        //if(altura_objetivo>=maxflight)
           //status_heuristica = "bordear";
        //else{//si puede, sube
            for(int i = altura_actual; i < altura_objetivo; i += 5){
                cola_de_movimientos.add("moveUP");
                energia -= 5;
            }
        
            status_heuristica = "moveF";
        //}
        
    }
    
    private void mover_delante(){
        cola_de_movimientos.add("moveF");
        energia--;
        status_heuristica = "fin";
    }
        
    
    /**
     * @author Alexis Molina García
     */
    public void execute(){
        Info("Execute");
        JsonObject exeJson = new JsonObject();
        
        while (!cola_de_movimientos.isEmpty()){
            exeJson.add("command","execute");
            exeJson.add("key", this.key);
            String accion = cola_de_movimientos.poll();
            exeJson.add("action", accion);
            
            enviarMensaje(this.receiver, exeJson.toString());
            this.inbox = this.blockingReceive();
            Info(exeJson.toString());
        }
        
        status="read";
        sensor_visual.clear();
        sensor_gps.clear();
        // Si se está en on_target se hace logout
        // Else se vuelve a leer las percepciones
    }
    /**
     *@authors
     */
    public void Logout(){
        //myControlPanel.close();
        JsonObject logoutJson = new JsonObject();
        logoutJson.add("command","logout");
        logoutJson.add("key", this.key);
        enviarMensaje(this.receiver, logoutJson.toString());
        Info("Haciendo logout");
    }
   
    /**
     *@authors
     */
    @Override
    public void takeDown() {
        this.doCheckoutLARVA();
        this.doCheckoutPlatform();
        super.takeDown();
    }
}
