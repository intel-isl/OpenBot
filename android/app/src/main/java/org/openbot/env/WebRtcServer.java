package org.openbot.env;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.ToneGenerator;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.core.content.ContextCompat;
import com.pedro.rtplibrary.view.OpenGlView;
import java.util.ArrayList;
import org.json.JSONException;
import org.json.JSONObject;
import org.openbot.utils.AndGate;
import org.openbot.utils.ConnectionUtils;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CandidatePairChangeEvent;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import timber.log.Timber;

/*
This class initiates a WebRTC call to the controller, by sending an WebRTC "offer"
to the controller, providing its A/V capabilities. It then waits for an "answer" with
controller's capabilities. The two sides then exchange ICE candidates until a suitable
common capabilities are found, and then media is streamed from this class to the controller.

Note that the media is streamed only one way from this class to the controller.

WebRTC does not specify signaling protocol. Usually, a separate signaling server is used
witch mediates between the two WebRTC peers, and communication from and to this server is
carried over WebSocket. However, we already have a communication channel between the peers
(NetworkServiceConnection) so we are using it instead. No separate signaling server is required.

It is possible in the future to factor out signaling into a separate class and provide
various signalling types, such as to separate signalling server.
 */
public class WebRtcServer implements IVideoServer {
  private final String TAG = "WebRtcPeer";
  private SurfaceViewRenderer view;
  private Size resolution = new Size(640, 360);

  public static final String VIDEO_TRACK_ID = "ARDAMSv0";
  public static final int VIDEO_RESOLUTION_WIDTH = 640;
  public static final int VIDEO_RESOLUTION_HEIGHT = 360;
  public static final int FPS = 30;

  // WebRTC-specific
  private EglBase rootEglBase;
  private PeerConnectionFactory factory;
  private VideoTrack videoTrackFromCamera;
  MediaConstraints audioConstraints;
  AudioSource audioSource;
  AudioTrack localAudioTrack;
  SurfaceTextureHelper surfaceTextureHelper;
  private PeerConnection peerConnection;
  MediaStream mediaStream;

  private AndGate andGate;
  private Context context;

  private final CameraControlHandler cameraControlHandler = new CameraControlHandler();
  private final SignalingHandler signalingHandler = new SignalingHandler();

  public WebRtcServer() {}

  // IVideoServer Interface
  @Override
  public void init(Context context) {
    this.context = context;

    andGate = new AndGate(() -> startServer(), () -> stopServer());
    andGate.addCondition("connected");
    andGate.addCondition("view set");
    andGate.addCondition("camera permission");
    andGate.addCondition("resolution set");
    andGate.addCondition("can start");

    int camera = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA);
    andGate.set("camera permission", camera == PackageManager.PERMISSION_GRANTED);

    rootEglBase = EglBase.create();

    cameraControlHandler.handleCameraControlEvents();
    signalingHandler.handleControllerWebRtcEvents();
  }

  @Override
  public boolean isRunning() {
    return false;
  }

  @Override
  public void setCanStart(boolean canStart) {
    andGate.set("can start", canStart);
  }

  @Override
  public void startClient() {
    BotToControllerEventBus.emitEvent(ConnectionUtils.createStatus("VIDEO_PROTOCOL", "WEBRTC"));
    sendServerUrl();
    BotToControllerEventBus.emitEvent(ConnectionUtils.createStatus("VIDEO_COMMAND", "START"));
    BotToControllerEventBus.emitEvent(ConnectionUtils.createStatus("TOGGLE_MIRROR", true));
  }

  @Override
  public void sendServerUrl() {
    BotToControllerEventBus.emitEvent(ConnectionUtils.createStatus("VIDEO_SERVER_URL", ""));
  }

  @Override
  public void sendVideoStoppedStatus() {
    BotToControllerEventBus.emitEvent(ConnectionUtils.createStatus("VIDEO_COMMAND", "STOP"));
  }

  @Override
  public void setView(SurfaceView view) {}

  @Override
  public void setView(TextureView view) {}

  @Override
  public void setView(SurfaceViewRenderer view) {
    this.view = view;
    this.view.setEnabled(false);
    andGate.set("view set", true);
  }

  @Override
  public void setView(OpenGlView view) {}

  @Override
  public void setConnected(boolean connected) {
    andGate.set("connected", connected);

    int camera = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA);
    andGate.set("camera permission", camera == PackageManager.PERMISSION_GRANTED);
  }

  @Override
  public void setResolution(int w, int h) {
    resolution = new Size(w, h);
    andGate.set("resolution set", true);
  }
  // end Interface

  // local methods
  private void startServer() {

    initializeSurfaceViews();
    initializePeerConnectionFactory();
    createVideoTrackFromCameraAndShowIt();
    initializePeerConnections();

    startStreamingVideo();
    doCall();
    startClient();
  }

  private void doAnswer() {
    peerConnection.createAnswer(
        new SimpleSdpObserver() {
          @Override
          public void onCreateSuccess(SessionDescription sessionDescription) {
            peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
            JSONObject message = new JSONObject();
            try {
              message.put("type", "answer");
              message.put("sdp", sessionDescription.description);
              sendMessage(message);
            } catch (JSONException e) {
              e.printStackTrace();
            }
          }
        },
        new MediaConstraints());
  }

  private void startStreamingVideo() {
    mediaStream = factory.createLocalMediaStream("ARDAMS");
    mediaStream.addTrack(videoTrackFromCamera);
    mediaStream.addTrack(localAudioTrack);
    peerConnection.addStream(mediaStream);

    boolean isMute = ControllerConfig.getInstance().isMute();
    if (isMute) {
      cameraControlHandler.disableAudio();
    }
  }

  private void stopStreamingVideo() {
    peerConnection.removeStream(mediaStream);
  }

  private void stopServer() {
    mediaStream.removeTrack(videoTrackFromCamera);
    mediaStream.removeTrack(localAudioTrack);
    view.release();
    stopClient();
  }

  private void stopClient() {
    BotToControllerEventBus.emitEvent(ConnectionUtils.createStatus("VIDEO_COMMAND", "STOP"));
  }

  private void doCall() {
    MediaConstraints sdpMediaConstraints = new MediaConstraints();

    sdpMediaConstraints.mandatory.add(
        new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"));
    sdpMediaConstraints.mandatory.add(
        new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"));

    peerConnection.createOffer(
        new SimpleSdpObserver() {
          @Override
          public void onCreateSuccess(SessionDescription sessionDescription) {
            peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
            JSONObject message = new JSONObject();
            try {
              message.put("type", "offer");
              message.put("sdp", sessionDescription.description);

              sendMessage(message);
            } catch (JSONException e) {
              e.printStackTrace();
            }
          }
        },
        sdpMediaConstraints);
  }

  private void initializePeerConnections() {
    peerConnection = createPeerConnection(factory);
  }

  private PeerConnection createPeerConnection(PeerConnectionFactory factory) {
    ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<>();

    PeerConnection.IceServer stunServer =
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer();
    iceServers.add(stunServer);

    PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
    MediaConstraints pcConstraints = new MediaConstraints();

    PeerConnection.Observer pcObserver =
        new PeerConnection.Observer() {
          @Override
          public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.d(TAG, "onSignalingChange: ");
          }

          @Override
          public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.d(TAG, "onIceConnectionChange: ");
          }

          @Override
          public void onStandardizedIceConnectionChange(
              PeerConnection.IceConnectionState newState) {}

          @Override
          public void onConnectionChange(PeerConnection.PeerConnectionState newState) {}

          @Override
          public void onIceConnectionReceivingChange(boolean b) {
            Log.d(TAG, "onIceConnectionReceivingChange: ");
          }

          @Override
          public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            Log.d(TAG, "onIceGatheringChange: ");
          }

          @Override
          public void onIceCandidate(IceCandidate iceCandidate) {
            Log.d(TAG, "onIceCandidate: ");
            JSONObject message = new JSONObject();

            try {
              message.put("type", "candidate");
              message.put("label", iceCandidate.sdpMLineIndex);
              message.put("id", iceCandidate.sdpMid);
              message.put("candidate", iceCandidate.sdp);

              Log.d(TAG, "onIceCandidate: sending candidate " + message);
              sendMessage(message);
            } catch (JSONException e) {
              e.printStackTrace();
            }
          }

          @Override
          public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
            Log.d(TAG, "onIceCandidatesRemoved: ");
          }

          @Override
          public void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {}

          @Override
          public void onAddStream(MediaStream mediaStream) {
            Log.d(TAG, "onAddStream: " + mediaStream.videoTracks.size());
            VideoTrack remoteVideoTrack = mediaStream.videoTracks.get(0);
            AudioTrack remoteAudioTrack = mediaStream.audioTracks.get(0);
            remoteAudioTrack.setEnabled(true);
            remoteVideoTrack.setEnabled(true);
            remoteVideoTrack.addSink(view);
          }

          @Override
          public void onRemoveStream(MediaStream mediaStream) {
            Log.d(TAG, "onRemoveStream: ");
          }

          @Override
          public void onDataChannel(DataChannel dataChannel) {
            Log.d(TAG, "onDataChannel: ");
          }

          @Override
          public void onRenegotiationNeeded() {
            Log.d(TAG, "onRenegotiationNeeded: ");
          }

          @Override
          public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {}

          @Override
          public void onTrack(RtpTransceiver transceiver) {}
        };

    return factory.createPeerConnection(rtcConfig, pcConstraints, pcObserver);
  }

  private void sendMessage(JSONObject message) {
    BotToControllerEventBus.emitEvent(ConnectionUtils.createStatus("WEB_RTC_EVENT", message));
  }

  private void createVideoTrackFromCameraAndShowIt() {
    audioConstraints = new MediaConstraints();
    VideoCapturer videoCapturer = createVideoCapturer();
    VideoSource videoSource = factory.createVideoSource(videoCapturer.isScreencast());

    surfaceTextureHelper =
        SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());
    videoCapturer.initialize(
        surfaceTextureHelper,
        context /*getApplicationContext()*/,
        videoSource.getCapturerObserver());

    videoCapturer.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, FPS);

    videoTrackFromCamera = factory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
    videoTrackFromCamera.setEnabled(true);
    videoTrackFromCamera.addSink(view);

    // create an AudioSource instance
    audioSource = factory.createAudioSource(audioConstraints);
    localAudioTrack = factory.createAudioTrack("101", audioSource);
  }

  private void initializePeerConnectionFactory() {
    VideoEncoderFactory encoderFactory =
        new DefaultVideoEncoderFactory(rootEglBase.getEglBaseContext(), true, true);
    VideoDecoderFactory decoderFactory =
        new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());

    PeerConnectionFactory.InitializationOptions initializationOptions =
        PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions();
    PeerConnectionFactory.initialize(initializationOptions);

    factory =
        PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory();
  }

  private void initializeSurfaceViews() {
    view.init(rootEglBase.getEglBaseContext(), null);
    view.setEnableHardwareScaler(true);
  }

  private VideoCapturer createVideoCapturer() {
    VideoCapturer videoCapturer;
    if (useCamera2()) {
      videoCapturer = createCameraCapturer(new Camera2Enumerator(context));
    } else {
      videoCapturer = createCameraCapturer(new Camera1Enumerator(true));
    }
    return videoCapturer;
  }

  private boolean useCamera2() {
    return Camera2Enumerator.isSupported(context);
  }

  private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
    final String[] deviceNames = enumerator.getDeviceNames();

    for (String deviceName : deviceNames) {
      if (enumerator.isBackFacing(deviceName)) {
        VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
        if (videoCapturer != null) {
          return videoCapturer;
        }
      }
    }

    return null;
  }

  // Other commands
  class CameraControlHandler {

    private void disableAudio() {
      if (mediaStream.audioTracks.size() > 0) {
        AudioTrack audio = mediaStream.audioTracks.get(0);
        audio.setEnabled(false);

        // inform the controller of current state
        BotToControllerEventBus.emitEvent(ConnectionUtils.createStatus("TOGGLE_SOUND", false));
      }
    }

    private void handleCameraControlEvents() {
      ControllerConfig config = ControllerConfig.getInstance();
      ControllerToBotEventBus.subscribe(
          this.getClass().getSimpleName(),
          event -> {
            String commandType = event.getString("command");

            if (mediaStream.audioTracks.size() > 0) {
              AudioTrack audio = mediaStream.audioTracks.get(0);
              audio.setEnabled(!audio.enabled());
            }
          },
          error -> Log.d(null, "Error occurred in ControllerToBotEventBus: " + error),
          event -> event.has("command") && "TOGGLE_SOUND".equals(event.getString("command")));
    }
  }

  // Utils
  private void beep() {
    final ToneGenerator tg = new ToneGenerator(6, 100);
    tg.startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE);
  }

  class SignalingHandler {
    void handleControllerWebRtcEvents() {
      ControllerToBotEventBus.subscribe(
          "WEB_RTC_COMMANDS",
          event -> {
            String commandType = "";
            JSONObject webRtcEvent = event.getJSONObject("webrtc_event");
            String type = webRtcEvent.getString("type");
            switch (type) {
              case "offer":
                Timber.d("connectToSignallingServer: received an offer $isInitiator $isStarted");
                peerConnection.setRemoteDescription(
                    new SimpleSdpObserver(),
                    new SessionDescription(
                        SessionDescription.Type.OFFER, webRtcEvent.getString("sdp")));
                doAnswer();
                break;

              case "answer":
                String remoteDescr = webRtcEvent.getString("sdp");
                Timber.i("Got remote description %s", remoteDescr);
                peerConnection.setRemoteDescription(
                    new SimpleSdpObserver(),
                    new SessionDescription(SessionDescription.Type.ANSWER, remoteDescr));
                break;

              case "candidate":
                IceCandidate candidate =
                    new IceCandidate(
                        webRtcEvent.getString("id"),
                        webRtcEvent.getInt("label"),
                        webRtcEvent.getString("candidate"));
                peerConnection.addIceCandidate(candidate);
                break;
            }
          },
          error -> Log.d(TAG, "Error occurred in handleControllerWebRtcEvents: %s", error),
          commandJsn ->
              commandJsn.has("webrtc_event") // filter out all non "webrtc_event" messages.
          );
    }

    public void shutDown() {
      // Not used
      ControllerToBotEventBus.unsubscribe("WEB_RTC_COMMANDS");
    }
  }
}
