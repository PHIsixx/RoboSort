import lejos.hardware.motor.*;
import lejos.hardware.sensor.*;
import lejos.hardware.port.MotorPort;
import lejos.hardware.port.SensorPort;
import lejos.robotics.Color;
import lejos.utility.*;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.simple.JSONObject;
import org.json.simple.parser.*;

public class Main{
	
	private static MqttCommunication mc;
	private static Work w;
	private static Communicate comm;
	
	public static void main(String[] args) throws MqttException {
		comm = new Communicate();
		w = new Work(comm);
		mc = new MqttCommunication(comm);

		w.start();
		mc.start();
	}
}

class Work extends Thread{
	Communicate commObj;
	
	EV3ColorSensor sensor;
	EV3LargeRegulatedMotor pusher;
	EV3LargeRegulatedMotor foerderband;
	
	public Work(Communicate comm) {
		commObj = comm;
		
		sensor = new EV3ColorSensor(SensorPort.S1);
		pusher = new EV3LargeRegulatedMotor(MotorPort.A);
		foerderband = new EV3LargeRegulatedMotor(MotorPort.D);	
			
    	while(!pusher.isStalled()) {
    		pusher.backward();
    	}
    	
    	pusher.setSpeed(10000);
    	pusher.rotate(250);
    	sensor.setFloodlight(true);
    	Delay.msDelay(500);
	}
	
	public void run() {
		while(true) {
			while(commObj.getBricksOnBelt() > 0) {
	    		foerderband.backward();
				int value = sensor.getColorID();
				
				if (value == 7) {
					for (int i = 0; i<50; i++) {
						try {
							Thread.sleep(2);
						}
						catch(InterruptedException e) {
							
						}
						value = sensor.getColorID();
						if (value != 7) {
							value = -1;
							break;
						}
					}
				}
	    		
	    		for (int i = 0; i<commObj.conArr.length;i++) {
	    			Container c = commObj.conArr[i];
	    			if (value == c.getColVal()) {
						System.out.println(value + " ");
	    				if (sort(c) == 1) {
	    					commObj.setType("remove");
	    					commObj.setValue(c.getPosition());
	    				};
	        			break;
	        		}
	    		}
			}
			try {
				Thread.sleep(100);
			}
			catch (InterruptedException e) {
				
			}
    	}
	}
	
	public int sort(Container c) {
		int time = 350 + c.getPosition() * 550;
		
		foerderband.stop();
		pusher.rotate(-250);
		foerderband.backward();
		Delay.msDelay(time);
		foerderband.stop();
		if(c.isRemoved()  || c.isFull()) {
			commObj.setIsPaused(true);
			while(c.isRemoved() || c.isFull()) {
				if(commObj.getType().equals("returned")) {
					commObj.returnContainer(commObj.getValue());
					commObj.reset();
				}
			}
			commObj.setIsPaused(false);
		}
		pusher.rotate(450);
		pusher.rotate(-200);
		commObj.removeBrick();
		c.addBrick();
		if (c.isFull()) {
			return 1;
		}
		return 0;
	
	}

}

//-------------------------------------------------------------------------------------------------------------------------

class Communicate extends Thread{

	//Container
	Container[] conArr = {
			new Container(0,Color.BLACK,0),
			new Container(1,Color.BLUE,1),
			new Container(2,Color.GREEN,2),
			new Container(3,Color.RED,3)};
	
	// state of the container
	// 0:none; 1:remove; 2:removed; 3:returned;
	private String type = "none";
	
	// which container
	// -1: none; value = position of container
	private int value = -1;
	
	//paused bc got brick for removed container = wait for return of container
	private boolean isPaused = false;
	
	//number of bricks on the conveyer belt
	private int bricksOnBelt = 0; 

	
	public Communicate() {
		
	}
	
	public synchronized String getType() {
		return this.type;
	}
	
	public synchronized int getValue() {
		return this.value;
	}
	
	public synchronized boolean getIsPaused() {
		return this.isPaused;
	}
	
	public synchronized void setType(String type) {
		this.type = type;
	}
	
	public synchronized void setValue(int value) {
		this.value = value;
	}
	
	public synchronized void setIsPaused(boolean state) {
		this.isPaused = state;
	}
	
	public synchronized void reset() {
		this.type = "none";
		this.value = -1;
	}
	
	public synchronized int getBricksOnBelt() {
		return this.bricksOnBelt;
	}
	
	public synchronized void addBrick() {
		this.bricksOnBelt++;
	}
	
	public synchronized void removeBrick() {
		if(this.bricksOnBelt > 0) {
			this.bricksOnBelt--;
		} else {
			System.out.print("Error: tried to remove from zero Bricks");
		}
	}
	
	public void removeContainer(int pos) {
		this.conArr[pos].removeContainer();
	}
	
	public void returnContainer(int pos) {
		this.conArr[pos].returnContainer();
	}
	
	public int getPositionOfFullContainer() {
		for(int i = 0; i<conArr.length; i++) {
			if (conArr[i].isFull()) {
				return conArr[i].getPosition();
			}
		}
		return -1;
	}
	
	public Container getContainerByPosition(int pos){
		for(int i = 0; i<conArr.length; i++) {
			if (conArr[i].getPosition() == pos) {
				return conArr[i];
			}
		}
		return new Container(-1,-1,-1);
	}
}

//-------------------------------------------------------------------------------------------------------------------------

class MqttCommunication extends Thread{
	
	Communicate commObj;
	MqttClient client;

	String broker = "tcp://141.46.233.97:1883"; //"tcp://test.mosquitto.org:1883"
	String topic = "robots/sorter";
	
	private boolean isPaused = false;
	
	public MqttCommunication(Communicate comm) throws MqttException{
		commObj = comm;
		
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
		    		JSONObject msg  = (JSONObject) new JSONParser().parse(new String(message.getPayload()).replace('\'','\"'));
		    	
		    	if (topic.equals(getTopic())) {
		    		String type = (String) msg.get("type");
		    		
		    		if (/*type == "removed" || */type.equals("returned") && msg.get("value") != null) {
			    		int pos;
		    			pos = ((Long) msg.get("value")).intValue();
			    		System.out.println(">r" + pos + " ");
		    			commObj.setType(type);
		    			commObj.setValue(pos);
		    		} else if (type.equals("information") && msg.get("value").equals("brick_delivered")) {
		    			System.out.print(">b");
		    			commObj.addBrick();
		    		}
		    		else {
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
	
	public void run() {
		while (true) {
			if (commObj.getType() == "remove") {
				JSONObject jsonObject = new JSONObject();
				jsonObject.put("type", commObj.getType());
				jsonObject.put("value", commObj.getValue());
				
				this.sendMsg("robots/container", jsonObject.toJSONString().getBytes());
				System.out.print("<r" + commObj.getValue() + " ");
				commObj.reset();
			}
			if (commObj.getIsPaused() && !getIsPaused()) {
				JSONObject jsonObject = new JSONObject();
				jsonObject.put("type", "pause");
				jsonObject.put("value", true);
				
				this.sendMsg("robots/collector", jsonObject.toJSONString().getBytes());
				System.out.print("<p ");
				setIsPaused(true);
			}
			if (!commObj.getIsPaused() && getIsPaused()) {
				JSONObject jsonObject = new JSONObject();
				jsonObject.put("type", "pause");
				jsonObject.put("value", false);
				
				this.sendMsg("robots/collector", jsonObject.toJSONString().getBytes());
				System.out.print("<u ");
				setIsPaused(false);
			}
		}
	}
	
	public boolean getIsPaused() {
		return this.isPaused;
	}
	
	public void setIsPaused(boolean state) {
		this.isPaused = state;
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
			commObj.reset();
		} catch (MqttException e) {
			System.out.println("msg delivery failed");
		}
	}
}

//-------------------------------------------------------------------------------------------------------------------------

class Container{
	int id;
	int colVal;
	int position = -1;
	int capacity = 3;
	int bricksStored;
	boolean removed = false;
	
	
	public Container(int id, int value, int pos) {
		this.id = id;
		this.colVal = value;
		this.position = pos;
	}
	
	public int getColVal() {
		return this.colVal;
	}
	
	public int getPosition() {
		return this.position;
	}
	
	public int getCapacity() {
		return this.capacity;
	}
	
	public int getBricksStored() {
		return this.bricksStored;
	}
	
	public void setBricksStored(int bricks) {
		this.bricksStored = bricks;
	}
	
	public void addBrick() {
		this.bricksStored++;
	}
	
	public boolean isFull() {
		return capacity == bricksStored;
	}
	
	public void emptyContainer() {
		this.bricksStored = 0;
	}
	
	public boolean isRemoved() {
		return this.removed;
	}
	
	public void removeContainer() {
		this.removed = true;
	}
	
	public void returnContainer() {
		this.emptyContainer();
		this.removed = false;
	}
}
