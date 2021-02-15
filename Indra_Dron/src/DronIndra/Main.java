
package DronIndra;

import AppBoot.ConsoleBoot;

public class Main {

    public static void main(String[] args) {
        ConsoleBoot app = new ConsoleBoot("Dron", args);
        app.selectConnection(); 
        
        app.launchAgent("IndraDron2", MyWorldExplorer.class);
        app.shutDown();
    }

}