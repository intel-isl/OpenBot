package org.openbot.logging;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.ImageProxy;
import androidx.navigation.Navigation;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.openbot.R;
import org.openbot.common.CameraFragment;
import org.openbot.databinding.FragmentLoggerBinding;
import org.openbot.env.BotToControllerEventBus;
import org.openbot.env.ImageUtils;
import org.openbot.server.ServerCommunication;
import org.openbot.server.ServerListener;
import org.openbot.tflite.Model;
import org.openbot.utils.ConnectionUtils;
import org.openbot.utils.Constants;
import org.openbot.utils.Enums;
import org.openbot.utils.PermissionUtils;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.commons.FileUtils;
import timber.log.Timber;

public class LoggerFragment extends CameraFragment implements ServerListener {

  private FragmentLoggerBinding binding;
  private Handler handler;
  private HandlerThread handlerThread;
  private Intent intentSensorService;
  private ServerCommunication serverCommunication;
  protected String logFolder;

  protected boolean loggingEnabled;
  private Matrix frameToCropTransform;
  private Bitmap croppedBitmap;
  private int sensorOrientation;
  private RectF cropRect;
  private boolean maintainAspectRatio;

  @Override
  public View onCreateView(
      @NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    // Inflate the layout for this fragment
    binding = FragmentLoggerBinding.inflate(inflater, container, false);

    return inflateFragment(binding, inflater, container);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    binding.controllerContainer.speedInfo.setText(getString(R.string.speedInfo, "---,---"));

    intentSensorService = new Intent(requireActivity(), SensorService.class);
    setSpeedMode(Enums.SpeedMode.getByID(preferencesManager.getSpeedMode()));
    setControlMode(Enums.ControlMode.getByID(preferencesManager.getControlMode()));
    setDriveMode(Enums.DriveMode.getByID(preferencesManager.getDriveMode()));

    binding.sensorDataButton.setOnClickListener(
        v -> {
          SensorsDialog sensorsDialog = new SensorsDialog();
          sensorsDialog.show(getChildFragmentManager(), sensorsDialog.getTag());
        });
    binding.controllerContainer.controlMode.setOnClickListener(
        v -> {
          Enums.ControlMode controlMode =
              Enums.ControlMode.getByID(preferencesManager.getControlMode());
          if (controlMode != null) setControlMode(Enums.switchControlMode(controlMode));
        });
    binding.controllerContainer.driveMode.setOnClickListener(
        v -> setDriveMode(Enums.switchDriveMode(vehicle.getDriveMode())));

    binding.controllerContainer.speedMode.setOnClickListener(
        v ->
            setSpeedMode(
                Enums.toggleSpeed(
                    Enums.Direction.CYCLIC.getValue(),
                    Enums.SpeedMode.getByID(preferencesManager.getSpeedMode()))));

    binding.loggerSwitch.setOnCheckedChangeListener(
        (buttonView, isChecked) -> setIsLoggingActive(isChecked));

    binding.cameraToggle.setOnClickListener(v -> toggleCamera());

    List<String> models =
        masterList.stream()
            .filter(f -> f.pathType != Model.PATH_TYPE.URL)
            .map(f -> org.openbot.utils.FileUtils.nameWithoutExtension(f.name))
            .collect(Collectors.toList());

    ArrayAdapter<String> modelAdapter =
        new ArrayAdapter<>(requireContext(), R.layout.spinner_item, models);
    modelAdapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
    binding.modelSpinner.setAdapter(modelAdapter);
    binding.modelSpinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            String selected = parent.getItemAtPosition(position).toString();
            masterList.stream()
                .filter(f -> f.name.contains(selected))
                .findFirst()
                .ifPresent(f -> updateCropImageInfo(f));
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {}
        });
    binding.resolutionSpinner.setOnItemSelectedListener(
        new AdapterView.OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            switch (position) {
              case 0:
                setAnalyserResolution(Enums.Preview.SD.getValue());
                break;
              case 1:
                setAnalyserResolution(Enums.Preview.HD.getValue());
                break;
              case 2:
                setAnalyserResolution(Enums.Preview.FULL_HD.getValue());
                break;
            }
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {}
        });
    BottomSheetBehavior.from(binding.loggerBottomSheet)
        .setState(BottomSheetBehavior.STATE_EXPANDED);

    mViewModel
        .getUsbStatus()
        .observe(getViewLifecycleOwner(), status -> binding.usbToggle.setChecked(status));

    binding.usbToggle.setChecked(vehicle.isUsbConnected());

    binding.usbToggle.setOnClickListener(
        v -> {
          binding.usbToggle.setChecked(vehicle.isUsbConnected());
          Navigation.findNavController(requireView()).navigate(R.id.open_settings_fragment);
        });
  }

  private void updateCropImageInfo(Model selected) {
    frameToCropTransform = null;
    binding.cropInfo.setText(
        String.format(
            Locale.US,
            "%d x %d",
            selected.getInputSize().getWidth(),
            selected.getInputSize().getHeight()));

    croppedBitmap =
        Bitmap.createBitmap(
            selected.getInputSize().getWidth(),
            selected.getInputSize().getHeight(),
            Bitmap.Config.ARGB_8888);

    sensorOrientation = 90 - ImageUtils.getScreenOrientation(requireActivity());
    if (selected.type == Model.TYPE.AUTOPILOT) {
      cropRect = new RectF(0.0f, 240.0f / 720.0f, 0.0f, 0.0f);
      maintainAspectRatio = true;
    } else {
      cropRect = new RectF(0.0f, 0.0f, 0.0f, 0.0f);
      maintainAspectRatio = false;
    }
  }

  @Override
  public synchronized void onResume() {
    serverCommunication = new ServerCommunication(requireContext(), this);
    serverCommunication.start();
    handlerThread = new HandlerThread("logging");
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
    super.onResume();
  }

  @Override
  public synchronized void onPause() {
    handlerThread.quitSafely();
    try {
      handlerThread.join();
      handlerThread = null;
      handler = null;
    } catch (final InterruptedException e) {
      e.printStackTrace();
    }
    serverCommunication.stop();
    super.onPause();
  }

  protected synchronized void runInBackground(final Runnable r) {
    if (handler != null) {
      handler.post(r);
    }
  }

  Messenger sensorMessenger;

  ServiceConnection sensorConnection =
      new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder binder) {
          sensorMessenger = new Messenger(binder);
          Timber.d("SensorServiceConnection: connected");
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
          sensorMessenger = null;
          Timber.d("SensorServiceConnection: disconnected");
        }
      };

  protected void sendFrameNumberToSensorService(long frameNumber) {
    if (sensorMessenger != null) {
      try {
        sensorMessenger.send(LogDataUtils.generateFrameNumberMessage(frameNumber));
      } catch (RemoteException e) {
        e.printStackTrace();
      }
    }
  }

  protected void sendControlToSensorService() {
    if (sensorMessenger != null) {
      try {
        sensorMessenger.send(
            LogDataUtils.generateControlDataMessage(
                (int) vehicle.getLeftSpeed(), (int) vehicle.getRightSpeed()));
      } catch (RemoteException e) {
        e.printStackTrace();
      }
    }
  }

  protected void sendVehicleDataToSensorService(long timestamp, String data) {
    if (sensorMessenger != null) {
      try {
        sensorMessenger.send(LogDataUtils.generateVehicleDataMessage(timestamp, data));
      } catch (RemoteException e) {
        e.printStackTrace();
      }
    }
  }

  protected void sendIndicatorToSensorService() {
    if (sensorMessenger != null) {
      try {
        sensorMessenger.send(LogDataUtils.generateIndicatorMessage(vehicle.getIndicator()));
      } catch (RemoteException e) {
        e.printStackTrace();
      }
    }
  }

  private void startLogging() {
    logFolder =
        Environment.getExternalStorageDirectory().getAbsolutePath()
            + File.separator
            + getString(R.string.app_name)
            + File.separator
            + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
    intentSensorService.putExtra("logFolder", logFolder + File.separator + "sensor_data");
    requireActivity().startService(intentSensorService);
    requireActivity().bindService(intentSensorService, sensorConnection, Context.BIND_AUTO_CREATE);
    // Send current vehicle state to log
    runInBackground(
        () -> {
          try {
            TimeUnit.MILLISECONDS.sleep(500);
            sendControlToSensorService();
            sendIndicatorToSensorService();
          } catch (InterruptedException e) {
            Timber.e(e, "Got interrupted.");
          }
        });
  }

  private void stopLogging() {
    if (sensorConnection != null) requireActivity().unbindService(sensorConnection);
    requireActivity().stopService(intentSensorService);

    // Pack and upload the collected data
    runInBackground(
        () -> {
          String logZipFile = logFolder + ".zip";
          // Zip the log folder and then delete it
          File folder = new File(logFolder);
          File zip = new File(logZipFile);
          try {
            TimeUnit.MILLISECONDS.sleep(500);
            // These two lines below are messy and may cause bugs. needs to be looked into
            ZipUtil.pack(folder, zip);
            FileUtils.deleteQuietly(folder);
            serverCommunication.upload(zip);
          } catch (InterruptedException e) {
            Timber.e(e, "Got interrupted.");
          }
        });
  }

  protected void setIsLoggingActive(boolean loggingActive) {
    if (loggingActive && !loggingEnabled) {
      if (!PermissionUtils.hasLoggingPermissions(requireActivity())) {
        requestPermissionLauncherLogging.launch(Constants.PERMISSIONS_LOGGING);
        loggingEnabled = false;
      } else {
        startLogging();
        loggingEnabled = true;
      }
    } else if (!loggingActive && loggingEnabled) {
      stopLogging();
      loggingEnabled = false;
    }
    BotToControllerEventBus.emitEvent(ConnectionUtils.createStatus("LOGS", loggingEnabled));

    binding.loggerSwitch.setChecked(loggingEnabled);
  }

  private boolean allGranted = true;
  protected final ActivityResultLauncher<String[]> requestPermissionLauncherLogging =
      registerForActivityResult(
          new ActivityResultContracts.RequestMultiplePermissions(),
          result -> {
            result.forEach((permission, granted) -> allGranted = allGranted && granted);
            if (allGranted) setIsLoggingActive(true);
            else {
              PermissionUtils.showLoggingPermissionsToast(requireActivity());
            }
          });

  protected void handleLogging() {
    setIsLoggingActive(!loggingEnabled);
    audioPlayer.playLogging(voice, loggingEnabled);
  }

  @Override
  protected void processUSBData(String data) {
    binding.controllerContainer.speedInfo.setText(
        getString(
            R.string.speedInfo,
            String.format(
                Locale.US, "%3.0f,%3.0f", vehicle.getLeftWheelRPM(), vehicle.getRightWheelRPM())));
    sendVehicleDataToSensorService(SystemClock.elapsedRealtimeNanos(), data);
  }

  @Override
  protected void processControllerKeyData(String commandType) {
    switch (commandType) {
      case Constants.CMD_DRIVE:
        handleDriveCommand();
        break;

      case Constants.CMD_LOGS:
        handleLogging();
        break;

      case Constants.CMD_INDICATOR_LEFT:
      case Constants.CMD_INDICATOR_RIGHT:
      case Constants.CMD_INDICATOR_STOP:
        sendIndicatorToSensorService();
        break;
      case Constants.CMD_DRIVE_MODE:
        setDriveMode(Enums.switchDriveMode(vehicle.getDriveMode()));
        break;

      case Constants.CMD_DISCONNECTED:
        handleDriveCommand();
        setControlMode(Enums.ControlMode.GAMEPAD);
        break;

      case Constants.CMD_SPEED_DOWN:
        setSpeedMode(
            Enums.toggleSpeed(
                Enums.Direction.DOWN.getValue(),
                Enums.SpeedMode.getByID(preferencesManager.getSpeedMode())));
        break;
      case Constants.CMD_SPEED_UP:
        setSpeedMode(
            Enums.toggleSpeed(
                Enums.Direction.UP.getValue(),
                Enums.SpeedMode.getByID(preferencesManager.getSpeedMode())));
        break;
      case Constants.CMD_NETWORK:
        //        handleNetwork();
        break;
    }
  }

  protected void handleDriveCommand() {
    float left = vehicle.getLeftSpeed();
    float right = vehicle.getRightSpeed();
    binding.controllerContainer.controlInfo.setText(
        String.format(Locale.US, "%.0f,%.0f", left, right));
    runInBackground(this::sendControlToSensorService);
  }

  private void setSpeedMode(Enums.SpeedMode speedMode) {
    if (speedMode != null) {
      switch (speedMode) {
        case SLOW:
          binding.controllerContainer.speedMode.setImageResource(R.drawable.ic_speed_low);
          break;
        case NORMAL:
          binding.controllerContainer.speedMode.setImageResource(R.drawable.ic_speed_medium);
          break;
        case FAST:
          binding.controllerContainer.speedMode.setImageResource(R.drawable.ic_speed_high);
          break;
      }

      Timber.d("Updating  controlSpeed: %s", speedMode);
      preferencesManager.setSpeedMode(speedMode.getValue());
      vehicle.setSpeedMultiplier(speedMode.getValue());
    }
  }

  private void setControlMode(Enums.ControlMode controlMode) {
    if (controlMode != null) {
      switch (controlMode) {
        case GAMEPAD:
          binding.controllerContainer.controlMode.setImageResource(R.drawable.ic_controller);
          disconnectPhoneController();
          break;
        case PHONE:
          binding.controllerContainer.controlMode.setImageResource(R.drawable.ic_phone);
          if (!PermissionUtils.hasControllerPermissions(requireActivity()))
            requestPermissionLauncher.launch(Constants.PERMISSIONS_CONTROLLER);
          else connectPhoneController();
          break;
      }
      Timber.d("Updating  controlMode: %s", controlMode);
      preferencesManager.setControlMode(controlMode.getValue());
    }
  }

  protected void setDriveMode(Enums.DriveMode driveMode) {
    if (driveMode != null) {
      switch (driveMode) {
        case DUAL:
          binding.controllerContainer.driveMode.setImageResource(R.drawable.ic_dual);
          break;
        case GAME:
          binding.controllerContainer.driveMode.setImageResource(R.drawable.ic_game);
          break;
        case JOYSTICK:
          binding.controllerContainer.driveMode.setImageResource(R.drawable.ic_joystick);
          break;
      }

      Timber.d("Updating  driveMode: %s", driveMode);
      vehicle.setDriveMode(driveMode);
      preferencesManager.setDriveMode(driveMode.getValue());
    }
  }

  private void connectPhoneController() {
    phoneController.connect(requireContext());
    Enums.DriveMode oldDriveMode = currentDriveMode;
    // Currently only dual drive mode supported
    setDriveMode(Enums.DriveMode.DUAL);
    binding.controllerContainer.driveMode.setAlpha(0.5f);
    binding.controllerContainer.driveMode.setEnabled(false);
    preferencesManager.setDriveMode(oldDriveMode.getValue());
  }

  private void disconnectPhoneController() {
    phoneController.disconnect();
    setDriveMode(Enums.DriveMode.getByID(preferencesManager.getDriveMode()));
    binding.controllerContainer.driveMode.setEnabled(true);
    binding.controllerContainer.driveMode.setAlpha(1.0f);
  }

  private long frameNum = 0;

  @Override
  protected void processFrame(Bitmap bitmap, ImageProxy image) {
    ++frameNum;
    if (binding != null) {
      if (isAdded())
        requireActivity()
            .runOnUiThread(
                () ->
                    binding.frameInfo.setText(
                        String.format(Locale.US, "%d x %d", image.getWidth(), image.getHeight())));

      if (!binding.loggerSwitch.isChecked()) return;

      if (binding.previewCheckBox.isChecked() || binding.trainingDataCheckBox.isChecked()) {
        sendFrameNumberToSensorService(frameNum);
      }

      if (binding.previewCheckBox.isChecked()) {
        if (bitmap != null)
          ImageUtils.saveBitmap(
              bitmap, logFolder + File.separator + "images", frameNum + "_preview.jpeg");
      }
      if (binding.trainingDataCheckBox.isChecked()) {
        if (frameToCropTransform == null)
          frameToCropTransform =
              ImageUtils.getTransformationMatrix(
                  getMaxAnalyseImageSize().getWidth(),
                  getMaxAnalyseImageSize().getHeight(),
                  croppedBitmap.getWidth(),
                  croppedBitmap.getHeight(),
                  sensorOrientation,
                  cropRect,
                  maintainAspectRatio);

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(bitmap, frameToCropTransform, null);
        ImageUtils.saveBitmap(
            croppedBitmap, logFolder + File.separator + "images", frameNum + "_crop.jpeg");
      }
    }
  }

  @Override
  public void onConnectionEstablished(String ipAddress) {
    requireActivity().runOnUiThread(() -> binding.ipAddress.setText(ipAddress));
  }

  @Override
  public void onAddModel(String model) {}

  @Override
  public void onRemoveModel(String model) {}
}
