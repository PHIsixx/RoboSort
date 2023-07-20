import threading
import tkinter as tk
import time

import paho.mqtt.client as mqtt
import json

# MQTT-Broker Konfigurationbroker = "tcp://test.mosquitto.org" #"tcp://141.46.234.19"port = 1883topic = "robots/sorter"c_state = [True,True,True,True]

def mqtt_thread():
    # Verbinden mit dem Broker    client.connect(broker, port)
    while(not client.is_connected()):
        time.sleep(1000)
    print("connected")
    client.subscribe(topic)
    client.subscribe("robots/container")
    client.subscribe("robots/collector")
    client.subscribe("robots/sorter")
    client.on_message = on_message

    # Starten der MQTT-Schleife    client.loop_forever()

def on_message(client, userdata, msg):
    received_message = msg.payload.decode()
    log_message("", True)
    log_message("received: " + received_message, False)

def log_message(message, out):
    if out:
        out_console.config(state=tk.NORMAL)
        out_console.insert(tk.END, message + "\n")
        out_console.config(state=tk.DISABLED)
    else:
        in_console.config(state=tk.NORMAL)
        in_console.insert(tk.END, message + "\n")
        in_console.config(state=tk.DISABLED)

def container_action(c):
    if c_state[c]:
        data = {
            "type": 2,            "value": c
        }
    else:
        data = {
            "type": 3,            "value": c
        }

    message = json.dumps(data)
    send_mqtt_message(message)
    c_state[c] = not c_state[c]

def send_mqtt_message(message):
    client.publish(topic, message)
    client.subscribe(topic)
    log_message("sent: " + topic + " - " + message, True)
    log_message("", False)

def clear_log():
    in_console.config(state=tk.NORMAL)
    in_console.delete('1.0', tk.END)
    in_console.config(state=tk.DISABLED)

    out_console.config(state=tk.NORMAL)
    out_console.delete('1.0', tk.END)
    out_console.config(state=tk.DISABLED)


if __name__ == '__main__':
    # Erstellen des Hauptfensters    window = tk.Tk()
    window.title("Python GUI")

    # Erstellen der UI-Elemente    c0_button = tk.Button(window, text="C0 entfernt", command=lambda: container_action(0))
    c0_button.pack()

    c1_button = tk.Button(window, text="C1 entfernt", command=lambda: container_action(1))
    c1_button.pack()

    c2_button = tk.Button(window, text="C2 entfernt", command=lambda: container_action(2))
    c2_button.pack()

    c3_button = tk.Button(window, text="C3 entfernt", command=lambda: container_action(3))
    c3_button.pack()

    clear_button = tk.Button(window, text="Log löschen", command=clear_log)
    clear_button.pack()

    console_frame = tk.Frame(window)
    console_frame.pack()

    out_console = tk.Text(console_frame, state=tk.DISABLED)
    out_console.pack(side=tk.LEFT)

    in_console = tk.Text(console_frame, state=tk.DISABLED)
    in_console.pack(side=tk.LEFT)

    client = mqtt.Client()
    mqtt_thread = threading.Thread(target=mqtt_thread)
    mqtt_thread.start()

    # Starten der GUI-Schleife    window.mainloop()

    # Nach dem Schließen des Fensters die MQTT-Verbindung beenden    client.loop_stop()
    client.disconnect()
