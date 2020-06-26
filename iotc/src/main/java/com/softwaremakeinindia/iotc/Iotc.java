package com.softwaremakeinindia.iotc;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.HttpHeaderParser;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static android.content.ContentValues.TAG;

public class Iotc {
    static String password = "";
    static String username = "";
    static String appname = "";
    static ArrayList<String> devices = new ArrayList<String>();
    static String userkey = "";

    static MqttAndroidClient iotclient;

    public interface Options{
        void onConnect(String appName, String[] devices);
        void onMessageReceive(String deviceId, String msg);
        void onAppKeyError();
    }



    public static void connect(final Context c, final String key, final Options... mcb){
        String url = "https://iot.softwaremakeinindia.com/iot/";
        userkey = key;

        RequestQueue requestQueue = Volley.newRequestQueue(c);
        final StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {

                        JSONObject jsonobject=null;
                        try {
                            jsonobject = new JSONObject(response);
                            System.out.println(jsonobject);
                            username = jsonobject.getString("username");
                            password = jsonobject.getString("password");
                            appname = jsonobject.getString("name");

                            JSONArray raw_devices = jsonobject.getJSONArray("devices");
                            devices.clear();
                            for (int i =0; i<raw_devices.length(); i++){
                                JSONObject temp = new JSONObject(raw_devices.get(i).toString());
                                devices.add(temp.getString("name"));
                            }

                            if (mcb.length>0)
                                mqttConnect(c, key, mcb[0]);
                            else
                                mqttConnect(c, key,null);

                        } catch (JSONException e) {
                            mcb[0].onAppKeyError();
                            e.printStackTrace();
                        }

                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.d("error", "onErrorResponse: error "+error.toString());
                        mcb[0].onAppKeyError();
                    }
                }
        ) {
            @Override
            protected Map<String, String> getParams(){
                Map<String, String> params = new HashMap<String, String>();
                params.put("key",key);
                return params;
            }

        };
        requestQueue.add(stringRequest);

    }

    public static void mqttConnect(final Context c, String key, final Options mcb){


        String clientId = MqttClient.generateClientId();
        final MqttAndroidClient client =
                new MqttAndroidClient(c, "ssl://iot.softwaremakeinindia.com:8883",
                        clientId);
        iotclient = client;
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {

            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {

                if (mcb != null){
                    mcb.onMessageReceive(topic.split("/")[1], message.toString());
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }


        });

        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setUserName(username);
            options.setPassword(password.toCharArray());

            IMqttToken token = client.connect(options);
            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // We are connected
                    Log.d(TAG, "onSuccess is here");

                    String[] tempDevices = new String[devices.size()];
                    for (int i=0; i<tempDevices.length; i++){
                        tempDevices[i] = devices.get(i);
                    }
                    mcb.onConnect(appname, tempDevices);

                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    // Something went wrong e.g. connection timeout or firewall problems
                    Log.d(TAG, "onFailure");

                }

            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public static void send(String deviceId, String msg){
        String topic = userkey+"/"+deviceId;
        byte[] encodedPayload = new byte[0];
        try {
            encodedPayload = msg.getBytes("UTF-8");
            MqttMessage message = new MqttMessage(encodedPayload);
            if (isConnected()) {
                iotclient.publish(topic, message);
            }
        } catch (UnsupportedEncodingException | MqttException e) {
            e.printStackTrace();
        }
    }

    static boolean isConnected(){
        try {
            if (iotclient.isConnected()){
                return true;
            }else{
                return false;
            }
        }catch (Exception e){
            return false;
        }
    }

    public static void subscribe(String deviceId){
        String topic = userkey+"/"+deviceId;
        int qos = 0;
        try {
            IMqttToken subToken = iotclient.subscribe(topic, qos);
            subToken.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // The message was published
                    Log.d(TAG, "onSuccess: subscribe");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken,
                                      Throwable exception) {
                    Log.d(TAG, "onFailure: subscribe error");
                    // The subscription could not be performed, maybe the user was not
                    // authorized to subscribe on the specified topic e.g. using wildcards

                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public static void disConnect(){
        System.out.println("From Lib");
//        iotclient.unregisterResources();
        iotclient.close();
    }

}
