package org.openbot.env;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import org.json.JSONObject;
import org.openbot.R;
import org.openbot.customview.AutoFitSurfaceGlView;
import org.openbot.customview.WebRTCSurfaceView;
import org.openbot.utils.CameraUtils;
import timber.log.Timber;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class PhoneController {
  private static final String TAG = "PhoneController";
  private static PhoneController _phoneController;
  private ConnectionSelector connectionSelector;
  private IVideoServer videoServer;
  private View view = null;

  public static PhoneController getInstance(Context context) {
    if (_phoneController == null) { // Check for the first time

      synchronized (PhoneController.class) { // Check for the second time.
        // if there is no instance available... create new one
        if (_phoneController == null) _phoneController = new PhoneController();
        _phoneController.init(context);
      }
    }

    return _phoneController;
  }

  class DataReceived implements IDataReceived {
    @Override
    public void dataReceived(String commandStr) {
      ControllerToBotEventBus.emitEvent(commandStr);
    }
  }

  private void init(Context context) {
    ControllerConfig.getInstance().init(context);

    videoServer =
        "RTSP".equals(ControllerConfig.getInstance().getVideoServerType())
            ? new RtspServer()
            : new WebRtcServer();

    videoServer.init(context);
    videoServer.setCanStart(true);

    this.connectionSelector = ConnectionSelector.getInstance(context);
    connectionSelector.getConnection().setDataCallback(new DataReceived());

    android.util.Size resolution =
        CameraUtils.getClosestCameraResolution(context, new android.util.Size(640, 360));
    videoServer.setResolution(resolution.getWidth(), resolution.getHeight());

    handleBotEvents();
    createAndSetView(context);
    monitorConnection();
  }

  private void createAndSetView(Context context) {
    if (videoServer instanceof WebRtcServer) {
      view = new org.openbot.customview.WebRTCSurfaceView(context);
    } else if (videoServer instanceof RtspServer) {
      view = new org.openbot.customview.AutoFitSurfaceGlView(context);
    }
    if (view != null) {
      addVideoView(view, context);
    }
  }

  private void addVideoView(View videoView, Context context) {
    ViewGroup viewGroup = (ViewGroup) ((Activity) context).getWindow().getDecorView();

    ViewGroup.LayoutParams layoutParams =
        new ViewGroup.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
    videoView.setLayoutParams(layoutParams);
    videoView.setId(R.id.video_window);
    videoView.setAlpha(0f);
    viewGroup.addView(videoView, 0); // send to back

    if (videoView instanceof WebRTCSurfaceView) {
      videoServer.setView((WebRTCSurfaceView) videoView);
    } else if (videoView instanceof AutoFitSurfaceGlView) {
      videoServer.setView((AutoFitSurfaceGlView) videoView);
    }
  }

  public void connect(Context context) {
    ILocalConnection connection = connectionSelector.getConnection();

    if (!connection.isConnected()) {
      connection.init(context);
      connection.connect(context);
    } else {
      connection.start();
    }
  }

  public void disconnect() {
    connectionSelector.getConnection().stop();
  }

  public void send(JSONObject info) {
    connectionSelector.getConnection().sendMessage(info.toString());
  }

  public boolean isConnected() {
    return connectionSelector.getConnection().isConnected();
  }

  private void handleBotEvents() {
    BotToControllerEventBus.subscribe(
        this::send, error -> Timber.d("Error occurred in BotToControllerEventBus: %s", error));
  }

  private void monitorConnection() {
    ControllerToBotEventBus.subscribe(
        this.getClass().getSimpleName(),
        event -> {
          switch (event.getString("command")) {
            case "CONNECTED":
              videoServer.setConnected(true);
              break;

            case "DISCONNECTED":
              videoServer.setConnected(false);
              break;
          }
        },
        error -> {
          Log.d(null, "Error occurred in monitorConnection: " + error);
        },
        event ->
            event.has("command")
                && ("CONNECTED".equals(event.getString("command"))
                    || "DISCONNECTED".equals(event.getString("command"))) // filter everything else
        );
  }

  private PhoneController() {
    if (_phoneController != null) {
      throw new RuntimeException(
          "Use getInstance() method to get the single instance of this class.");
    }
  }
}
