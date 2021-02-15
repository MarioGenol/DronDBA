
package DronIndra;

import AppBoot.ConsoleBoot;

/**
 * Lanzamiento de los agentes
 * @author Equipo de desarrollo al completo
 */
public class Main {

    public static void main(String[] args) {
        ConsoleBoot app = new ConsoleBoot("", args);
        app.selectConnection(); 
        
        app.launchAgent("listener1", Awacs.class);
        app.launchAgent("rescuer1", Rescuer.class);
        app.launchAgent("rescuer2", Rescuer.class);
        app.launchAgent("rescuer3", Rescuer.class);
        app.launchAgent("seeker2",Seeker.class);
        app.launchAgent("coach1", Coach.class);
        app.shutDown();
    }

}