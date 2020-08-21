package our.android;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.widget.Toast;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

/*
    bindService(new Intent(this, AndroidWebSocketService.class), serviceConnection, BIND_AUTO_CREATE);

    AndroidWebSocketService.WS ws;
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            String endPoint = "ws://192.168.1.231:8887";
            ws = ((AndroidWebSocketService.LocalBinder) service).newWS(defaultWSL, endPoint);
            ws.open();
            ws.send("data:,log - opened");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (ws != null) {
                ws.close();
                ws = null;
            }
        }
    };
    
    <service android:name=".AndroidWebSocketService" android:enabled="true" android:exported="true" />
    
    implementation 'com.squareup.okhttp3:okhttp:3.12.12'
    
*/
public class AndroidWebSocketService extends Service {
    private final IBinder binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class LocalBinder extends Binder {
        private AndroidWebSocketService.WS ws;

        WS newWS(WebSocketListener wsl) {
            ws = new WS(wsl);
            return ws;
        }

        WS newWS(WebSocketListener wsl, String endPoint) {
            ws = new WS(wsl, endPoint);
            return ws;
        }

        WS getWS() {
            if (ws == null) {
                ws = new WS(defaultWSL);
            }
            return ws;
        }

        WS getWS(String endPoint) {
            if (ws == null) {
                ws = new WS(defaultWSL, endPoint);
            }
            return ws;
        }

        //  WebSocketListener示例
        private WebSocketListener defaultWSL = new WebSocketListener() {
            boolean reconnecting;

            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                super.onOpen(webSocket, response);
                System.out.println("WebSocket onOpen");
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                super.onMessage(webSocket, text);
                System.out.println("WebSocket onMessage - " + text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                super.onMessage(webSocket, bytes);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                super.onClosing(webSocket, code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                super.onClosed(webSocket, code, reason);
                System.out.println("WebSocket onClosed - " + reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                super.onFailure(webSocket, t, response);
                //  拔网线不会触发该回调
                System.out.println("WebSocket onFailure - " + t.getMessage());
                //System.out.println("webSocket - " + webSocket);

                if (!reconnecting) {
                    reconnecting = true;
                    new Thread(() -> {
                        try {
                            Thread.sleep(30 * 1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        reconnecting = false;
                        if (ws != null) {
                            ws.open();
                        }
                        System.out.println("reconnect over.");

                        new Handler(getMainLooper()).post(() -> Toast.makeText(getApplicationContext(), "WebSocket reconnect...", Toast.LENGTH_SHORT).show());
                        //  MainActivity.this.runOnUiThread(() -> Toast.makeText(MainActivity.this, "WebSocket reconnect...", Toast.LENGTH_SHORT).show());
                    }).start();
                }
            }
        };

    }

    public class WS {
        private String endPoint = "ws://127.0.0.1:8887";
        private final Request request = new Request.Builder().url(endPoint).build();
        private final OkHttpClient client = new OkHttpClient.Builder().pingInterval(1, TimeUnit.MINUTES).build();
        private WebSocket webSocket;
        private WebSocketListener wsWSL;

        public WS(WebSocketListener wsl) {
            this.wsWSL = wsl;
        }

        public WS(WebSocketListener wsl, String endPoint) {
            this.wsWSL = wsl;
            this.endPoint = endPoint;
        }

        public void setListener(WebSocketListener wsl) {
            this.wsWSL = wsl;
        }

        public void open() {
            close();    //  关闭老ws实例
            webSocket = client.newWebSocket(request, this.wsWSL);
            System.out.println("webSocket - " + webSocket);
        }

        public void send(String msg) {
            System.out.println("WS send");
            if (webSocket != null) {
                boolean isOK = webSocket.send(msg);
                System.out.println("send return " + isOK);
                if (isOK == false) {
                    //open();
                }
            }
        }

        public void close() {
            System.out.println("WS close - " + webSocket);
            if (webSocket != null) {
                boolean r = webSocket.close(1000, "reason");
                System.out.println("close return - " + r);
                webSocket = null;
            }
        }
    }
}
