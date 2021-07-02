package org.openbot.logging;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.openbot.R;
import org.openbot.databinding.DialogSensorsBinding;
import org.openbot.env.SharedPreferencesManager;
import org.openbot.utils.Enums;

public class SensorsDialog extends DialogFragment {

  public SensorsDialog() {}

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setStyle(DialogFragment.STYLE_NORMAL, R.style.FullScreenDialog);
  }

  @Override
  public View onCreateView(
      @NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    SharedPreferencesManager preferencesManager = new SharedPreferencesManager(requireContext());
    DialogSensorsBinding binding = DialogSensorsBinding.inflate(LayoutInflater.from(getContext()));
    SensorManager sensorManager =
        (SensorManager) requireActivity().getSystemService(Context.SENSOR_SERVICE);
    List<Sensor> sensorList = sensorManager.getSensorList(Sensor.TYPE_ALL);

    HashMap<String, Boolean> list = new LinkedHashMap<>();
    list.put(
        Enums.SensorType.GPS.getSensor(),
        preferencesManager.getSensorStatus(Enums.SensorType.GPS.getSensor()));
    list.put(
        Enums.SensorType.VEHICLE.getSensor(),
        preferencesManager.getSensorStatus(Enums.SensorType.VEHICLE.getSensor()));
    list.put(
        Enums.SensorType.MOTION.getSensor(),
        preferencesManager.getSensorStatus(Enums.SensorType.MOTION.getSensor()));
    list.put(
        Enums.SensorType.POSE.getSensor(),
        preferencesManager.getSensorStatus(Enums.SensorType.POSE.getSensor()));

    for (Sensor sensor : sensorList) {
      for (Enums.SensorType name : Enums.SensorType.values())
        if (sensor.getName().toLowerCase().contains(name.getSensor().toLowerCase())) {
          list.put(name.getSensor(), preferencesManager.getSensorStatus(name.getSensor()));
          break;
        }
    }

    SensorListAdapter adapter = new SensorListAdapter(list, preferencesManager);

    binding.listView.setLayoutManager(new LinearLayoutManager(requireContext()));
    binding.listView.setAdapter(adapter);
    binding.dismiss.setOnClickListener(v -> dismiss());

    binding.delay.setText(String.valueOf(preferencesManager.getDelay()));
    binding.delay.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {}

          @Override
          public void afterTextChanged(Editable s) {
            if (!s.toString().isEmpty())
              preferencesManager.setDelay(Integer.parseInt(s.toString()));
          }
        });
    return binding.getRoot();
  }
}
