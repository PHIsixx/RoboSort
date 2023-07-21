import lejos.hardware.BrickFinder;
import lejos.hardware.Button;
import lejos.hardware.lcd.TextLCD;
import lejos.hardware.motor.EV3LargeRegulatedMotor;
import lejos.hardware.motor.EV3MediumRegulatedMotor;
import lejos.hardware.port.MotorPort;
import lejos.robotics.RegulatedMotor;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class EV3Robot {

    private static MqttCommunication mc;
    private static int selectedRoute = -1;

    public static void main(String[] args) {
        try {
            mc = new MqttCommunication();
            mc.start();
        } catch (Exception e) {
            System.out.println("Error while starting MQTT communication.");
            return;
        }

        // Initialisiere die Motoren
        RegulatedMotor leftMotor = new EV3LargeRegulatedMotor(MotorPort.A);
        RegulatedMotor rightMotor = new EV3LargeRegulatedMotor(MotorPort.D);
        RegulatedMotor threadMotor = new EV3MediumRegulatedMotor(MotorPort.C);

        // Setze die Geschwindigkeit der Motoren (Standardwert: 400)
        int motorSpeed = 400;
        leftMotor.setSpeed(motorSpeed);
        rightMotor.setSpeed(motorSpeed);
        threadMotor.setSpeed(100); // Geschwindigkeit für das Gewinde

        // Initialisiere das Display für die Anweisungen
        TextLCD lcd = BrickFinder.getDefault().getTextLCD();

        // Startposition des Roboters
        int startX = 0;
        int startY = 0;

        // Routen-Koordinaten definieren
        int[][] routes = {
            {1, 1},
            {2, 2},
            {3, 3},
            {4, 4}
        };

        // Hauptsteuerung des Roboters
        boolean exit = false;
        while (!exit) {
            lcd.clear();
            lcd.drawString("1: Route 1", 0, 0);
            lcd.drawString("2: Route 2", 0, 1);
            lcd.drawString("3: Route 3", 0, 2);
            lcd.drawString("4: Route 4", 0, 3);

            // Warte auf die Nachricht für die ausgewählte Route
            while (selectedRoute == -1) {
                try {
                    Thread.sleep(100); // Warte 100 Millisekunden
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            lcd.clear();
            lcd.drawString("Starting Route " + selectedRoute, 0, 0);

            // Hier sollte die entsprechende Route abhängig von "selectedRoute" gestartet werden
            int chosenRoute = selectedRoute - 1; // Array-Index beginnt bei 0
            if (chosenRoute >= 0 && chosenRoute < routes.length) {
                travelRoute(routes[chosenRoute][0], routes[chosenRoute][1], leftMotor, rightMotor, threadMotor);
            }

            lcd.clear();
            lcd.drawString("Route " + selectedRoute + " Completed", 0, 0);
            lcd.drawString("Press any button to continue", 0, 2);
            Button.waitForAnyPress();

            // Sende Nachricht, dass die Route abgeschlossen ist
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("type", "completed");
            jsonObject.put("route", selectedRoute);
            mc.sendMsg(mc.getTopic(), jsonObject.toJSONString().getBytes());

            // Zurücksetzen der ausgewählten Route für die nächste Auswahl
            selectedRoute = -1;
        }

        // Beende das Programm
        leftMotor.close();
        rightMotor.close();
        threadMotor.close();
        lcd.clear();
    }

    // Hilfsfunktion zum Anhalten des Roboters
    private static void stopRobot(RegulatedMotor leftMotor, RegulatedMotor rightMotor) {
        leftMotor.stop(true);
        rightMotor.stop();
    }

    // Hilfsfunktion zum Bewegen des Roboters zu den Koordinaten (x, y)
    private static void travelRoute(int x, int y, RegulatedMotor leftMotor, RegulatedMotor rightMotor, RegulatedMotor threadMotor) {
        // Berechne die Distanz-Toleranz und die Winkel-Toleranz
        final float DISTANCE_TOLERANCE = 5.0f; // 5 cm
        final float ANGLE_TOLERANCE = 5.0f; // 5 Grad

        // Definiere das feste Zwischenziel (anpassbar nach Bedarf)
        int intermediateX = x - 10; // 10 cm vor dem Ziel in x-Richtung
        int intermediateY = y - 10; // 10 cm vor dem Ziel in y-Richtung

        // Erstelle einen DifferentialPilot für das Fahren des Roboters
        DifferentialPilot pilot = new DifferentialPilot(5.6, 11.2, leftMotor, rightMotor);

        // Fahre zum Zwischenziel (intermediateX, intermediateY)
        pilot.travelTo(intermediateX, intermediateY);

        // Halte den Roboter an und senke das Gewinde
        pilot.stop();
        threadMotor.backward(); // Senke das Gewinde

        // Fahre zum Ende der Route (x, y)
        pilot.travelTo(x, y);

        // Erhöhe das Gewinde
        threadMotor.forward(); // Hebe das Gewinde an

        // Fahre rückwärts zum Zwischenziel (intermediateX, intermediateY)
        pilot.travel(-DISTANCE_TOLERANCE);
        pilot.rotate(180); // Wende den Roboter

        // Fahre zum Startpunkt (0, 0)
        pilot.travel(-x);
        pilot.travel(-y);

        // Halte den Roboter an und senke das Gewinde
        pilot.stop();
        threadMotor.backward(); // Senke das Gewinde

        // Warte auf Knopfdruck
        lejos.hardware.Button.waitForAnyPress();

        // Fahre das Gewinde erneut hoch
        threadMotor.forward(); // Hebe das Gewinde an

        // Wende den Roboter
        pilot.rotate(180);

        // Fahre die gleiche Route bis zum Ende (x, y)
        pilot.travelTo(x, y);

        // Senke das Gewinde erneut
        threadMotor.backward(); // Senke das Gewinde

        // Fahre rückwärts zum Zwischenziel (intermediateX, intermediateY)
        pilot.travel(-DISTANCE_TOLERANCE);
        pilot.rotate(180); // Wende den Roboter

        // Fahre zum Startpunkt (0, 0)
        pilot.travel(-x);
        pilot.travel(-y);

        // Halte den Roboter an und senke das Gewinde
        pilot.stop();
        threadMotor.backward(); // Senke das Gewinde
    }
}

class MqttCommunication extends Thread{
	
	MqttClient client;

	String broker = "tcp://141.46.233.97:1883"; //"tcp://test.mosquitto.org:1883"; //
	String topic = "robots/container";
	
	public MqttCommunication() throws MqttException{
		
		client = new MqttClient(broker,MqttClient.generateClientId(), new MemoryPersistence());
	
		client.setCallback(new MqttCallback() {

		    @Override
		    public void connectionLost(Throwable cause){ //Called when the client lost the connection to the broker 
		    	
				System.out.println("connection lost"); 
				
				try {
					client = new MqttClient(broker,MqttClient.generateClientId(), new MemoryPersistence());
	
					client.connect();
	
					while(!client.isConnected()) {
					    try {
					        Thread.sleep(5000);
					    } catch (Exception e) {}
					}
					
					client.subscribe(topic);
					
				} catch (MqttException e) {}

				System.out.println("connected");


    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        try {
            JSONObject msg = (JSONObject) new JSONParser().parse(new String(message.getPayload()));
            if (topic.equals(getTopic())) {
                String type = ((String) msg.get("type"));
                int pos;
                if (msg.get("value") == null) {
                    pos = -1;
                } else {
                    pos = ((Long) msg.get("value")).intValue();
                }
                if (type.equals("remove") && pos != -1) {
                    System.out.print("emptying " + pos);
                    selectedRoute = pos;
                    System.out.println(" DONE");
                } else {
                    System.out.println("unhandled msg");
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.out.println(e.getCause());
            e.printStackTrace();
        }
    }
}



