import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import lejos.hardware.Button;

public class Main {
	
	private static MqttCommunication mc;
	
	public static void main(String[] args) throws MqttException {

		mc = new MqttCommunication();
		mc.start();
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
		    }

		    @Override
		    public void messageArrived(String topic, MqttMessage message) throws Exception {
		    	try {
		    	JSONObject msg  = (JSONObject) new JSONParser().parse(new String(message.getPayload()));
		    	
		    	if (topic.equals(getTopic())) {
		    		String type = ((String) msg.get("type"));
		    		int pos;
		    		if (msg.get("value") == null){
		    			pos = -1;
		    		} else {
		    			pos = ((Long) msg.get("value")).intValue();
		    		}
		    		
		    		if (type.equals("remove") && pos != -1) {
		    			System.out.print("emptying " + pos);
		    			while(!Button.ENTER.isDown()) {
			    			try{
			    				Thread.sleep(10);
			    			}
			    			catch (InterruptedException e) {
			    				
			    			}
		    			}
		    			System.out.println(" DONE");
						JSONObject jsonObject = new JSONObject();
						jsonObject.put("type", "returned");
						jsonObject.put("value", pos);
		    			sendMsg("robots/sorter", jsonObject.toJSONString().getBytes());
		    			
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

		    @Override
		    public void deliveryComplete(IMqttDeliveryToken token) {//Called when a outgoing publish is complete 
		    }
		});

		client.connect();

		while(!client.isConnected()) {
		    try {
		        Thread.sleep(5000);
		    } catch (Exception e) {}
		}
		
		client.subscribe(topic);

		System.out.println("connected");
	}
	
	public String getTopic() {
		return this.topic;
	}
	
	public void sendMsg(String topic, byte[] bytes) {
		while(!client.isConnected()) {
		    try {
		        Thread.sleep(5000);
		    } catch (Exception e) {}
		}
		
		MqttMessage m = new MqttMessage(bytes);
		try {
			client.publish(topic, m);
		} catch (MqttException e) {
			System.out.println("msg delivery failed");
		}
	}
}
