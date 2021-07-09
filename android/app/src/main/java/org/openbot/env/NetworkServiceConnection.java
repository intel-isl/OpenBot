package org.openbot.env;

import static timber.log.Timber.*;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.openbot.utils.ConnectionUtils;
import timber.log.Timber;

public class NetworkServiceConnection implements ILocalConnection {

  private static final String TAG = "NetworkServiceConn";
  private Context context;

  private String SERVICE_NAME_CONTROLLER = "OPEN_BOT_CONTROLLER";
  private String MY_SERVICE_NAME = "OPEN_BOT";
  private String ALL_SERVICE_TYPES = "_services._dns-sd._udp";
  private String SERVICE_TYPE = "_openbot._tcp.";
  private int port = 19400;

  private InetAddress hostAddress;
  private int hostPort;
  private NsdManager mNsdManager;
  private IDataReceived dataReceivedCallback;
  private SocketHandler socketHandler;
  private BlockingQueue<String> messageQueue = new ArrayBlockingQueue<>(25);
  private boolean stopped = true;

  @Override
  public void init(Context context) {
    mNsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
    socketHandler = new SocketHandler(messageQueue);
  }

  @Override
  public void setDataCallback(IDataReceived dataCallback) {
    this.dataReceivedCallback = dataCallback;
  }

  @Override
  public void connect(Context context) {
    this.context = context;
    start();
    runConnection();
  }

  @Override
  public void disconnect(Context context) {
    stop();

    if (socketHandler == null) {
      return;
    }
    socketHandler.close();
    try {
      mNsdManager.stopServiceDiscovery(mDiscoveryListener);
    } catch (IllegalArgumentException e) {
      Log.d(TAG, "disconnect: Already discovering: " + e);
    }
  }

  @Override
  public void stop() {
    stopped = true;
    BotToControllerEventBus.emitEvent(ConnectionUtils.createStatus("CONNECTION_ACTIVE", false));
  }

  @Override
  public void start() {

    stopped = false;
    BotToControllerEventBus.emitEvent(ConnectionUtils.createStatus("CONNECTION_ACTIVE", true));
  }

  @Override
  public boolean isVideoCapable() {
    return true;
  }

  @Override
  public boolean isConnected() {
    return socketHandler != null && socketHandler.isConnected();
  }

  @Override
  public void sendMessage(String message) {
    if (socketHandler != null) {
      socketHandler.put(message);
    }
  }
  // end of interface

  private void runConnection() {
    try {
      mNsdManager.discoverServices(
          /*ALL_SERVICE_TYPES*/ SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener);
    } catch (IllegalArgumentException e) {
      Log.d(TAG, "runConnection: Already discovering: " + e);
    }
  }

  NsdManager.DiscoveryListener mDiscoveryListener =
      new NsdManager.DiscoveryListener() {
        // Called as soon as service discovery begins.
        @Override
        public void onDiscoveryStarted(String regType) {
          d("Service discovery started");
        }

        @Override
        public void onServiceFound(NsdServiceInfo service) {
          // A service was found! Do something with it.
          d("Service discovery success : %s", service);
          d("Host = %s", service.getServiceName());
          d("port = %s", String.valueOf(service.getPort()));

          try {
            if (service.getServiceType().equals(SERVICE_TYPE)
                && service.getServiceName().equals(SERVICE_NAME_CONTROLLER)) {
              mNsdManager.resolveService(service, mResolveListener);
            } else if (service.getServiceName().equals(MY_SERVICE_NAME)) {
              Log.d(TAG, "Same machine: " + MY_SERVICE_NAME);
            }
          } catch (java.lang.IllegalArgumentException e) {
            Log.d(TAG, "Got exception: " + e);
          }
        }

        @Override
        public void onServiceLost(NsdServiceInfo service) {
          // When the network service is no longer available.
          // Internal bookkeeping code goes here.
          ((Activity) context)
              .runOnUiThread(
                  () -> {
                    ControllerToBotEventBus.emitEvent("{command: \"DISCONNECTED\"}");
                  });
        }

        @Override
        public void onDiscoveryStopped(String serviceType) {
          i("Discovery stopped: %s", serviceType);
        }

        @SuppressLint("TimberArgCount")
        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {
          e(TAG, "Discovery failed: Error code: %s", errorCode);
          mNsdManager.stopServiceDiscovery(this);

          // re-try connecting
          runConnection();
        }

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {
          e("Discovery failed: Error code:%s", errorCode);
          mNsdManager.stopServiceDiscovery(this);
        }
      };

  NsdManager.ResolveListener mResolveListener =
      new NsdManager.ResolveListener() {

        @Override
        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
          // Called when the resolve fails. Use the error code to debug.
          Timber.e("Resolve failed %s", errorCode);
          Timber.e("serivce = %s", serviceInfo);

          // re-try connecting
          runConnection();
        }

        @Override
        public void onServiceResolved(NsdServiceInfo serviceInfo) {
          Timber.d("Resolve Succeeded. %s", serviceInfo);
          if (serviceInfo.getServiceName().equals(MY_SERVICE_NAME)) {
            Timber.d("Same IP.");
            return;
          }

          // Obtain port and IP
          int port = serviceInfo.getPort();
          String host = serviceInfo.getHost().getHostAddress();
          Timber.d("PORT: " + port + ", address: " + host);

          ((Activity) context)
              .runOnUiThread(
                  () -> {
                    ControllerToBotEventBus.emitEvent("{command: \"CONNECTED\"}");
                  });

          new Thread("Receiver Thread") {
            public void run() {
              SocketHandler.ClientInfo clientInfo = socketHandler.connect(host, port);
              if (clientInfo == null) {
                Timber.d("Could not get a connection");
                return;
              }
              startReceiver(socketHandler, clientInfo.reader);
              startSender(socketHandler, clientInfo.writer);
            }
          }.start();
        }
      };

  private void startReceiver(SocketHandler socketHandler, Scanner reader) {
    new Thread("startReceiver Thread") {
      public void run() {
        socketHandler.runReceiver(reader);
      }
    }.start();
  }

  private void startSender(SocketHandler socketHandler, OutputStream writer) {
    new Thread("startSender Thread") {
      public void run() {
        try {
          socketHandler.runSender(writer);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }.start();
  }

  class SocketHandler {
    private BlockingQueue<String> messageQueue;
    private Socket client;

    boolean isConnected() {
      return client != null && !client.isClosed();
    }

    SocketHandler(BlockingQueue<String> messageQueue) {
      this.messageQueue = messageQueue;
    }

    class ClientInfo {
      Scanner reader;
      OutputStream writer;

      ClientInfo(Scanner reader, OutputStream writer) {
        this.reader = reader;
        this.writer = writer;
      }
    }

    ClientInfo connect(String host, int port) {
      ClientInfo clientInfo;

      try {
        client = new Socket(host, port);
        clientInfo =
            new ClientInfo(
                new Scanner(new DataInputStream(new BufferedInputStream(client.getInputStream()))),
                client.getOutputStream());
      } catch (Exception e) {
        return null;
      }

      return clientInfo;
    }

    void runReceiver(Scanner reader) {
      try {
        while (true) {
          String msg = reader.nextLine().trim();

          if (!stopped) {
            ((Activity) context).runOnUiThread(() -> dataReceivedCallback.dataReceived(msg));
          }
        }
      } catch (Exception e) {
        close();
      }
    }

    void put(String message) {
      try {
        this.messageQueue.put(message);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }

    @SuppressLint("TimberArgCount")
    void runSender(OutputStream writer) {
      while (true) {
        try {
          String message = messageQueue.take();
          i(TAG, "queue capacity: " + messageQueue.remainingCapacity());
          writer.write((message + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (InterruptedException | IOException e) {
          i(TAG, "runSender got exception: " + e);
          close();

          // reconnect again
          if (isConnected()) {
            runConnection();
          }
          break;
        }
      }
    }

    void close() {
      try {
        if (client == null || client.isClosed()) {
          return;
        }
        client.close();

        ((Activity) context)
            .runOnUiThread(
                () -> {
                  ControllerToBotEventBus.emitEvent("{command: \"DISCONNECTED\"}");
                });
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }
}
